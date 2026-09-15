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
 * CMS Portal API (:7070) — card inventory / search / detail / status.
 * Auth: POST /api/auth/login → Bearer token (cached until 401).
 */
@Component
public class CmsPortalClient {

    private static final Logger log = LoggerFactory.getLogger(CmsPortalClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String loginId;
    private final String password;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final AtomicReference<String> cachedToken = new AtomicReference<>();

    public CmsPortalClient(
            @Value("${dfs.cms-api.enabled:false}") boolean enabled,
            @Value("${dfs.cms-api.portal-base-url:http://46.224.146.158:7070}") String baseUrl,
            @Value("${dfs.cms-api.portal-login-id:}") String loginId,
            @Value("${dfs.cms-api.portal-password:}") String password,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.baseUrl = trimSlash(baseUrl);
        this.loginId = loginId != null ? loginId.trim() : "";
        this.password = password != null ? password : "";
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public JsonNode searchCards(ObjectNode body) {
        return withAuthRetry(() -> post("/api/cards/search", body, token()));
    }

    public JsonNode listCards() {
        return withAuthRetry(() -> get("/api/cards", token()));
    }

    public JsonNode getCard(String cardId) {
        return withAuthRetry(() -> get("/api/cards/" + cardId, token()));
    }

    public JsonNode dropdowns() {
        return withAuthRetry(() -> get("/api/cards/dropdowns", token()));
    }

    public JsonNode updateCard(String cardId, ObjectNode body) {
        return withAuthRetry(() -> put("/api/cards/" + cardId, body, token()));
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
        body.put("loginId", loginId);
        body.put("password", password);
        try {
            log.info("CMS Portal login → {}/api/auth/login", baseUrl);
            String raw = restClient.post()
                    .uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(raw != null ? raw : "{}");
            JsonNode data = json.path("data");
            String t = text(data, "token");
            if (t.isBlank()) t = text(data, "accessToken");
            if (t.isBlank()) {
                throw new IllegalStateException("CMS Portal login succeeded but no token in response");
            }
            return t;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("CMS Portal login HTTP " + ex.getStatusCode().value()
                    + ": " + truncate(ex.getResponseBodyAsString()));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("CMS Portal login failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode get(String path, String bearer) {
        try {
            String raw = restClient.get()
                    .uri(path)
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
            throw new IllegalStateException("CMS Portal GET " + path + " failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode post(String path, ObjectNode body, String bearer) {
        try {
            String raw = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
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
            throw new IllegalStateException("CMS Portal POST " + path + " failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode put(String path, ObjectNode body, String bearer) {
        try {
            String raw = restClient.put()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + bearer)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw != null ? raw : "{}");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) throw new UnauthorizedException();
            throw httpError("PUT", path, ex);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("CMS Portal PUT " + path + " failed: " + ex.getMessage(), ex);
        }
    }

    private void ensureReady() {
        if (!enabled) {
            throw new IllegalStateException(
                    "dfs.cms-api.enabled=false — set DFS_CMS_API_ENABLED=true to call CMS");
        }
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("dfs.cms-api.portal-base-url is empty");
        }
        if (loginId.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "DFS_CMS_PORTAL_LOGIN_ID / DFS_CMS_PORTAL_PASSWORD not configured");
        }
    }

    private static IllegalStateException httpError(String method, String path, RestClientResponseException ex) {
        return new IllegalStateException("CMS Portal " + method + " " + path + " HTTP "
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
