package com.dfs.corporate.integration.dfs;

import com.dfs.corporate.domain.EmployeeBulkRow;
import com.dfs.corporate.domain.Party;
import com.dfs.corporate.util.IdentityFormats;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Live DFS App API: {@code POST /v1/corporate/bulkAccounts} with X-Portal-Key.
 */
@Component
@ConditionalOnProperty(name = "dfs.employee-onboard.use-stub", havingValue = "false", matchIfMissing = true)
public class LiveEmployeeAccountParkClient implements EmployeeAccountParkClient {

    private static final Logger log = LoggerFactory.getLogger(LiveEmployeeAccountParkClient.class);

    private final boolean portalEnabled;
    private final String baseUrl;
    private final String portalKey;
    private final String channel;
    private final String segment;
    private final String path;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LiveEmployeeAccountParkClient(
            @Value("${dfs.portal-api.enabled:false}") boolean portalEnabled,
            @Value("${dfs.portal-api.app-base-url:http://46.225.160.93:18002/app}") String baseUrl,
            @Value("${dfs.portal-api.portal-key:}") String portalKey,
            @Value("${dfs.employee-onboard.channel:COP}") String channel,
            @Value("${dfs.employee-onboard.segment:Corporate Clients Segment}") String segment,
            @Value("${dfs.employee-onboard.bulk-accounts-path:/v1/corporate/bulkAccounts}") String path,
            ObjectMapper objectMapper) {
        this.portalEnabled = portalEnabled;
        this.baseUrl = trimSlash(baseUrl);
        this.portalKey = portalKey != null ? portalKey.trim() : "";
        this.channel = channel != null && !channel.isBlank() ? channel.trim() : "COP";
        this.segment = segment != null && !segment.isBlank() ? segment.trim() : "Corporate Clients Segment";
        this.path = path != null && !path.isBlank() ? (path.startsWith("/") ? path : "/" + path) : "/v1/corporate/bulkAccounts";
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    @Override
    public ParkResult park(Party party, EmployeeBulkRow row) {
        if (!portalEnabled || portalKey.isBlank() || baseUrl.isBlank()) {
            return ParkResult.failed(
                    "Live employee onboard disabled — set DFS_PORTAL_API_ENABLED=true, CORPORATE_PORTAL_API_KEY, and DFS_EMPLOYEE_ONBOARD_USE_STUB=false");
        }
        String mobile = IdentityFormats.phoneDigits(row.getMobile());
        String nid = IdentityFormats.cnicDigits(row.getCnic());
        String title = row.getFullName() != null ? row.getFullName().trim() : "";
        if (mobile == null || mobile.length() < 10) {
            return ParkResult.failed("mobileNo invalid");
        }
        if (nid == null || nid.length() != 13) {
            return ParkResult.failed("nidNo must be 13 digits");
        }
        if (title.isBlank()) {
            return ParkResult.failed("accountTitle (full_name) required");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mobileNo", mobile);
        payload.put("nidNo", nid);
        payload.put("accountTitle", title);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("channel", channel);
        body.put("segment", segment);
        body.set("payload", payload);

        try {
            log.info("DFS bulkAccounts partyId={} row={} mobile={} → {}{}",
                    party.getId(), row.getPublicId(), mobile, baseUrl, path);
            String raw = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Portal-Key", portalKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw != null ? raw : "{}");
            return mapResponse(root, mobile);
        } catch (RestClientResponseException ex) {
            String msg = "DFS HTTP " + ex.getStatusCode().value() + ": " + truncate(ex.getResponseBodyAsString());
            log.warn("bulkAccounts failed row={}: {}", row.getPublicId(), msg);
            return ParkResult.failed(msg);
        } catch (Exception ex) {
            log.warn("bulkAccounts error row={}: {}", row.getPublicId(), ex.getMessage());
            return ParkResult.failed(ex.getMessage() != null ? ex.getMessage() : "DFS bulkAccounts failed");
        }
    }

    private ParkResult mapResponse(JsonNode root, String mobile) {
        String code = text(root, "responsecode", "responseCode");
        String messages = text(root, "messages", "message", "responseDescription");
        JsonNode data = root != null && root.has("data") ? root.get("data") : root;

        boolean ok = code == null || code.isBlank() || "000".equals(code) || "00".equals(code);
        if (!ok) {
            return ParkResult.failed((messages != null ? messages : "DFS rejected") + " (" + code + ")");
        }

        String accountNo = firstNonBlank(
                text(data, "accountNo", "accountNumber", "mobileNo", "fromAccountNo", "walletNo"),
                mobile);
        String customerId = text(data, "customerId", "customerID", "appUserId", "userId", "id");
        String parkRef = firstNonBlank(
                text(data, "reference", "txnRef", "transactionReference", "requestId", "batchId"),
                text(root, "stan", "rrn"),
                "BA-" + mobile);

        String msg = messages != null ? messages : "DFS bulkAccounts accepted";
        if (accountNo != null && !accountNo.isBlank()) {
            return ParkResult.opened(parkRef, msg, accountNo, customerId);
        }
        return ParkResult.parked(parkRef, msg + " — awaiting account confirmation");
    }

    private static String text(JsonNode node, String... keys) {
        if (node == null || node.isNull()) return null;
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) {
                String v = node.get(k).asText(null);
                if (v != null && !v.isBlank()) return v.trim();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }
}
