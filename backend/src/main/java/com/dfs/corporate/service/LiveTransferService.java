package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.integration.dfs.CorporatePortalAppClient;
import com.dfs.corporate.integration.dfs.CorporatePortalTxnClient;
import com.dfs.corporate.repository.MockTransferRepository;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.web.dto.*;
import com.dfs.corporate.web.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live corporate transfers against DFS Transactions + App (X-Portal-Key).
 * Portal JWT auth selects the party; portal key never leaves the server.
 */
@Service
public class LiveTransferService {

    private final CorporatePortalTxnClient txnClient;
    private final CorporatePortalAppClient appClient;
    private final PartyRepository partyRepository;
    private final PartnerAppUserRepository partnerAppUserRepository;
    private final MockTransferRepository transferRepository;
    private final ObjectMapper objectMapper;

    public LiveTransferService(CorporatePortalTxnClient txnClient,
                               CorporatePortalAppClient appClient,
                               PartyRepository partyRepository,
                               PartnerAppUserRepository partnerAppUserRepository,
                               MockTransferRepository transferRepository,
                               ObjectMapper objectMapper) {
        this.txnClient = txnClient;
        this.appClient = appClient;
        this.partyRepository = partyRepository;
        this.partnerAppUserRepository = partnerAppUserRepository;
        this.transferRepository = transferRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> status(AccountPrincipal principal) {
        Party party = requireActiveParty(principal);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("liveEnabled", txnClient.isEnabled());
        out.put("txnConfigured", txnClient.isConfigured());
        out.put("appConfigured", appClient.isEnabled());
        out.put("fromAccountNo", requireMobile(party));
        out.put("hasNid", requireNidOptional(party) != null);
        out.put("hasDfsAppUserId", resolveAppUserId(party, null) != null);
        out.put("levelCode", levelOf(party));
        out.put("note",
                "IBFT bankList downstream is POST (Postman). Portal exposes GET /api/transfers/live/ibft/banks for convenience. "
                        + "getbiller is GET. Branch on responsecode=000.");
        out.put("products", List.of("FT", "IBFT", "UBP"));
        out.put("raastLive", false);
        return out;
    }

    public DfsPortalTxnResponse ibftBanks(AccountPrincipal principal) {
        requireActiveParty(principal);
        return wrap("IBFT", null, call(() -> txnClient.ibftBankList()));
    }

    public DfsPortalTxnResponse ibftTitleFetch(AccountPrincipal principal, LiveIbftTitleRequest req) {
        Party party = requireActiveParty(principal);
        String from = requireMobile(party);
        String nid = requireNid(party);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("fromAccountNo", from);
        payload.put("fromAccountNid", nid);
        payload.put("beneficiaryAccountNo", req.getBeneficiaryAccountNo().trim());
        payload.put("beneficiaryBankImd", req.getBeneficiaryBankImd().trim());
        payload.put("amount", normalizeAmount(req.getAmount()));
        return wrap("IBFT", from, call(() -> txnClient.ibftTitleFetch(payload)));
    }

    @Transactional
    public DfsPortalTxnResponse ibftAdvice(AccountPrincipal principal, LiveIbftAdviceRequest req) {
        Party party = requireActiveParty(principal);
        String from = requireMobile(party);
        String nid = requireNid(party);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("fromAccountNo", from);
        payload.put("fromAccountNid", nid);
        payload.put("beneficiaryAccountNo", req.getBeneficiaryAccountNo().trim());
        payload.put("beneficiaryBankImd", req.getBeneficiaryBankImd().trim());
        payload.put("amount", normalizeAmount(req.getAmount()));
        payload.put("purposeOfPayment", blankToEmpty(req.getPurposeOfPayment()));
        payload.put("transactionReference", blankToEmpty(req.getTransactionReference()));

        DfsPortalTxnResponse resp = wrap("IBFT", from, call(() -> txnClient.ibftAdvice(payload)));
        persist(party, principal, MockTransferProduct.IBFT, req.getAmount(),
                req.getBeneficiaryAccountNo(), null, req.getBeneficiaryName(),
                req.getNotes(), resp);
        return resp;
    }

    public DfsPortalTxnResponse ubpBillers(AccountPrincipal principal) {
        requireActiveParty(principal);
        return wrap("UBP", null, call(() -> txnClient.getBillers()));
    }

    public DfsPortalTxnResponse ubpInquiry(AccountPrincipal principal, LiveBillInquiryRequest req) {
        Party party = requireActiveParty(principal);
        String from = requireMobile(party);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("fromAccountNo", from);
        payload.put("utilityCompanyCode", req.getUtilityCompanyCode().trim());
        payload.put("consumerNo", req.getConsumerNo().trim());
        return wrap("UBP", from, call(() -> txnClient.billInquiry(payload)));
    }

    @Transactional
    public DfsPortalTxnResponse ubpPay(AccountPrincipal principal, LiveBillPaymentRequest req) {
        Party party = requireActiveParty(principal);
        String from = requireMobile(party);
        String nid = requireNid(party);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("fromAccountNo", from);
        payload.put("fromAccountNid", nid);
        payload.put("utilityCompanyCode", req.getUtilityCompanyCode().trim());
        payload.put("consumerNo", req.getConsumerNo().trim());
        payload.put("amount", normalizeAmount(req.getAmount()));
        payload.put("transactionReference", blankToEmpty(req.getTransactionReference()));

        DfsPortalTxnResponse resp = wrap("UBP", from, call(() -> txnClient.billPayment(payload)));
        MockTransfer t = base(party, principal, MockTransferProduct.UBP);
        t.setAmount(parseAmount(req.getAmount()));
        t.setUbpCompany(req.getUtilityCompanyCode());
        t.setConsumerNumber(req.getConsumerNo());
        t.setBeneficiaryName(trim(req.getBeneficiaryName()));
        t.setNotes(trim(req.getNotes()));
        applyStatus(t, resp);
        transferRepository.save(t);
        return resp;
    }

    public DfsPortalTxnResponse ftInitiate(AccountPrincipal principal, LiveFtInitiateRequest req) {
        Party party = requireActiveParty(principal);
        String mobile = requireMobile(party);
        String nid = requireNid(party);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mobileNumber", mobile);
        payload.put("nidNo", nid);
        payload.put("accountNo", IdentityFormats.phoneDigits(req.getAccountNo()));
        payload.put("amount", normalizeAmount(req.getAmount()));
        payload.put("accountType", blankTo(req.getAccountType(), "W"));
        // Never send type=QR from portal (Postman warning)
        return wrap("FT", mobile, call(() -> txnClient.initiateLocalFt(payload)));
    }

    @Transactional
    public DfsPortalTxnResponse ftConfirm(AccountPrincipal principal, LiveFtConfirmRequest req) {
        Party party = requireActiveParty(principal);
        String mobile = requireMobile(party);
        String nid = requireNid(party);
        String appUserId = resolveAppUserId(party, req.getAppUserId());
        if (appUserId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Payer appUserId is required. Set parties.dfs_app_user_id or pass appUserId in the request "
                            + "(DFS APP_USER_ID of the corporate wallet — see Postman customerAppUserId).");
        }
        String mpin = IdentityFormats.pinPlain(req.getMpin());
        if (mpin == null || mpin.length() < 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Valid customer MPIN is required");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mobileNumber", mobile);
        payload.put("nidNo", nid);
        payload.put("accountNo", IdentityFormats.phoneDigits(req.getAccountNo()));
        payload.put("accountType", blankTo(req.getAccountType(), "W"));
        payload.put("amount", normalizeAmount(req.getAmount()));
        payload.put("appUserId", appUserId);
        payload.put("mpin", mpin);
        payload.put("transPurposeId", blankTo(req.getTransPurposeId(), "1"));
        payload.put("narration", blankToEmpty(req.getNarration()));

        DfsPortalTxnResponse resp = wrap("FT", mobile, call(() -> txnClient.fundsTransferLocal(payload)));
        persist(party, principal, MockTransferProduct.FT, req.getAmount(),
                req.getAccountNo(), null, req.getBeneficiaryName(), req.getNarration(), resp);
        return resp;
    }

    public DfsPortalTxnResponse verifyMpin(AccountPrincipal principal, LiveMpinVerifyRequest req) {
        Party party = requireActiveParty(principal);
        String mobile = requireMobile(party);
        String mpin = IdentityFormats.pinPlain(req.getMpin());
        if (mpin == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "mpin is required");
        }
        return wrap("MPIN", mobile, call(() -> appClient.verifyMpin(mobile, mpin)));
    }

    private DfsPortalTxnResponse wrap(String product, String from, JsonNode root) {
        DfsPortalTxnResponse r = DfsPortalTxnResponse.from(root);
        r.setProduct(product);
        r.setFromAccountNo(from);
        return r;
    }

    private JsonNode call(ThrottledCall call) {
        try {
            return call.run();
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrottledCall {
        JsonNode run();
    }

    private void persist(Party party, AccountPrincipal principal, MockTransferProduct product,
                         String amount, String accountNumber, String bankName,
                         String beneficiaryName, String notes, DfsPortalTxnResponse resp) {
        MockTransfer t = base(party, principal, product);
        t.setAmount(parseAmount(amount));
        t.setAccountNumber(trim(accountNumber));
        t.setBankName(trim(bankName));
        t.setBeneficiaryName(trim(beneficiaryName));
        t.setNotes(trim(notes));
        applyStatus(t, resp);
        transferRepository.save(t);
    }

    private void applyStatus(MockTransfer t, DfsPortalTxnResponse resp) {
        String code = resp.getResponsecode();
        boolean ok = "000".equals(code);
        t.setStatus(ok ? "LIVE_SUCCESS" : "LIVE_FAIL_" + (code != null ? code : "UNKNOWN"));
        t.setMockTxnRef("LIVE-" + (code != null ? code : "NA") + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        String msg = resp.getMessages();
        if (msg != null) {
            t.setNotes((t.getNotes() != null ? t.getNotes() + " · " : "") + msg);
        }
    }

    private MockTransfer base(Party party, AccountPrincipal principal, MockTransferProduct product) {
        MockTransfer t = new MockTransfer();
        t.setPublicId(UUID.randomUUID().toString());
        t.setPartyId(party.getId());
        t.setProductType(product);
        t.setMode(MockTransferMode.SINGLE);
        t.setCreatedBy(principal.getUsername());
        t.setMockTxnRef("LIVE-PENDING");
        t.setStatus("LIVE_PENDING");
        return t;
    }

    private Party requireActiveParty(AccountPrincipal principal) {
        if (principal.getPartyId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Merchant party required");
        }
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Transfers available after entity is ACTIVE");
        }
        return party;
    }

    private String requireMobile(Party party) {
        String mobile = IdentityFormats.phoneDigits(party.getPhone());
        if (mobile == null || mobile.length() < 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Party has no valid mobile (fromAccountNo / mobileNumber) for DFS transfers");
        }
        return mobile;
    }

    private String requireNid(Party party) {
        String nid = requireNidOptional(party);
        if (nid == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Party CNIC / NID is required (fromAccountNid / nidNo)");
        }
        return nid;
    }

    private String requireNidOptional(Party party) {
        return IdentityFormats.cnicDigits(party.getCnicNumber());
    }

    private String resolveAppUserId(Party party, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        if (party.getDfsAppUserId() != null && !party.getDfsAppUserId().isBlank()) {
            return party.getDfsAppUserId().trim();
        }
        // Last-resort: local partner app user id (may not match DFS APP_USER_ID — prefer setting dfs_app_user_id)
        List<PartnerAppUser> users = partnerAppUserRepository.findByPartyIdOrderByIdAsc(party.getId());
        if (!users.isEmpty()) {
            return String.valueOf(users.get(0).getId());
        }
        return null;
    }

    private String levelOf(Party party) {
        return party.getLevelCode() != null && !party.getLevelCode().isBlank()
                ? party.getLevelCode().trim() : "L4";
    }

    private static String normalizeAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "amount is required");
        }
        try {
            BigDecimal v = new BigDecimal(amount.trim());
            if (v.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
            }
            return v.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "amount must be numeric");
        }
    }

    private static BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String blankTo(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
