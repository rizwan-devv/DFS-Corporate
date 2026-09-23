package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.integration.dfs.EmployeeAccountParkClient;
import com.dfs.corporate.repository.EmployeeBulkBatchRepository;
import com.dfs.corporate.repository.EmployeeBulkRowRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.web.dto.EmployeeBulkBatchResponse;
import com.dfs.corporate.web.dto.EmployeeBulkRowResponse;
import com.dfs.corporate.web.dto.EmployeeOnboardConfirmRequest;
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

/**
 * Corporate uploads employee CSV → validate → park on DFS → await confirmation webhook.
 */
@Service
public class EmployeeBulkOnboardService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeBulkOnboardService.class);
    private static final int MAX_ROWS = 500;

    private final EmployeeBulkBatchRepository batchRepository;
    private final EmployeeBulkRowRepository rowRepository;
    private final PartyRepository partyRepository;
    private final EmployeeAccountParkClient parkClient;

    public EmployeeBulkOnboardService(EmployeeBulkBatchRepository batchRepository,
                                      EmployeeBulkRowRepository rowRepository,
                                      PartyRepository partyRepository,
                                      EmployeeAccountParkClient parkClient) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.partyRepository = partyRepository;
        this.parkClient = parkClient;
    }

    public String templateCsv() {
        return "employee_code,full_name,father_name,mobile,cnic,date_of_birth,gender,email,department\n"
                + "E001,Ali Khan,Ahmed Khan,03005900256,3520212345671,1990-01-15,M,ali@example.com,Finance\n";
    }

    @Transactional
    public EmployeeBulkBatchResponse upload(AccountPrincipal principal, MultipartFile file) {
        Party party = requireActiveParty(principal);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV file is required");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "employees.csv";
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".txt")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Upload a .csv file (save Excel as CSV)");
        }

        List<String> lines = readLines(file);
        if (lines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        int start = 0;
        String first = lines.get(0).toLowerCase(Locale.ROOT);
        if (first.contains("mobile") || first.contains("cnic") || first.contains("full_name")
                || first.contains("employee")) {
            start = 1;
        }
        List<String> data = lines.subList(start, lines.size());
        if (data.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No data rows");
        }
        if (data.size() > MAX_ROWS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Max " + MAX_ROWS + " rows per file");
        }

        EmployeeBulkBatch batch = new EmployeeBulkBatch();
        batch.setPublicId(UUID.randomUUID().toString());
        batch.setPartyId(party.getId());
        batch.setStatus(EmployeeBulkBatchStatus.DRAFT);
        batch.setFileName(name);
        batch.setTotalRows(data.size());
        batch.setCreatedBy(principal.getUsername());
        batch.setCreatedAt(Instant.now());
        batch = batchRepository.save(batch);

        Set<String> mobilesInFile = new HashSet<>();
        Set<String> cnicsInFile = new HashSet<>();
        int lineNo = start;
        List<EmployeeBulkRow> rows = new ArrayList<>();
        for (String line : data) {
            lineNo++;
            EmployeeBulkRow row = parseRow(lineNo, line, mobilesInFile, cnicsInFile);
            row.setBatchId(batch.getId());
            rows.add(row);
        }
        rowRepository.saveAll(rows);
        return toResponse(batch, rows, true);
    }

    @Transactional
    public EmployeeBulkBatchResponse park(AccountPrincipal principal, String publicId) {
        Party party = requireActiveParty(principal);
        EmployeeBulkBatch batch = requirePartyBatch(party, publicId);
        if (batch.getStatus() != EmployeeBulkBatchStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Batch already parked or finished: " + batch.getStatus());
        }
        List<EmployeeBulkRow> rows = rowRepository.findByBatchIdOrderByLineNoAsc(batch.getId());
        long valid = rows.stream().filter(r -> r.getStatus() == EmployeeBulkRowStatus.VALIDATED).count();
        if (valid == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No VALIDATED rows to park — fix the CSV and re-upload");
        }

        batch.setStatus(EmployeeBulkBatchStatus.PARKING);
        batchRepository.save(batch);

        int parked = 0;
        int open = 0;
        int failed = 0;
        for (EmployeeBulkRow row : rows) {
            if (row.getStatus() != EmployeeBulkRowStatus.VALIDATED) {
                if (row.getStatus() == EmployeeBulkRowStatus.INVALID) failed++;
                continue;
            }
            try {
                EmployeeAccountParkClient.ParkResult result = parkClient.park(party, row);
                if (result.success()) {
                    row.setParkRef(result.parkRef());
                    row.setResponseMessage(result.message());
                    row.setParkedAt(Instant.now());
                    if (result.dfsAccountNo() != null && !result.dfsAccountNo().isBlank()) {
                        row.setDfsAccountNo(result.dfsAccountNo());
                    }
                    if (result.dfsCustomerId() != null && !result.dfsCustomerId().isBlank()) {
                        row.setDfsCustomerId(result.dfsCustomerId());
                    }
                    if (result.accountOpened()) {
                        row.setStatus(EmployeeBulkRowStatus.OPEN);
                        row.setConfirmedAt(Instant.now());
                        open++;
                    } else {
                        row.setStatus(EmployeeBulkRowStatus.PARKED);
                        parked++;
                    }
                } else {
                    row.setStatus(EmployeeBulkRowStatus.FAILED);
                    row.setResponseMessage(result.message() != null ? result.message() : "Park failed");
                    failed++;
                }
            } catch (Exception ex) {
                log.warn("Park failed for row {}: {}", row.getPublicId(), ex.getMessage());
                row.setStatus(EmployeeBulkRowStatus.FAILED);
                row.setResponseMessage(ex.getMessage());
                failed++;
            }
        }
        rowRepository.saveAll(rows);

        batch.setParkedRows(parked);
        batch.setOpenRows(open);
        batch.setFailedRows(failed);
        batch.setParkedAt(Instant.now());
        if (parked == 0 && open == 0) {
            batch.setStatus(EmployeeBulkBatchStatus.FAILED);
            batch.setFinishedAt(Instant.now());
            batch.setErrorMessage("No rows parked successfully");
        } else if (parked == 0 && failed == 0) {
            batch.setStatus(EmployeeBulkBatchStatus.COMPLETED);
            batch.setFinishedAt(Instant.now());
        } else if (open > 0 && parked == 0 && failed > 0) {
            batch.setStatus(EmployeeBulkBatchStatus.PARTIAL);
            batch.setFinishedAt(Instant.now());
        } else {
            batch.setStatus(EmployeeBulkBatchStatus.PARKED);
        }
        batchRepository.save(batch);
        return toResponse(batch, rows, true);
    }

    public EmployeeBulkBatchResponse get(AccountPrincipal principal, String publicId) {
        Party party = requireActiveParty(principal);
        EmployeeBulkBatch batch = requirePartyBatch(party, publicId);
        return toResponse(batch, rowRepository.findByBatchIdOrderByLineNoAsc(batch.getId()), true);
    }

    public List<EmployeeBulkBatchResponse> list(AccountPrincipal principal) {
        Party party = requireActiveParty(principal);
        return batchRepository.findByPartyIdOrderByCreatedAtDesc(party.getId()).stream()
                .limit(40)
                .map(b -> toResponse(b, List.of(), false))
                .toList();
    }

    public String resultCsv(AccountPrincipal principal, String publicId) {
        Party party = requireActiveParty(principal);
        EmployeeBulkBatch batch = requirePartyBatch(party, publicId);
        List<EmployeeBulkRow> rows = rowRepository.findByBatchIdOrderByLineNoAsc(batch.getId());
        StringBuilder sb = new StringBuilder();
        sb.append("line_no,row_id,status,employee_code,full_name,mobile,cnic,park_ref,dfs_account_no,dfs_customer_id,message\n");
        for (EmployeeBulkRow r : rows) {
            sb.append(r.getLineNo()).append(',')
                    .append(csv(r.getPublicId())).append(',')
                    .append(csv(r.getStatus() != null ? r.getStatus().name() : "")).append(',')
                    .append(csv(r.getEmployeeCode())).append(',')
                    .append(csv(r.getFullName())).append(',')
                    .append(csv(r.getMobile())).append(',')
                    .append(csv(r.getCnic())).append(',')
                    .append(csv(r.getParkRef())).append(',')
                    .append(csv(r.getDfsAccountNo())).append(',')
                    .append(csv(r.getDfsCustomerId())).append(',')
                    .append(csv(r.getResponseMessage())).append('\n');
        }
        return sb.toString();
    }

    /**
     * DFS (or test UI) confirms account open / failure for a parked row.
     */
    @Transactional
    public EmployeeBulkRowResponse confirm(EmployeeOnboardConfirmRequest req) {
        if (req == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Body required");
        }
        EmployeeBulkRow row = null;
        if (req.getRowPublicId() != null && !req.getRowPublicId().isBlank()) {
            row = rowRepository.findByPublicId(req.getRowPublicId().trim())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee row not found"));
        } else if (req.getParkRef() != null && !req.getParkRef().isBlank()) {
            row = rowRepository.findByParkRef(req.getParkRef().trim())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Park ref not found"));
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "rowPublicId or parkRef required");
        }

        if (row.getStatus() == EmployeeBulkRowStatus.OPEN) {
            return toRowResponse(row);
        }
        if (row.getStatus() != EmployeeBulkRowStatus.PARKED
                && row.getStatus() != EmployeeBulkRowStatus.FAILED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Row is not awaiting confirmation: " + row.getStatus());
        }

        String st = req.getStatus() != null ? req.getStatus().trim().toUpperCase(Locale.ROOT) : "OPEN";
        Instant now = Instant.now();
        if ("OPEN".equals(st) || "SUCCESS".equals(st) || "CREATED".equals(st)) {
            row.setStatus(EmployeeBulkRowStatus.OPEN);
            row.setDfsAccountNo(req.getDfsAccountNo());
            row.setDfsCustomerId(req.getDfsCustomerId());
            row.setResponseMessage(req.getMessage() != null ? req.getMessage() : "Account opened on DFS");
            row.setConfirmedAt(now);
        } else if ("REJECTED".equals(st)) {
            row.setStatus(EmployeeBulkRowStatus.REJECTED);
            row.setResponseMessage(req.getMessage() != null ? req.getMessage() : "Rejected by DFS");
            row.setConfirmedAt(now);
        } else {
            row.setStatus(EmployeeBulkRowStatus.FAILED);
            row.setResponseMessage(req.getMessage() != null ? req.getMessage() : "Account open failed");
            row.setConfirmedAt(now);
        }
        rowRepository.save(row);
        refreshBatchCounts(row.getBatchId());
        return toRowResponse(row);
    }

    @Transactional
    public EmployeeBulkRowResponse confirmForParty(AccountPrincipal principal, EmployeeOnboardConfirmRequest req) {
        Party party = requireActiveParty(principal);
        EmployeeBulkRowResponse updated = confirm(req);
        EmployeeBulkRow row = rowRepository.findByPublicId(updated.getPublicId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Row not found"));
        EmployeeBulkBatch batch = batchRepository.findById(row.getBatchId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Batch not found"));
        if (!batch.getPartyId().equals(party.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Row does not belong to this party");
        }
        return updated;
    }

    private void refreshBatchCounts(Long batchId) {
        EmployeeBulkBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) return;
        List<EmployeeBulkRow> rows = rowRepository.findByBatchIdOrderByLineNoAsc(batchId);
        int parked = 0, open = 0, failed = 0;
        boolean anyPending = false;
        for (EmployeeBulkRow r : rows) {
            switch (r.getStatus()) {
                case PARKED -> { parked++; anyPending = true; }
                case VALIDATED -> anyPending = true;
                case OPEN -> open++;
                case FAILED, REJECTED, INVALID -> failed++;
                default -> {}
            }
        }
        batch.setParkedRows(parked);
        batch.setOpenRows(open);
        batch.setFailedRows(failed);
        if (!anyPending) {
            if (open > 0 && failed == 0) {
                batch.setStatus(EmployeeBulkBatchStatus.COMPLETED);
            } else if (open > 0) {
                batch.setStatus(EmployeeBulkBatchStatus.PARTIAL);
            } else {
                batch.setStatus(EmployeeBulkBatchStatus.FAILED);
            }
            batch.setFinishedAt(Instant.now());
        } else if (batch.getStatus() == EmployeeBulkBatchStatus.PARKING
                || batch.getStatus() == EmployeeBulkBatchStatus.DRAFT) {
            batch.setStatus(EmployeeBulkBatchStatus.PARKED);
        }
        batchRepository.save(batch);
    }

    private EmployeeBulkRow parseRow(int lineNo, String line, Set<String> mobiles, Set<String> cnics) {
        EmployeeBulkRow row = new EmployeeBulkRow();
        row.setPublicId(UUID.randomUUID().toString());
        row.setLineNo(lineNo);
        row.setRawLine(line.length() > 1500 ? line.substring(0, 1500) : line);

        String[] cols = splitCsv(line);
        // employee_code,full_name,father_name,mobile,cnic,date_of_birth,gender,email,department
        String code = col(cols, 0);
        String fullName = col(cols, 1);
        String father = col(cols, 2);
        String mobile = IdentityFormats.phoneDigits(col(cols, 3));
        String cnic = IdentityFormats.cnicDigits(col(cols, 4));
        String dob = col(cols, 5);
        String gender = col(cols, 6);
        String email = col(cols, 7);
        String dept = col(cols, 8);

        row.setEmployeeCode(blankToNull(code));
        row.setFullName(blankToNull(fullName));
        row.setFatherName(blankToNull(father));
        row.setMobile(blankToNull(mobile));
        row.setCnic(blankToNull(cnic));
        row.setDateOfBirth(blankToNull(dob));
        row.setGender(blankToNull(gender));
        row.setEmail(blankToNull(email));
        row.setDepartment(blankToNull(dept));

        List<String> errors = new ArrayList<>();
        if (fullName == null || fullName.isBlank()) errors.add("full_name required");
        if (mobile == null || mobile.length() < 10) errors.add("mobile invalid");
        if (cnic == null || cnic.length() != 13) errors.add("cnic must be 13 digits");
        if (mobile != null && !mobiles.add(mobile)) errors.add("duplicate mobile in file");
        if (cnic != null && !cnics.add(cnic)) errors.add("duplicate cnic in file");

        if (errors.isEmpty()) {
            row.setStatus(EmployeeBulkRowStatus.VALIDATED);
        } else {
            row.setStatus(EmployeeBulkRowStatus.INVALID);
            row.setResponseMessage(String.join("; ", errors));
        }
        return row;
    }

    private static String col(String[] cols, int i) {
        if (cols == null || i >= cols.length) return null;
        String v = cols[i];
        return v != null ? v.trim() : null;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String[] splitCsv(String line) {
        // Simple CSV split; supports comma / semicolon / tab
        String sep = line.contains("\t") ? "\t" : (line.contains(";") && !line.contains(",") ? ";" : ",");
        return line.split(sep, -1);
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

    private EmployeeBulkBatch requirePartyBatch(Party party, String publicId) {
        EmployeeBulkBatch batch = batchRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee batch not found"));
        if (!batch.getPartyId().equals(party.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Batch does not belong to this party");
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

    private EmployeeBulkBatchResponse toResponse(EmployeeBulkBatch b, List<EmployeeBulkRow> rows, boolean includeRows) {
        EmployeeBulkBatchResponse r = new EmployeeBulkBatchResponse();
        r.setPublicId(b.getPublicId());
        r.setStatus(b.getStatus() != null ? b.getStatus().name() : null);
        r.setFileName(b.getFileName());
        r.setTotalRows(b.getTotalRows() != null ? b.getTotalRows() : 0);
        r.setParkedRows(b.getParkedRows() != null ? b.getParkedRows() : 0);
        r.setOpenRows(b.getOpenRows() != null ? b.getOpenRows() : 0);
        r.setFailedRows(b.getFailedRows() != null ? b.getFailedRows() : 0);
        r.setErrorMessage(b.getErrorMessage());
        r.setCreatedAt(b.getCreatedAt());
        r.setParkedAt(b.getParkedAt());
        r.setFinishedAt(b.getFinishedAt());
        if (includeRows && rows != null) {
            r.setRows(rows.stream().map(this::toRowResponse).toList());
        }
        return r;
    }

    private EmployeeBulkRowResponse toRowResponse(EmployeeBulkRow row) {
        EmployeeBulkRowResponse r = new EmployeeBulkRowResponse();
        r.setPublicId(row.getPublicId());
        r.setLineNo(row.getLineNo() != null ? row.getLineNo() : 0);
        r.setStatus(row.getStatus() != null ? row.getStatus().name() : null);
        r.setEmployeeCode(row.getEmployeeCode());
        r.setFullName(row.getFullName());
        r.setFatherName(row.getFatherName());
        r.setMobile(row.getMobile());
        r.setCnic(row.getCnic());
        r.setDateOfBirth(row.getDateOfBirth());
        r.setGender(row.getGender());
        r.setEmail(row.getEmail());
        r.setDepartment(row.getDepartment());
        r.setParkRef(row.getParkRef());
        r.setDfsAccountNo(row.getDfsAccountNo());
        r.setDfsCustomerId(row.getDfsCustomerId());
        r.setResponseMessage(row.getResponseMessage());
        r.setParkedAt(row.getParkedAt());
        r.setConfirmedAt(row.getConfirmedAt());
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
}
