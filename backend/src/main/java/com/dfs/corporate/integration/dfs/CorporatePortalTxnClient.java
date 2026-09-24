package com.dfs.corporate.integration.dfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * DFS Transactions corporate portal APIs (token-free; X-Portal-Key).
 * <p>
 * Per Postman "DFS - Corporate Portal":
 * <ul>
 *   <li>IBFT bankList is POST (empty payload) — not GET</li>
 *   <li>getbiller is the only GET</li>
 *   <li>Business outcomes return HTTP 200; branch on {@code responsecode}</li>
 * </ul>
 */
@Component
public class CorporatePortalTxnClient {

    private static final Logger log = LoggerFactory.getLogger(CorporatePortalTxnClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String portalKey;
    private final String channel;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public CorporatePortalTxnClient(
            @Value("${dfs.portal-api.enabled:false}") boolean enabled,
            @Value("${dfs.portal-api.txn-base-url:http://46.225.160.93:18009/transactions}") String baseUrl,
            @Value("${dfs.portal-api.portal-key:}") String portalKey,
            @Value("${dfs.portal-api.channel:MOB}") String channel,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.baseUrl = trimSlash(baseUrl);
        this.portalKey = portalKey != null ? portalKey.trim() : "";
        this.channel = channel != null && !channel.isBlank() ? channel : "MOB";
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    public boolean isEnabled() {
        return enabled && !portalKey.isBlank() && !baseUrl.isBlank();
    }

    public boolean isConfigured() {
        return !portalKey.isBlank() && !baseUrl.isBlank();
    }

    /** POST /v1/corporate/ibft/bankList — empty payload (Postman Step 1). */
    public JsonNode ibftBankList() {
        return post("/v1/corporate/ibft/bankList", objectMapper.createObjectNode());
    }

    public JsonNode ibftTitleFetch(ObjectNode payload) {
        return post("/v1/corporate/ibft/titleFetch", payload);
    }

    public JsonNode ibftAdvice(ObjectNode payload) {
        return post("/v1/corporate/ibft/advice", payload);
    }

    /** GET /v1/corporate/getbiller */
    public JsonNode getBillers() {
        ensureReady();
        try {
            log.info("Calling DFS txn GET → {}/v1/corporate/getbiller", baseUrl);
            String raw = restClient.get()
                    .uri("/v1/corporate/getbiller")
                    .header("X-Portal-Key", portalKey)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw != null ? raw : "{}");
        } catch (RestClientResponseException ex) {
            throw httpError("getbiller", ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DFS getbiller failed: " + ex.getMessage(), ex);
        }
    }

    public JsonNode billInquiry(ObjectNode payload) {
        return post("/v1/corporate/billInquiry", payload);
    }

    public JsonNode billPayment(ObjectNode payload) {
        return post("/v1/corporate/billPayment", payload);
    }

    public JsonNode initiateLocalFt(ObjectNode payload) {
        return post("/v1/corporate/initiateLocalFT", payload, "COP");
    }

    public JsonNode fundsTransferLocal(ObjectNode payload) {
        return post("/v1/corporate/fundsTransferLocal", payload, "COP");
    }

    private JsonNode post(String path, ObjectNode payload) {
        return post(path, payload, channel);
    }

    private JsonNode post(String path, ObjectNode payload, String requestChannel) {
        ensureReady();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channel", requestChannel != null && !requestChannel.isBlank() ? requestChannel : channel);
        body.set("payload", payload != null ? payload : objectMapper.createObjectNode());

        try {
            log.info("Calling DFS txn POST channel={} → {}{}", body.get("channel").asText(), baseUrl, path);
            String raw = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Portal-Key", portalKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw != null ? raw : "{}");
        } catch (RestClientResponseException ex) {
            throw httpError(path, ex);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("DFS txn call failed {}: {}", path, ex.getMessage());
            throw new IllegalStateException("DFS txn call failed: " + ex.getMessage(), ex);
        }
    }

    private IllegalStateException httpError(String path, RestClientResponseException ex) {
        String msg = "DFS txn HTTP " + ex.getStatusCode().value() + ": " + truncate(ex.getResponseBodyAsString());
        log.warn("{} for {}", msg, path);
        return new IllegalStateException(msg);
    }

    private void ensureReady() {
        if (!enabled) {
            throw new IllegalStateException(
                    "Live transfers disabled — set DFS_PORTAL_API_ENABLED=true and CORPORATE_PORTAL_API_KEY");
        }
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("dfs.portal-api.txn-base-url is empty");
        }
        if (portalKey.isBlank()) {
            throw new IllegalStateException("CORPORATE_PORTAL_API_KEY is not configured");
        }
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
