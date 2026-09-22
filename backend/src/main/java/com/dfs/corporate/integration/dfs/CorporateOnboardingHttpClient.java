package com.dfs.corporate.integration.dfs;

import com.dfs.corporate.domain.PartnerAppUser;
import com.dfs.corporate.domain.Party;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Calls DFS backend corporate account API:
 * POST {baseUrl}/agentapp/v1/corporateonboarding
 * GET  {baseUrl}/agentapp/v1/getAllSegments
 * GET  {baseUrl}/agentapp/v1/getAllLovs
 */
@Primary
@Component
public class CorporateOnboardingHttpClient implements DfsAccountClient {

    private static final Logger log = LoggerFactory.getLogger(CorporateOnboardingHttpClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String onboardingPath;
    private final String segmentsPath;
    private final String lovsPath;
    private final CorporateOnboardingMapper mapper;
    private final PartnerAppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public CorporateOnboardingHttpClient(
            @Value("${dfs.account-api.enabled:false}") boolean enabled,
            @Value("${dfs.account-api.base-url:http://46.225.160.93:18001}") String baseUrl,
            @Value("${dfs.account-api.path:/agentapp/v1/corporateonboarding}") String onboardingPath,
            @Value("${dfs.account-api.segments-path:/agentapp/v1/getAllSegments}") String segmentsPath,
            @Value("${dfs.account-api.lovs-path:/agentapp/v1/getAllLovs}") String lovsPath,
            CorporateOnboardingMapper mapper,
            PartnerAppUserRepository appUserRepository,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.baseUrl = trimSlash(baseUrl);
        this.onboardingPath = onboardingPath.startsWith("/") ? onboardingPath : "/" + onboardingPath;
        this.segmentsPath = segmentsPath.startsWith("/") ? segmentsPath : "/" + segmentsPath;
        this.lovsPath = lovsPath.startsWith("/") ? lovsPath : "/" + lovsPath;
        this.mapper = mapper;
        this.appUserRepository = appUserRepository;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    @Override
    public DfsAccountCreateResult createAccount(Party party) {
        if (!enabled) {
            log.info("DFS backend corporateonboarding disabled — party {} PENDING", party.getTrackingId());
            return DfsAccountCreateResult.deferred(
                    "dfs.account-api.enabled=false — enable to call DFS backend corporateonboarding");
        }
        if (baseUrl.isBlank()) {
            return DfsAccountCreateResult.failed("dfs.account-api.base-url is empty");
        }

        List<PartnerAppUser> users = appUserRepository.findByPartyIdOrderByIdAsc(party.getId());
        CorporateOnboardingRequest body = mapper.map(party, users);

        try {
            log.info("Calling DFS backend corporateonboarding for party {} → {}{}",
                    party.getTrackingId(), baseUrl, onboardingPath);
            String raw = restClient.post()
                    .uri(onboardingPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseResult(raw);
        } catch (RestClientResponseException ex) {
            String msg = "DFS backend HTTP " + ex.getStatusCode().value() + ": " + truncate(ex.getResponseBodyAsString());
            log.warn("corporateonboarding failed for {}: {}", party.getTrackingId(), msg);
            return DfsAccountCreateResult.failed(msg);
        } catch (Exception ex) {
            log.warn("corporateonboarding error for {}: {}", party.getTrackingId(), ex.getMessage());
            return DfsAccountCreateResult.failed(ex.getMessage());
        }
    }

    public JsonNode getAllSegments() {
        String raw = restClient.get()
                .uri(segmentsPath)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readTree(raw != null ? raw : "{}");
        } catch (Exception e) {
            throw new IllegalStateException("Invalid segments response: " + e.getMessage(), e);
        }
    }

    /**
     * GET /agentapp/v1/getAllLovs — returns DFS payload as-is (responsecode + data + messages).
     * Used after OTP verify for KYC app dropdowns (city, occupation, businessType, …).
     */
    public JsonNode getAllLovs() {
        log.info("Calling DFS getAllLovs → {}{}", baseUrl, lovsPath);
        String raw = restClient.get()
                .uri(lovsPath)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readTree(raw != null ? raw : "{}");
        } catch (Exception e) {
            throw new IllegalStateException("Invalid getAllLovs response: " + e.getMessage(), e);
        }
    }

    private DfsAccountCreateResult parseResult(String raw) {
        if (raw == null || raw.isBlank()) {
            return DfsAccountCreateResult.failed("Empty response from DFS backend");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            String code = text(root, "responsecode", "responseCode", "code", "statusCode");
            String message = text(root, "messages", "message", "statusMessage");
            boolean ok = isSuccessCode(code) || root.path("success").asBoolean(false);

            // Prefer wallet / account numbers — never take a bare short "id" first (caused dfs_account_id=106)
            String accountId = pickAccountId(root);
            if (accountId == null && root.has("data") && root.get("data").isObject()) {
                accountId = pickAccountId(root.get("data"));
            } else if (accountId == null && root.has("data") && root.get("data").isTextual()) {
                accountId = root.get("data").asText();
            }

            if (ok) {
                if (accountId == null || accountId.isBlank()) {
                    accountId = "DFS-" + (code != null ? code : "OK");
                }
                return DfsAccountCreateResult.ok(accountId);
            }
            return DfsAccountCreateResult.failed(
                    firstNonBlank(message, "DFS backend rejected: " + truncate(raw)));
        } catch (Exception e) {
            // Non-JSON success body — treat as opaque success id
            if (raw.toLowerCase().contains("success")) {
                return DfsAccountCreateResult.ok(truncate(raw));
            }
            return DfsAccountCreateResult.failed("Unparseable DFS backend response: " + truncate(raw));
        }
    }

    /**
     * Extract DFS wallet / account key for parties.dfs_account_id.
     * Prefer accountNo / mobile / long numeric ids; avoid short internal "id" fields.
     */
    private static String pickAccountId(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        String preferred = text(node,
                "accountNo", "accountNumber", "ACCOUNT_NO", "mobileNumber", "mobileNo", "msisdn",
                "dfsAccountId", "accountId", "walletId", "agentId");
        if (preferred != null && looksLikeStoredAccountId(preferred)) {
            return preferred.trim();
        }
        if (preferred != null && !preferred.isBlank() && !looksLikeShortInternalId(preferred)) {
            return preferred.trim();
        }
        String bareId = text(node, "id");
        if (bareId != null && looksLikeStoredAccountId(bareId)) {
            return bareId.trim();
        }
        // Last resort: non-short preferred field even if not 12–14 digits (e.g. alphanumeric)
        if (preferred != null && !preferred.isBlank() && !looksLikeShortInternalId(preferred)) {
            return preferred.trim();
        }
        return null;
    }

    private static boolean looksLikeStoredAccountId(String v) {
        if (v == null) return false;
        String t = v.trim();
        // 11-digit mobile wallet or 12–14 digit account/CNIC-style
        return t.matches("\\d{11,14}");
    }

    private static boolean looksLikeShortInternalId(String v) {
        if (v == null) return true;
        String t = v.trim();
        return t.matches("\\d{1,6}");
    }

    private static boolean isSuccessCode(String code) {
        if (code == null) return false;
        String c = code.trim();
        return "000".equals(c) || "0".equals(c) || "00".equals(c)
                || "200".equals(c) || "SUCCESS".equalsIgnoreCase(c) || "OK".equalsIgnoreCase(c);
    }

    private static String text(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode n = node.get(f);
            if (n != null && !n.isNull() && n.isValueNode() && !n.asText().isBlank()) {
                return n.asText();
            }
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}
