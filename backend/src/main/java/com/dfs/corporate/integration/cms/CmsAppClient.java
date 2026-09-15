package com.dfs.corporate.integration.cms;

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

import java.util.concurrent.atomic.AtomicReference;

/**
 * CMS App API (:8016) — card inquiry (masked / unmask with PIN) + status + LOVs.
 * Auth: X-Api-Key + POST /user/login → Bearer token.
 */
@Component
public class CmsAppClient {

    private static final Logger log = LoggerFactory.getLogger(CmsAppClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final AtomicReference<String> cachedToken = new AtomicReference<>();

    public CmsAppClient(
            @Value("${dfs.cms-api.enabled:false}") boolean enabled,
            @Value("${dfs.cms-api.app-base-url:http://46.224.146.158:8016}") String baseUrl,
            @Value("${dfs.cms-api.app-api-key:}") String apiKey,
            @Value("${dfs.cms-api.app-username:}") String username,
            @Value("${dfs.cms-api.app-password:}") String password,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.baseUrl = trimSlash(baseUrl);
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.username = username != null ? username.trim() : "";
        this.password = password != null ? password : "";
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public JsonNode inquire(String relationshipNum, String pinOrNull) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("relationshipNum", relationshipNum);
        if (pinOrNull != null && !pinOrNull.isBlank()) {
            body.put("pin", pinOrNull);
        }
        return withAuthRetry(() -> post("/card/inquiry", body, token()));
    }

    public JsonNode updateStatus(String pan, String statusCode) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("pan", pan);
        body.put("statusCode", statusCode);
        return withAuthRetry(() -> post("/card/update-status", body, token()));
    }

    public JsonNode statusLov() {
        return withAuthRetry(() -> get("/card/lov/status", token()));
    }

    private JsonNode withAuthRetry(Call call) {
        ensureReady();
        try {
            return call.run();
        } catch (UnauthorizedException ex) {
            cachedToken.set(null);
            return call.run();
        }
    }

    private String token() {
        String existing = cachedToken.get();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        synchronized (cachedToken) {
            existing = cachedToken.get();
            if (existing != null && !existing.isBlank()) {
                return existing;
            }
            String t = login();
            cachedToken.set(t);
            return t;
        }
    }

    private String login() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("username", username);
        body.put("password", password);
        try {
            log.info("CMS App login → {}/user/login", baseUrl);
            String raw = restClient.post()
                    .uri("/user/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Api-Key", apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(raw != null ? raw : "{}");
            JsonNode rb = json.path("responseBody");
            String t = text(rb, "token");
            if (t.isBlank()) t = text(rb, "accessToken");
            if (t.isBlank()) {
                throw new IllegalStateException("CMS App login succeeded but no token in response");
            }
            return t;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("CMS App login HTTP " + ex.getStatusCode().value()
                    + ": " + truncate(ex.getResponseBodyAsString()));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("CMS App login failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode get(String path, String bearer) {
        try {
            String raw = restClient.get()
                    .uri(path)
                    .header("X-Api-Key", apiKey)
                    .header("Authorization", "Bearer " + bearer)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw != null ? raw : "{}");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw new UnauthorizedException();
            throw httpError("GET", path, ex);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("CMS App GET " + path + " failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode post(String path, ObjectNode body, String bearer) {
        try {
            String raw = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Api-Key", apiKey)
                    .header("Authorization", "Bearer " + bearer)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw != null ? raw : "{}");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw new UnauthorizedException();
            throw httpError("POST", path, ex);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("CMS App POST " + path + " failed: " + ex.getMessage(), ex);
        }
    }

    private void ensureReady() {
        if (!enabled) {
            throw new IllegalStateException(
                    "dfs.cms-api.enabled=false — set DFS_CMS_API_ENABLED=true to call CMS");
        }
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("dfs.cms-api.app-base-url is empty");
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("DFS_CMS_APP_API_KEY not configured");
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "DFS_CMS_APP_USERNAME / DFS_CMS_APP_PASSWORD not configured");
        }
    }

    private static IllegalStateException httpError(String method, String path, RestClientResponseException ex) {
        return new IllegalStateException("CMS App " + method + " " + path + " HTTP "
                + ex.getStatusCode().value() + ": " + truncate(ex.getResponseBodyAsString()));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
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

    @FunctionalInterface
    private interface Call {
        JsonNode run();
    }

    private static final class UnauthorizedException extends RuntimeException {}
}
