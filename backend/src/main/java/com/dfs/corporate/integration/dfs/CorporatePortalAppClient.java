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
 * DFS App corporate portal APIs — customer MPIN verification (X-Portal-Key).
 */
@Component
public class CorporatePortalAppClient {

    private static final Logger log = LoggerFactory.getLogger(CorporatePortalAppClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String portalKey;
    private final String channel;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public CorporatePortalAppClient(
            @Value("${dfs.portal-api.enabled:false}") boolean enabled,
            @Value("${dfs.portal-api.app-base-url:http://46.225.160.93:18002/app}") String baseUrl,
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

    public JsonNode verifyMpin(String mobileNumber, String mpin) {
        ensureReady();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mobileNumber", mobileNumber);
        payload.put("mpin", mpin);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("channel", channel);
        body.set("payload", payload);

        try {
            log.info("Calling DFS app MPIN verify → {}/v1/corporate/mpinVerification", baseUrl);
            String raw = restClient.post()
                    .uri("/v1/corporate/mpinVerification")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Portal-Key", portalKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw != null ? raw : "{}");
        } catch (RestClientResponseException ex) {
            String msg = "DFS app HTTP " + ex.getStatusCode().value() + ": " + truncate(ex.getResponseBodyAsString());
            log.warn(msg);
            throw new IllegalStateException(msg);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DFS MPIN verify failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Corporate account details including Raast {@code qrCode} / IBAN.
     * {@code POST /v1/corporate/accountDetails}
     */
    public JsonNode accountDetails(String mobileNumber) {
        ensureReady();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mobileNumber", mobileNumber);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("channel", channel);
        body.set("payload", payload);

        try {
            log.info("Calling DFS app accountDetails → {}/v1/corporate/accountDetails mobile={}", baseUrl, mobileNumber);
            String raw = restClient.post()
                    .uri("/v1/corporate/accountDetails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Portal-Key", portalKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw != null ? raw : "{}");
        } catch (RestClientResponseException ex) {
            String msg = "DFS app HTTP " + ex.getStatusCode().value() + ": " + truncate(ex.getResponseBodyAsString());
            log.warn(msg);
            throw new IllegalStateException(msg);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DFS accountDetails failed: " + ex.getMessage(), ex);
        }
    }

    private void ensureReady() {
        if (!enabled) {
            throw new IllegalStateException(
                    "Live transfers disabled — set DFS_PORTAL_API_ENABLED=true and CORPORATE_PORTAL_API_KEY");
        }
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("dfs.portal-api.app-base-url is empty");
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
