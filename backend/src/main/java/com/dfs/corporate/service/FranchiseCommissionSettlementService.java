package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.integration.dfs.CorporatePortalAgentAppClient;
import com.dfs.corporate.integration.dfs.CorporatePortalTxnClient;
import com.dfs.corporate.repository.FranchiseCommissionEntryRepository;
import com.dfs.corporate.repository.FranchiseCommissionPlanRepository;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.web.dto.FranchiseCommissionEntryResponse;
import com.dfs.corporate.web.dto.FranchiseCommissionSettleRequest;
import com.dfs.corporate.web.dto.FranchiseCommissionSettleResponse;
import com.dfs.corporate.web.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Inbound franchise commission: on child wallet credits, take locked % and FT child → parent.
 */
@Service
public class FranchiseCommissionSettlementService {

    private static final Logger log = LoggerFactory.getLogger(FranchiseCommissionSettlementService.class);
    public static final String NARRATION_PREFIX = "FRANCHISE-COMM";
    private static final BigDecimal MIN_COMMISSION = new BigDecimal("0.01");
    private static final List<String> OPEN_STATUSES = List.of("PENDING", "FAILED", "NEEDS_MPIN");

    private final FranchiseCommissionPlanRepository planRepository;
    private final FranchiseCommissionEntryRepository entryRepository;
    private final PartyRepository partyRepository;
    private final PartnerAppUserRepository partnerAppUserRepository;
    private final CorporatePortalAgentAppClient agentClient;
    private final CorporatePortalTxnClient txnClient;
    private final DfsWalletIdentityService walletIdentityService;
    private final ObjectMapper objectMapper;
    private final boolean portalEnabled;
    private final String defaultLevelCode;

    public FranchiseCommissionSettlementService(
            FranchiseCommissionPlanRepository planRepository,
            FranchiseCommissionEntryRepository entryRepository,
            PartyRepository partyRepository,
            PartnerAppUserRepository partnerAppUserRepository,
            CorporatePortalAgentAppClient agentClient,
            CorporatePortalTxnClient txnClient,
            DfsWalletIdentityService walletIdentityService,
            ObjectMapper objectMapper,
            @Value("${dfs.portal-api.enabled:false}") boolean portalEnabled,
            @Value("${dfs.account-api.level-code:L4}") String defaultLevelCode) {
        this.planRepository = planRepository;
        this.entryRepository = entryRepository;
        this.partyRepository = partyRepository;
        this.partnerAppUserRepository = partnerAppUserRepository;
        this.agentClient = agentClient;
        this.txnClient = txnClient;
        this.walletIdentityService = walletIdentityService;
        this.objectMapper = objectMapper;
        this.portalEnabled = portalEnabled;
        this.defaultLevelCode = defaultLevelCode;
    }

    public List<FranchiseCommissionEntryResponse> listForParent(AccountPrincipal principal) {
        Party parent = requireActiveMaster(principal);
        return entryRepository.findByParentPartyIdOrderByPostedAtDesc(parent.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public FranchiseCommissionSettleResponse settleForParent(AccountPrincipal principal,
                                                             FranchiseCommissionSettleRequest req) {
        Party parent = requireActiveMaster(principal);
        if (!portalEnabled || !txnClient.isEnabled() || !agentClient.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Live DFS portal APIs must be enabled to settle commission");
        }
        Long onlyChild = req != null ? req.getChildPartyId() : null;
        String overrideMpin = req != null ? IdentityFormats.pinPlain(req.getMpin()) : null;
        return settleLockedPlans(parent.getId(), onlyChild, overrideMpin);
    }

    /** Scheduler — all locked plans. */
    public FranchiseCommissionSettleResponse settleAllLocked() {
        if (!portalEnabled || !txnClient.isEnabled() || !agentClient.isEnabled()) {
            return emptyRun("Live DFS portal APIs disabled");
        }
        return settleLockedPlans(null, null, null);
    }

    private FranchiseCommissionSettleResponse settleLockedPlans(Long parentId, Long onlyChild, String overrideMpin) {
        List<FranchiseCommissionPlan> plans = planRepository.findByStatus(CommissionPlanStatus.LOCKED);
        int scanned = 0;
        int created = 0;
        int posted = 0;
        int failed = 0;
        int skipped = 0;
        for (FranchiseCommissionPlan plan : plans) {
            if (parentId != null && !parentId.equals(plan.getParentPartyId())) continue;
            if (onlyChild != null && !onlyChild.equals(plan.getChildPartyId())) continue;
            try {
                ScanResult r = settleOneChild(plan, overrideMpin);
                scanned += r.scanned;
                created += r.created;
                posted += r.posted;
                failed += r.failed;
                skipped += r.skipped;
            } catch (Exception ex) {
                failed++;
                log.warn("Commission settle child={} failed: {}", plan.getChildPartyId(), ex.getMessage());
            }
        }
        FranchiseCommissionSettleResponse out = new FranchiseCommissionSettleResponse();
        out.setScannedCredits(scanned);
        out.setCreated(created);
        out.setPosted(posted);
        out.setFailed(failed);
        out.setSkipped(skipped);
        out.setMessage("Inbound credits: " + scanned + " · new " + created
                + " · paid to parent " + posted + " · failed " + failed);
        return out;
    }

    private ScanResult settleOneChild(FranchiseCommissionPlan plan, String overrideMpin) {
        Party child = partyRepository.findById(plan.getChildPartyId()).orElse(null);
        Party parent = partyRepository.findById(plan.getParentPartyId()).orElse(null);
        if (child == null || parent == null
                || child.getStatus() != PartyStatus.ACTIVE
                || parent.getStatus() != PartyStatus.ACTIVE) {
            return ScanResult.empty();
        }
        String childMobile = IdentityFormats.phoneDigits(child.getPhone());
        String parentMobile = IdentityFormats.phoneDigits(parent.getPhone());
        if (childMobile == null || parentMobile == null || childMobile.equals(parentMobile)) {
            return ScanResult.empty();
        }

        child = walletIdentityService.refreshFromDfs(child);
        parent = walletIdentityService.refreshFromDfs(parent);
        childMobile = IdentityFormats.phoneDigits(child.getPhone());
        parentMobile = IdentityFormats.phoneDigits(parent.getPhone());

        String level = child.getLevelCode() != null && !child.getLevelCode().isBlank()
                ? child.getLevelCode().trim() : defaultLevelCode;
        // Same as Onboarded: omit dates so AgentApp returns the latest credits.
        JsonNode root = agentClient.miniStatement(childMobile, level, null, null);
        List<JsonNode> rows = extractRows(root);
        log.info("Commission scan child={} mobile={} statementRows={}", child.getId(), childMobile, rows.size());
        int scanned = 0;
        int created = 0;
        for (JsonNode row : rows) {
            if (!isInboundCredit(row)) continue;
            if (isOwnCommissionNarration(row)) continue;
            if (isFundingCredit(row)) continue;
            BigDecimal gross = decimal(row, "txnAmt", "txnAmount", "amount");
            if (gross == null || gross.compareTo(BigDecimal.ZERO) <= 0) continue;
            scanned++;
            String sourceRef = sourceRef(child.getId(), row);
            if (entryRepository.existsByChildPartyIdAndSourceRef(child.getId(), sourceRef)) continue;
            BigDecimal rate = plan.getCommissionRatePercent();
            BigDecimal commission = gross.multiply(rate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (commission.compareTo(MIN_COMMISSION) < 0) continue;
            FranchiseCommissionEntry e = new FranchiseCommissionEntry();
            e.setPublicId(java.util.UUID.randomUUID().toString());
            e.setParentPartyId(parent.getId());
            e.setChildPartyId(child.getId());
            e.setPlanId(plan.getId());
            e.setSourceRef(sourceRef);
            e.setInboundRef(text(row, "transRefnum", "authIdResponse", "txnRef"));
            e.setInboundAt(text(row, "transDate"));
            e.setGrossAmount(gross);
            e.setRatePercent(rate);
            e.setCommissionAmount(commission);
            e.setStatus("PENDING");
            e.setPostedAt(Instant.now());
            entryRepository.save(e);
            created++;
        }

        String mpin = overrideMpin != null ? overrideMpin : resolveMpin(child);
        int posted = 0;
        int failed = 0;
        int skipped = 0;
        List<FranchiseCommissionEntry> open = entryRepository.findByChildPartyIdAndStatusIn(
                child.getId(), OPEN_STATUSES);
        for (FranchiseCommissionEntry e : open) {
            if (mpin == null) {
                e.setStatus("NEEDS_MPIN");
                e.setErrorMessage("Child wallet MPIN not on file — enter MPIN and settle again");
                entryRepository.save(e);
                skipped++;
                continue;
            }
            try {
                postToParent(child, parent, parentMobile, mpin, e);
                posted++;
            } catch (Exception ex) {
                e.setStatus("FAILED");
                e.setErrorMessage(trimErr(ex.getMessage()));
                entryRepository.save(e);
                failed++;
                log.warn("Commission FT failed entry={} child={}: {}", e.getId(), child.getId(), ex.getMessage());
            }
        }
        return new ScanResult(scanned, created, posted, failed, skipped);
    }

    private void postToParent(Party child, Party parent, String parentMobile, String mpin,
                              FranchiseCommissionEntry e) {
        String nid = requireNid(child);
        String appUserId = DfsWalletIdentityService.requireDfsAppUserId(child);
        if (appUserId == null) {
            throw new IllegalStateException(
                    "Child DFS APP_USER_ID missing after accountDetails — cannot use local partner id");
        }
        if (!DfsWalletIdentityService.isUsableCnic(nid)) {
            throw new IllegalStateException("Child CNIC from DFS is missing or invalid");
        }
        String lastError = null;
        for (String accountType : List.of("W", "10")) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("mobileNumber", IdentityFormats.phoneDigits(child.getPhone()));
            payload.put("nidNo", nid);
            payload.put("accountNo", parentMobile);
            payload.put("accountType", accountType);
            payload.put("amount", e.getCommissionAmount().stripTrailingZeros().toPlainString());
            payload.put("appUserId", appUserId);
            payload.put("mpin", mpin);
            payload.put("transPurposeId", "1");
            payload.put("narration", NARRATION_PREFIX + " " + e.getPublicId().substring(0, 8));

            JsonNode root = txnClient.fundsTransferLocal(payload);
            String code = text(root, "responsecode", "responseCode");
            if (code == null && root != null && root.has("data")) {
                code = text(root.get("data"), "responsecode", "responseCode");
            }
            if ("000".equals(code) || "00".equals(code)) {
                JsonNode data = root != null && root.has("data") ? root.get("data") : root;
                e.setStatus("POSTED");
                e.setDfsAuthId(text(data, "authIdResponse", "authId"));
                e.setErrorMessage(null);
                e.setSettledAt(Instant.now());
                entryRepository.save(e);
                return;
            }
            lastError = text(root, "messages", "message");
            if (lastError == null) lastError = "DFS FT " + code;
            if (lastError != null && !lastError.toLowerCase(Locale.ROOT).contains("account not found")) {
                break;
            }
        }
        throw new IllegalStateException(lastError != null ? lastError : "DFS FT failed");
    }

    private Party requireActiveMaster(AccountPrincipal principal) {
        if (principal.getPartyId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Merchant party required");
        }
        Party parent = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (parent.getPartyType() != PartyType.MERCHANT || parent.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only an ACTIVE master can settle commission");
        }
        return parent;
    }

    private String resolveMpin(Party child) {
        String pin = IdentityFormats.pinPlain(child.getWalletPin());
        if (pin != null && pin.length() >= 4) return pin;
        for (PartnerAppUser u : partnerAppUserRepository.findByPartyIdOrderByIdAsc(child.getId())) {
            pin = IdentityFormats.pinPlain(u.getWalletPin());
            if (pin != null && pin.length() >= 4) return pin;
        }
        return null;
    }

    private String requireNid(Party child) {
        String nid = IdentityFormats.cnicDigits(child.getCnicNumber());
        if (nid != null && nid.length() >= 12) return nid;
        for (PartnerAppUser u : partnerAppUserRepository.findByPartyIdOrderByIdAsc(child.getId())) {
            nid = IdentityFormats.cnicDigits(u.getCnicNumber());
            if (nid != null && nid.length() >= 12) return nid;
        }
        throw new IllegalStateException("Child CNIC is required for commission FT");
    }

    private static boolean isInboundCredit(JsonNode row) {
        String t = text(row, "amountType", "amtType", "drCr");
        if (t == null) return false;
        String u = t.trim().toUpperCase(Locale.ROOT);
        return u.equals("C") || u.equals("CR") || u.startsWith("C");
    }

    private static boolean isOwnCommissionNarration(JsonNode row) {
        String d = descr(row);
        return d != null && d.toUpperCase(Locale.ROOT).contains(NARRATION_PREFIX);
    }

    /** Opening / GL loads are not franchise collections. */
    private static boolean isFundingCredit(JsonNode row) {
        String d = descr(row);
        if (d == null) return false;
        String u = d.toUpperCase(Locale.ROOT);
        return u.contains("GL TO WALLET") || u.contains("GL-") || u.contains("OPENING");
    }

    private static String descr(JsonNode row) {
        return text(row, "transDocsDescr", "narration", "description", "remarks");
    }

    private static String sourceRef(Long childId, JsonNode row) {
        String ref = text(row, "transRefnum", "authIdResponse", "txnRef");
        if (ref != null && !ref.isBlank()) {
            return trimTo(childId + ":" + ref.trim(), 190);
        }
        String raw = text(row, "transDate") + "|" + text(row, "txnAmt") + "|"
                + text(row, "fromAccountNo") + "|" + text(row, "transDocsDescr");
        return trimTo(childId + ":" + sha12(raw), 190);
    }

    private static List<JsonNode> extractRows(JsonNode root) {
        List<JsonNode> out = new ArrayList<>();
        collectTxnRows(root, out);
        return out;
    }

    private static void collectTxnRows(JsonNode n, List<JsonNode> out) {
        if (n == null || n.isNull()) return;
        if (n.isArray()) {
            n.forEach(item -> {
                if (looksLikeTxn(item)) out.add(item);
                else collectTxnRows(item, out);
            });
            return;
        }
        if (!n.isObject()) return;
        for (String key : List.of("transactions", "list", "records", "data", "items",
                "transactionList", "miniStatement", "statement", "stmt")) {
            if (n.has(key)) collectTxnRows(n.get(key), out);
        }
        if (out.isEmpty()) {
            n.fields().forEachRemaining(e -> {
                if (e.getValue() != null && e.getValue().isArray()) {
                    collectTxnRows(e.getValue(), out);
                }
            });
        }
    }

    private static boolean looksLikeTxn(JsonNode n) {
        if (n == null || !n.isObject()) return false;
        return n.has("txnAmt") || n.has("transRefnum") || n.has("amountType")
                || n.has("transDate") || n.has("transDocsDescr");
    }

    private static BigDecimal decimal(JsonNode n, String... keys) {
        if (n == null || n.isNull()) return null;
        for (String k : keys) {
            if (!n.has(k) || n.get(k).isNull()) continue;
            JsonNode v = n.get(k);
            if (v.isNumber()) return v.decimalValue();
            String t = v.asText(null);
            if (t == null || t.isBlank()) continue;
            try {
                return new BigDecimal(t.trim());
            } catch (Exception ignored) {
                /* next key */
            }
        }
        return null;
    }

    private static String text(JsonNode n, String... keys) {
        if (n == null || n.isNull()) return null;
        for (String k : keys) {
            if (n.has(k) && !n.get(k).isNull()) {
                String v = n.get(k).asText(null);
                if (v != null && !v.isBlank()) return v.trim();
            }
        }
        return null;
    }

    private static String sha12(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 12);
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private static String trimTo(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String trimErr(String s) {
        if (s == null) return "FT failed";
        return s.length() > 480 ? s.substring(0, 480) : s;
    }

    private FranchiseCommissionEntryResponse toResponse(FranchiseCommissionEntry e) {
        FranchiseCommissionEntryResponse r = new FranchiseCommissionEntryResponse();
        r.setPublicId(e.getPublicId());
        r.setChildPartyId(e.getChildPartyId());
        r.setParentPartyId(e.getParentPartyId());
        partyRepository.findById(e.getChildPartyId()).ifPresent(c -> {
            r.setChildBusinessName(c.getBusinessName());
            r.setChildTrackingId(c.getTrackingId());
        });
        r.setInboundRef(e.getInboundRef());
        r.setInboundAt(e.getInboundAt());
        r.setGrossAmount(e.getGrossAmount());
        r.setRatePercent(e.getRatePercent());
        r.setCommissionAmount(e.getCommissionAmount());
        r.setStatus(e.getStatus());
        r.setDfsAuthId(e.getDfsAuthId());
        r.setErrorMessage(e.getErrorMessage());
        r.setPostedAt(e.getPostedAt());
        r.setSettledAt(e.getSettledAt());
        return r;
    }

    private static FranchiseCommissionSettleResponse emptyRun(String msg) {
        FranchiseCommissionSettleResponse r = new FranchiseCommissionSettleResponse();
        r.setMessage(msg);
        return r;
    }

    private record ScanResult(int scanned, int created, int posted, int failed, int skipped) {
        static ScanResult empty() {
            return new ScanResult(0, 0, 0, 0, 0);
        }
    }
}
