package com.dfs.corporate.service;

import com.dfs.corporate.integration.cms.CmsAppClient;
import com.dfs.corporate.integration.cms.CmsPortalClient;
import com.dfs.corporate.web.dto.CmsCardInquiryRequest;
import com.dfs.corporate.web.dto.CmsCardSearchRequest;
import com.dfs.corporate.web.dto.CmsCardStatusUpdateRequest;
import com.dfs.corporate.web.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CmsCardService {

    private static final Logger log = LoggerFactory.getLogger(CmsCardService.class);

    private final CmsPortalClient portalClient;
    private final CmsAppClient appClient;
    private final ObjectMapper objectMapper;

    public CmsCardService(CmsPortalClient portalClient, CmsAppClient appClient, ObjectMapper objectMapper) {
        this.portalClient = portalClient;
        this.appClient = appClient;
        this.objectMapper = objectMapper;
    }

    public ObjectNode status() {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("enabled", portalClient.isEnabled());
        n.put("portalReady", portalClient.isEnabled());
        n.put("appReady", appClient.isEnabled());
        return n;
    }

    public JsonNode search(CmsCardSearchRequest req) {
        ensurePortal();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", req.getPage() != null ? req.getPage() : 0);
        body.put("size", req.getSize() != null ? req.getSize() : 20);
        if (req.getSort() != null && !req.getSort().isBlank()) body.put("sort", req.getSort());
        if (req.getSortDir() != null && !req.getSortDir().isBlank()) body.put("sortDir", req.getSortDir());
        if (req.getCardStatusCode() != null && !req.getCardStatusCode().isBlank()) {
            body.put("cardStatusCode", req.getCardStatusCode());
        }
        if (req.getAccountNumber() != null && !req.getAccountNumber().isBlank()) {
            body.put("accountNumber", req.getAccountNumber());
        }
        if (req.getRelationshipNum() != null && !req.getRelationshipNum().isBlank()) {
            body.put("relationshipNum", req.getRelationshipNum());
        }
        return wrap("search", portalClient.searchCards(body));
    }

    public JsonNode list() {
        ensurePortal();
        return wrap("list", portalClient.listCards());
    }

    public JsonNode get(String cardId) {
        ensurePortal();
        JsonNode raw = portalClient.getCard(cardId);
        ObjectNode out = wrap("detail", raw);
        attachSameAccountSiblings(out, raw);
        return out;
    }

    public JsonNode dropdowns() {
        ensurePortal();
        return wrap("dropdowns", portalClient.dropdowns());
    }

    public JsonNode updateStatus(String cardId, CmsCardStatusUpdateRequest req) {
        ensurePortal();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("cardStatusCode", req.getCardStatusCode());
        log.info("CMS card status update cardId={} status={}", cardId, req.getCardStatusCode());
        return wrap("updateStatus", portalClient.updateCard(cardId, body));
    }

    public JsonNode inquire(CmsCardInquiryRequest req, String requester) {
        ensureApp();
        boolean unmask = req.getPin() != null && !req.getPin().isBlank();
        log.info("CMS card inquiry relationshipNum={} unmask={} requester={}",
                req.getRelationshipNum(), unmask, requester);
        return wrap("inquiry", appClient.inquire(req.getRelationshipNum(), unmask ? req.getPin() : null));
    }

    public JsonNode appStatusLov() {
        ensureApp();
        return wrap("statusLov", appClient.statusLov());
    }

    /**
     * Best-effort: find other cards sharing the same account / relationship number.
     */
    private void attachSameAccountSiblings(ObjectNode out, JsonNode cardRaw) {
        try {
            JsonNode card = unwrapData(cardRaw);
            String account = firstNonBlank(
                    text(card, "accountNumber"),
                    text(card, "accountNo"),
                    text(card, "relationshipNum"),
                    text(card, "relationshipNumber")
            );
            String cardId = firstNonBlank(text(card, "cardId"), text(card, "id"));
            if (account.isBlank()) return;

            ObjectNode searchBody = objectMapper.createObjectNode();
            searchBody.put("page", 0);
            searchBody.put("size", 50);
            searchBody.put("accountNumber", account);
            JsonNode searchRaw = portalClient.searchCards(searchBody);
            List<JsonNode> siblings = new ArrayList<>();
            for (JsonNode item : extractItems(searchRaw)) {
                String id = firstNonBlank(text(item, "cardId"), text(item, "id"));
                String acc = firstNonBlank(
                        text(item, "accountNumber"),
                        text(item, "accountNo"),
                        text(item, "relationshipNum")
                );
                if (!acc.isBlank() && acc.equals(account) && !id.equals(cardId)) {
                    siblings.add(item);
                }
            }
            ArrayNode arr = objectMapper.createArrayNode();
            siblings.forEach(arr::add);
            out.set("sameAccountCards", arr);
            out.put("sameAccountDifferentCard", !siblings.isEmpty());
        } catch (Exception ex) {
            log.debug("Could not attach same-account siblings: {}", ex.getMessage());
            out.put("sameAccountDifferentCard", false);
            out.set("sameAccountCards", objectMapper.createArrayNode());
        }
    }

    private ObjectNode wrap(String operation, JsonNode cms) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("source", "cms");
        out.put("operation", operation);
        out.set("cms", cms != null ? cms : objectMapper.createObjectNode());
        return out;
    }

    private JsonNode unwrapData(JsonNode raw) {
        if (raw == null) return objectMapper.createObjectNode();
        if (raw.has("data") && !raw.get("data").isNull()) return raw.get("data");
        if (raw.has("responseBody") && !raw.get("responseBody").isNull()) return raw.get("responseBody");
        return raw;
    }

    private List<JsonNode> extractItems(JsonNode raw) {
        List<JsonNode> items = new ArrayList<>();
        JsonNode data = unwrapData(raw);
        if (data.isArray()) {
            data.forEach(items::add);
            return items;
        }
        for (String key : List.of("items", "content", "cards", "records", "list")) {
            JsonNode arr = data.get(key);
            if (arr != null && arr.isArray()) {
                arr.forEach(items::add);
                return items;
            }
        }
        if ((data.isObject() && data.has("cardId")) || (data.isObject() && data.has("id"))) {
            items.add(data);
        }
        return items;
    }

    private void ensurePortal() {
        if (!portalClient.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CMS integration disabled. Set DFS_CMS_API_ENABLED=true and portal credentials.");
        }
    }

    private void ensureApp() {
        if (!appClient.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CMS integration disabled. Set DFS_CMS_API_ENABLED=true and app credentials.");
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return "";
        return n.get(field).asText("");
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }
}
