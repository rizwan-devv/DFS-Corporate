package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.integration.dfs.CorporatePortalTxnClient;
import com.dfs.corporate.repository.LiveBulkBatchRepository;
import com.dfs.corporate.repository.LiveBulkRowRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.web.dto.*;
import com.dfs.corporate.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live bulk FT / IBFT / UBP — separate from single-transfer APIs.
 * Uploads CSV, then async worker calls existing {@link LiveTransferService} once per row.
 */
@Service
public class LiveBulkTransferService {

    private static final Logger log = LoggerFactory.getLogger(LiveBulkTransferService.class);
    private static final int MAX_ROWS = 100;
    private static final long ROW_DELAY_MS = 350L;

    /** Short-lived MPIN for FT bulk (never persisted). */
    private final ConcurrentHashMap<Long, String> ftMpinByBatchId = new ConcurrentHashMap<>();

    private final LiveBulkBatchRepository batchRepository;
    private final LiveBulkRowRepository rowRepository;
    private final PartyRepository partyRepository;
    private final LiveTransferService liveTransferService;
    private final CorporatePortalTxnClient txnClient;
    private final LiveBulkTransferWorker bulkWorker;

    public LiveBulkTransferService(LiveBulkBatchRepository batchRepository,
                                   LiveBulkRowRepository rowRepository,
                                   PartyRepository partyRepository,
                                   LiveTransferService liveTransferService,
                                   CorporatePortalTxnClient txnClient,
                                   @org.springframework.context.annotation.Lazy LiveBulkTransferWorker bulkWorker) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.partyRepository = partyRepository;
        this.liveTransferService = liveTransferService;
        this.txnClient = txnClient;
        this.bulkWorker = bulkWorker;
    }

    public String templateCsv(String productType) {
        MockTransferProduct p = parseProduct(productType);
        return switch (p) {
            case FT -> "beneficiary_mobile,amount,narration\n03006088659,10,Bulk FT sample\n";
            case IBFT -> "beneficiary_account_no,bank_imd,amount,purpose,narration\nPK00XXXX0000000000000000,627271,10,Payment,Bulk IBFT sample\n";
            case UBP -> "utility_company_code,consumer_no,amount\nSNGPL001,1234567890,100\n";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Bulk live supported for FT, IBFT, UBP only");
        };
    }

    @Transactional
    public LiveBulkBatchResponse upload(AccountPrincipal principal, String productType, MultipartFile file) {
        ensureLive();
        Party party = requireActiveParty(principal);
        MockTransferProduct product = parseProduct(productType);
        if (product == MockTransferProduct.RAAST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Raast bulk is not supported");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV file is required");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "bulk.csv";
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv") && !name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Upload a .csv file");
        }

        List<String> lines = readLines(file);
        if (lines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        int start = 0;
        String first = lines.get(0).toLowerCase(Locale.ROOT);
        if (first.contains("amount") || first.contains("beneficiary") || first.contains("consumer")
                || first.contains("utility") || first.contains("bank_imd") || first.contains("narration")) {
            start = 1;
        }
        List<String> data = lines.subList(start, lines.size());
        if (data.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No data rows");
        }
        if (data.size() > MAX_ROWS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Max " + MAX_ROWS + " rows per live bulk file");
        }

        LiveBulkBatch batch = new LiveBulkBatch();
        batch.setPublicId(UUID.randomUUID().toString());
        batch.setPartyId(party.getId());
        batch.setProductType(product);
        batch.setStatus(LiveBulkBatchStatus.DRAFT);
        batch.setFileName(name);
        batch.setTotalRows(data.size());
        batch.setCreatedBy(principal.getUsername());
        batch.setCreatedAt(Instant.now());
        batch = batchRepository.save(batch);

        int lineNo = start;
        List<LiveBulkRow> rows = new ArrayList<>();
        for (String line : data) {
            lineNo++;
            LiveBulkRow row = parseRow(product, lineNo, line);
            row.setBatchId(batch.getId());
            if (row.getStatus() == null) {
                row.setStatus(LiveBulkRowStatus.PENDING);
            }
            rows.add(row);
        }
        rowRepository.saveAll(rows);
        return toResponse(batch, rows, false);
    }

    @Transactional
    public LiveBulkBatchResponse start(AccountPrincipal principal, String publicId, LiveBulkStartRequest req) {
        ensureLive();
        LiveBulkBatch batch = requirePartyBatch(principal, publicId);
        if (batch.getStatus() != LiveBulkBatchStatus.DRAFT && batch.getStatus() != LiveBulkBatchStatus.QUEUED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Batch already started or finished: " + batch.getStatus());
        }
        if (batch.getProductType() == MockTransferProduct.FT) {
            String mpin = req != null ? IdentityFormats.pinPlain(req.getMpin()) : null;
            if (mpin == null || mpin.length() < 4) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Customer MPIN is required to start FT bulk");
            }
            ftMpinByBatchId.put(batch.getId(), mpin);
        }
        batch.setStatus(LiveBulkBatchStatus.QUEUED);
        batchRepository.save(batch);
        bulkWorker.run(batch.getId(), principal);
        return toResponse(batch, rowRepository.findByBatchIdOrderByLineNoAsc(batch.getId()), false);
    }

    public LiveBulkBatchResponse get(AccountPrincipal principal, String publicId) {
        LiveBulkBatch batch = requirePartyBatch(principal, publicId);
        return toResponse(batch, rowRepository.findByBatchIdOrderByLineNoAsc(batch.getId()), true);
    }

    public List<LiveBulkBatchResponse> list(AccountPrincipal principal, String productType) {
        Party party = requireActiveParty(principal);
        MockTransferProduct filter = productType == null || productType.isBlank()
                ? null : parseProduct(productType);
        return batchRepository.findByPartyIdOrderByCreatedAtDesc(party.getId()).stream()
                .filter(b -> filter == null || b.getProductType() == filter)
                .limit(30)
                .map(b -> toResponse(b, List.of(), false))
                .toList();
    }

    public String resultCsv(AccountPrincipal principal, String publicId) {
        LiveBulkBatch batch = requirePartyBatch(principal, publicId);
        List<LiveBulkRow> rows = rowRepository.findByBatchIdOrderByLineNoAsc(batch.getId());
        StringBuilder sb = new StringBuilder();
        sb.append("line_no,status,amount,beneficiary_account,bank_imd,utility_company_code,consumer_no,")
                .append("response_code,response_message,txn_ref\n");
        for (LiveBulkRow r : rows) {
            sb.append(r.getLineNo()).append(',')
                    .append(csv(r.getStatus() != null ? r.getStatus().name() : "")).append(',')
                    .append(csv(r.getAmount())).append(',')
                    .append(csv(r.getBeneficiaryAccount())).append(',')
                    .append(csv(r.getBankImd())).append(',')
                    .append(csv(r.getUtilityCompanyCode())).append(',')
                    .append(csv(r.getConsumerNo())).append(',')
                    .append(csv(r.getResponseCode())).append(',')
                    .append(csv(r.getResponseMessage())).append(',')
                    .append(csv(r.getTxnRef())).append('\n');
        }
        return sb.toString();
    }

    public void processBatch(Long batchId, AccountPrincipal principal) {
        try {
            runBatch(batchId, principal);
        } catch (Exception ex) {
            log.error("Live bulk batch {} failed: {}", batchId, ex.getMessage(), ex);
            batchRepository.findById(batchId).ifPresent(b -> {
                b.setStatus(LiveBulkBatchStatus.FAILED);
                b.setErrorMessage(truncate(ex.getMessage(), 900));
                b.setFinishedAt(Instant.now());
                batchRepository.save(b);
            });
            ftMpinByBatchId.remove(batchId);
        }
    }

    private void runBatch(Long batchId, AccountPrincipal principal) {
        LiveBulkBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Batch not found"));
        batch.setStatus(LiveBulkBatchStatus.RUNNING);
        batch.setStartedAt(Instant.now());
        batchRepository.save(batch);

        String mpin = ftMpinByBatchId.get(batchId);
        List<LiveBulkRow> rows = rowRepository.findByBatchIdOrderByLineNoAsc(batchId);
        int ok = 0;
        int fail = 0;

        for (LiveBulkRow row : rows) {
            if (row.getStatus() != LiveBulkRowStatus.PENDING) {
                continue;
            }
            row.setStatus(LiveBulkRowStatus.PROCESSING);
            rowRepository.save(row);
            try {
                executeRow(principal, batch.getProductType(), row, mpin);
                if (row.getStatus() == LiveBulkRowStatus.SUCCESS) {
                    ok++;
                } else {
                    fail++;
                }
            } catch (Exception ex) {
                row.setStatus(LiveBulkRowStatus.FAILED);
                row.setResponseMessage(truncate(ex.getMessage(), 900));
                row.setProcessedAt(Instant.now());
                rowRepository.save(row);
                fail++;
            }
            sleepQuiet();
        }

        batch.setSuccessRows(ok);
        batch.setFailedRows(fail);
        batch.setFinishedAt(Instant.now());
        if (fail == 0) {
            batch.setStatus(LiveBulkBatchStatus.COMPLETED);
        } else if (ok == 0) {
            batch.setStatus(LiveBulkBatchStatus.FAILED);
        } else {
            batch.setStatus(LiveBulkBatchStatus.PARTIAL);
        }
        batchRepository.save(batch);
        ftMpinByBatchId.remove(batchId);
        log.info("Live bulk {} {} done ok={} fail={}", batch.getProductType(), batch.getPublicId(), ok, fail);
    }

    private void executeRow(AccountPrincipal principal, MockTransferProduct product,
                            LiveBulkRow row, String mpin) {
        switch (product) {
            case FT -> executeFt(principal, row, mpin);
            case IBFT -> executeIbft(principal, row);
            case UBP -> executeUbp(principal, row);
            default -> {
                row.setStatus(LiveBulkRowStatus.SKIPPED);
                row.setResponseMessage("Unsupported product");
                row.setProcessedAt(Instant.now());
                rowRepository.save(row);
            }
        }
    }

    private void executeFt(AccountPrincipal principal, LiveBulkRow row, String mpin) {
        LiveFtInitiateRequest init = new LiveFtInitiateRequest();
        init.setAccountNo(row.getBeneficiaryAccount());
        init.setAmount(row.getAmount());
        DfsPortalTxnResponse initResp = liveTransferService.ftInitiate(principal, init);
        if (!isSuccess(initResp)) {
            failRow(row, initResp);
            return;
        }
        LiveFtConfirmRequest conf = new LiveFtConfirmRequest();
        conf.setAccountNo(row.getBeneficiaryAccount());
        conf.setAmount(row.getAmount());
        conf.setMpin(mpin);
        conf.setNarration(row.getNarration());
        DfsPortalTxnResponse confResp = liveTransferService.ftConfirm(principal, conf);
        applyResult(row, confResp);
    }

    private void executeIbft(AccountPrincipal principal, LiveBulkRow row) {
        LiveIbftTitleRequest title = new LiveIbftTitleRequest();
        title.setBeneficiaryAccountNo(row.getBeneficiaryAccount());
        title.setBeneficiaryBankImd(row.getBankImd());
        title.setAmount(row.getAmount());
        DfsPortalTxnResponse titleResp = liveTransferService.ibftTitleFetch(principal, title);
        if (!isSuccess(titleResp)) {
            failRow(row, titleResp);
            return;
        }
        LiveIbftAdviceRequest advice = new LiveIbftAdviceRequest();
        advice.setBeneficiaryAccountNo(row.getBeneficiaryAccount());
        advice.setBeneficiaryBankImd(row.getBankImd());
        advice.setAmount(row.getAmount());
        String purpose = row.getNarration() != null && !row.getNarration().isBlank()
                ? row.getNarration() : "Bulk IBFT";
        advice.setPurposeOfPayment(purpose);
        advice.setNotes(purpose);
        DfsPortalTxnResponse adviceResp = liveTransferService.ibftAdvice(principal, advice);
        applyResult(row, adviceResp);
    }

    private void executeUbp(AccountPrincipal principal, LiveBulkRow row) {
        LiveBillInquiryRequest inq = new LiveBillInquiryRequest();
        inq.setUtilityCompanyCode(row.getUtilityCompanyCode());
        inq.setConsumerNo(row.getConsumerNo());
        DfsPortalTxnResponse inqResp = liveTransferService.ubpInquiry(principal, inq);
        if (!isSuccess(inqResp)) {
            failRow(row, inqResp);
            return;
        }
        String payAmount = row.getAmount();
        if ((payAmount == null || payAmount.isBlank()) && inqResp.getData() != null) {
            if (inqResp.getData().has("amount")) {
                payAmount = inqResp.getData().get("amount").asText();
            } else if (inqResp.getData().has("billAmount")) {
                payAmount = inqResp.getData().get("billAmount").asText();
            }
        }
        if (payAmount == null || payAmount.isBlank()) {
            row.setStatus(LiveBulkRowStatus.FAILED);
            row.setResponseMessage("No amount from inquiry and CSV amount empty");
            row.setProcessedAt(Instant.now());
            rowRepository.save(row);
            return;
        }
        LiveBillPaymentRequest pay = new LiveBillPaymentRequest();
        pay.setUtilityCompanyCode(row.getUtilityCompanyCode());
        pay.setConsumerNo(row.getConsumerNo());
        pay.setAmount(payAmount);
        DfsPortalTxnResponse payResp = liveTransferService.ubpPay(principal, pay);
        applyResult(row, payResp);
    }

    private void applyResult(LiveBulkRow row, DfsPortalTxnResponse resp) {
        row.setResponseCode(resp.getResponsecode());
        row.setResponseMessage(resp.getMessages());
        row.setProcessedAt(Instant.now());
        if (isSuccess(resp)) {
            row.setStatus(LiveBulkRowStatus.SUCCESS);
            if (resp.getData() != null && resp.getData().has("transactionReference")) {
                row.setTxnRef(resp.getData().get("transactionReference").asText());
            }
        } else {
            row.setStatus(LiveBulkRowStatus.FAILED);
        }
        rowRepository.save(row);
    }

    private void failRow(LiveBulkRow row, DfsPortalTxnResponse resp) {
        row.setStatus(LiveBulkRowStatus.FAILED);
        row.setResponseCode(resp != null ? resp.getResponsecode() : null);
        row.setResponseMessage(resp != null ? resp.getMessages() : "Failed");
        row.setProcessedAt(Instant.now());
        rowRepository.save(row);
    }

    private static boolean isSuccess(DfsPortalTxnResponse r) {
        return r != null && r.getResponsecode() != null && "000".equals(r.getResponsecode().trim());
    }

    private LiveBulkRow parseRow(MockTransferProduct product, int lineNo, String line) {
        String[] cols = line.split("[,;\\t]", -1);
        LiveBulkRow row = new LiveBulkRow();
        row.setLineNo(lineNo);
        row.setRawLine(truncate(line, 900));
        try {
            switch (product) {
                case FT -> {
                    requireCols(cols, 2, "beneficiary_mobile,amount");
                    row.setBeneficiaryAccount(cols[0].trim());
                    row.setAmount(cols[1].trim());
                    if (cols.length > 2) row.setNarration(cols[2].trim());
                }
                case IBFT -> {
                    requireCols(cols, 3, "beneficiary_account_no,bank_imd,amount");
                    row.setBeneficiaryAccount(cols[0].trim());
                    row.setBankImd(cols[1].trim());
                    row.setAmount(cols[2].trim());
                    // cols[3]=purpose (preferred for advice), cols[4]=narration/notes
                    String purpose = cols.length > 3 ? cols[3].trim() : "";
                    String notes = cols.length > 4 ? cols[4].trim() : "";
                    row.setNarration(!purpose.isBlank() ? purpose : notes);
                }
                case UBP -> {
                    requireCols(cols, 2, "utility_company_code,consumer_no");
                    row.setUtilityCompanyCode(cols[0].trim());
                    row.setConsumerNo(cols[1].trim());
                    if (cols.length > 2) row.setAmount(cols[2].trim());
                }
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported");
            }
        } catch (ApiException ex) {
            row.setStatus(LiveBulkRowStatus.FAILED);
            row.setResponseMessage(ex.getMessage());
        }
        return row;
    }

    private static void requireCols(String[] cols, int min, String hint) {
        if (cols.length < min) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Row needs at least " + min + " columns (" + hint + ")");
        }
        for (int i = 0; i < min; i++) {
            if (cols[i] == null || cols[i].isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Missing column in row (" + hint + ")");
            }
        }
    }

    private List<String> readLines(MultipartFile file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) lines.add(line.trim());
            }
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read CSV: " + e.getMessage());
        }
        return lines;
    }

    private LiveBulkBatch requirePartyBatch(AccountPrincipal principal, String publicId) {
        Party party = requireActiveParty(principal);
        LiveBulkBatch batch = batchRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bulk batch not found"));
        if (!batch.getPartyId().equals(party.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bulk batch does not belong to this party");
        }
        return batch;
    }

    private Party requireActiveParty(AccountPrincipal principal) {
        if (principal == null || principal.getPartyId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No party on this login");
        }
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Party must be ACTIVE");
        }
        return party;
    }

    private void ensureLive() {
        if (!txnClient.isEnabled() || !txnClient.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Live transfers disabled. Set DFS_PORTAL_API_ENABLED and CORPORATE_PORTAL_API_KEY.");
        }
    }

    private MockTransferProduct parseProduct(String productType) {
        try {
            return MockTransferProduct.valueOf(productType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "productType must be FT, IBFT, or UBP");
        }
    }

    private LiveBulkBatchResponse toResponse(LiveBulkBatch b, List<LiveBulkRow> rows, boolean includeRows) {
        LiveBulkBatchResponse r = new LiveBulkBatchResponse();
        r.setPublicId(b.getPublicId());
        r.setProductType(b.getProductType() != null ? b.getProductType().name() : null);
        r.setStatus(b.getStatus() != null ? b.getStatus().name() : null);
        r.setFileName(b.getFileName());
        r.setTotalRows(b.getTotalRows() != null ? b.getTotalRows() : 0);
        r.setSuccessRows(b.getSuccessRows() != null ? b.getSuccessRows() : 0);
        r.setFailedRows(b.getFailedRows() != null ? b.getFailedRows() : 0);
        r.setErrorMessage(b.getErrorMessage());
        r.setCreatedAt(b.getCreatedAt());
        r.setStartedAt(b.getStartedAt());
        r.setFinishedAt(b.getFinishedAt());
        if (includeRows && rows != null) {
            r.setRows(rows.stream().map(this::toRowResponse).toList());
        }
        return r;
    }

    private LiveBulkRowResponse toRowResponse(LiveBulkRow row) {
        LiveBulkRowResponse r = new LiveBulkRowResponse();
        r.setLineNo(row.getLineNo() != null ? row.getLineNo() : 0);
        r.setStatus(row.getStatus() != null ? row.getStatus().name() : null);
        r.setBeneficiaryAccount(row.getBeneficiaryAccount());
        r.setBankImd(row.getBankImd());
        r.setUtilityCompanyCode(row.getUtilityCompanyCode());
        r.setConsumerNo(row.getConsumerNo());
        r.setAmount(row.getAmount());
        r.setNarration(row.getNarration());
        r.setResponseCode(row.getResponseCode());
        r.setResponseMessage(row.getResponseMessage());
        r.setTxnRef(row.getTxnRef());
        r.setProcessedAt(row.getProcessedAt());
        return r;
    }

    private static String csv(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static void sleepQuiet() {
        try {
            Thread.sleep(ROW_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
