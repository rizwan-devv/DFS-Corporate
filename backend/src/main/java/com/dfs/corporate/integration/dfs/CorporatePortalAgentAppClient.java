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
 * AgentApp corporate portal APIs (token-free; authenticated with X-Portal-Key).
 * Mirrors mobile getbalance / miniStatment / changempin for server-side portal use.
 */
@Component
public class CorporatePortalAgentAppClient {

    private static final Logger log = LoggerFactory.getLogger(CorporatePortalAgentAppClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String portalKey;
    private final String channel;
    private final String getBalancePath;
    private final String miniStatementPath;
    private final String changeMpinPath;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public CorporatePortalAgentAppClient(
            @Value("${dfs.portal-api.enabled:false}") boolean enabled,
            @Value("${dfs.account-api.base-url:http://46.225.160.93:18001}") String baseUrl,
            @Value("${dfs.portal-api.portal-key:}") String portalKey,
            @Value("${dfs.account-api.channel:AGNT}") String channel,
            @Value("${dfs.portal-api.get-balance-path:/agentapp/v1/corporate/getbalance}") String getBalancePath,
            @Value("${dfs.portal-api.mini-statement-path:/agentapp/v1/corporate/miniStatment}") String miniStatementPath,
            @Value("${dfs.portal-api.change-mpin-path:/agentapp/v1/corporate/changempin}") String changeMpinPath,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.baseUrl = trimSlash(baseUrl);
        this.portalKey = portalKey != null ? portalKey.trim() : "";
        this.channel = channel;
        this.getBalancePath = normalizePath(getBalancePath);
        this.miniStatementPath = normalizePath(miniStatementPath);
        this.changeMpinPath = normalizePath(changeMpinPath);
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public JsonNode getBalance(String mobileNumber, String accountLevelCode) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mobileNumber", mobileNumber);
        payload.put("accountLevelCode", accountLevelCode);
        return post(getBalancePath, payload);
    }

    public JsonNode miniStatement(String mobileNumber, String accountLevelCode,
                                  String fromDate, String toDate) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mobileNumber", mobileNumber);
        payload.put("accountLevelCode", accountLevelCode);
        if (fromDate != null && !fromDate.isBlank()) {
            payload.put("fromDate", fromDate);
        }
        if (toDate != null && !toDate.isBlank()) {
            payload.put("toDate", toDate);
        }
        return post(miniStatementPath, payload);
    }

    public JsonNode changeMpin(String mobileNumber, String currentMpin, String newMpin, String confirmMpin) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mobileNumber", mobileNumber);
        payload.put("currentMpin", currentMpin);
        payload.put("newMpin", newMpin);
        payload.put("confirmMpin", confirmMpin);
        return post(changeMpinPath, payload);
    }

    private JsonNode post(String path, ObjectNode payload) {
        ensureReady();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channel", channel);
        body.set("payload", payload);

        try {
            log.info("Calling AgentApp portal API → {}{}", baseUrl, path);
            String raw = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Portal-Key", portalKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw != null ? raw : "{}");
        } catch (RestClientResponseException ex) {
            String msg = "AgentApp HTTP " + ex.getStatusCode().value() + ": " + truncate(ex.getResponseBodyAsString());
            log.warn("{} for {}", msg, path);
            throw new IllegalStateException(msg);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AgentApp portal call failed {}: {}", path, ex.getMessage());
            throw new IllegalStateException("AgentApp call failed: " + ex.getMessage(), ex);
        }
    }

    private void ensureReady() {
        if (!enabled) {
            throw new IllegalStateException(
                    "dfs.portal-api.enabled=false — set DFS_PORTAL_API_ENABLED=true to call AgentApp corporate APIs");
        }
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("dfs.account-api.base-url is empty");
        }
        if (portalKey.isBlank()) {
            throw new IllegalStateException(
                    "CORPORATE_PORTAL_API_KEY / dfs.portal-api.portal-key is not configured");
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        return path.startsWith("/") ? path : "/" + path;
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
