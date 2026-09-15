package com.dfs.corporate.service;

import com.dfs.corporate.domain.Party;
import com.dfs.corporate.domain.PartyType;
import com.dfs.corporate.domain.Role;
import com.dfs.corporate.integration.cms.CmsAppClient;
import com.dfs.corporate.integration.cms.CmsPortalClient;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.security.AccountPrincipal;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CmsCardService {

    private static final Logger log = LoggerFactory.getLogger(CmsCardService.class);

    private final CmsPortalClient portalClient;
    private final CmsAppClient appClient;
    private final PartyRepository partyRepository;
    private final ObjectMapper objectMapper;

    public CmsCardService(CmsPortalClient portalClient,
                          CmsAppClient appClient,
                          PartyRepository partyRepository,
                          ObjectMapper objectMapper) {
        this.portalClient = portalClient;
        this.appClient = appClient;
        this.partyRepository = partyRepository;
        this.objectMapper = objectMapper;
    }

    public ObjectNode status() {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("enabled", portalClient.isEnabled());
        n.put("portalReady", portalClient.isEnabled());
        n.put("appReady", appClient.isEnabled());
        return n;
    }

    /** Scoped search for the logged-in corporate party (own + children accounts). */
    public JsonNode searchForPrincipal(AccountPrincipal principal, CmsCardSearchRequest req) {
        ensurePortal();
        Scope scope = resolveScope(principal, req);
        List<JsonNode> matched = new ArrayList<>();

        if (scope.keys().isEmpty() && !scope.allowUnscopedAdmin()) {
            ObjectNode empty = baseWrap("search");
            empty.put("scoped", true);
            empty.put("message", "No DFS account id on this party yet — cannot match CMS cards. Complete account provisioning first.");
            empty.set("scopeKeys", objectMapper.createArrayNode());
            empty.set("items", objectMapper.createArrayNode());
            return empty;
        }

        if (scope.allowUnscopedAdmin() && scope.keys().isEmpty()) {
            ObjectNode body = searchBody(req, null);
            JsonNode raw = portalClient.searchCards(body);
            List<JsonNode> all = extractItems(raw);
            for (JsonNode item : all) matched.add(normalizeCard(item));
            return finishSearch(raw, matched, scope, false);
        }

        // Prefer filtered CMS queries; also merge by each scope key
        Set<String> seen = new LinkedHashSet<>();
        JsonNode lastRaw = objectMapper.createObjectNode();
        for (String key : scope.keys()) {
            try {
                ObjectNode byAccount = searchBody(req, key);
                lastRaw = portalClient.searchCards(byAccount);
                for (JsonNode item : extractItems(lastRaw)) {
                    String id = cardIdentity(item);
                    if (seen.add(id)) matched.add(normalizeCard(item));
                }
            } catch (Exception ex) {
                log.warn("CMS search by account/relationship {} failed: {}", key, ex.getMessage());
            }
        }

        // If CMS filter ignored our key and returned everything, filter locally
        if (matched.size() > 40 && !scope.keys().isEmpty()) {
            List<JsonNode> filtered = new ArrayList<>();
            for (JsonNode n : matched) {
                if (matchesScope(n, scope.keys())) filtered.add(n);
            }
            matched = filtered;
        } else if (!scope.keys().isEmpty()) {
            List<JsonNode> filtered = new ArrayList<>();
            for (JsonNode n : matched) {
                if (matchesScope(n, scope.keys())) filtered.add(n);
            }
            // Keep filtered if we got any; if CMS filter already worked, filtered ≈ matched
            if (!filtered.isEmpty() || matched.isEmpty()) {
                matched = filtered;
            }
        }

        return finishSearch(lastRaw, matched, scope, true);
    }

    public JsonNode getForPrincipal(AccountPrincipal principal, String cardId) {
        ensurePortal();
        Scope scope = resolveScope(principal, new CmsCardSearchRequest());
        JsonNode raw = portalClient.getCard(cardId);
        JsonNode card = firstCardNode(raw);
        ObjectNode normalized = normalizeCard(card);
        if (!scope.allowUnscopedAdmin() && !scope.keys().isEmpty() && !matchesScope(normalized, scope.keys())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Card does not belong to this corporate account");
        }
        ObjectNode out = baseWrap("detail");
        out.set("item", normalized);
        out.set("cms", raw != null ? raw : objectMapper.createObjectNode());
        ArrayNode siblings = objectMapper.createArrayNode();
        String account = text(normalized, "accountNumber");
        if (!account.isBlank()) {
            CmsCardSearchRequest req = new CmsCardSearchRequest();
            req.setAccountNumber(account);
            req.setSize(50);
            JsonNode search = searchForPrincipal(principal, req);
            JsonNode items = search.get("items");
            if (items != null && items.isArray()) {
                for (JsonNode s : items) {
                    if (!text(s, "cardId").equals(text(normalized, "cardId"))) siblings.add(s);
                }
            }
        }
        out.set("sameAccountCards", siblings);
        out.put("sameAccountDifferentCard", siblings.size() > 0);
        return out;
    }

    public JsonNode dropdowns() {
        ensurePortal();
        return wrapRaw("dropdowns", portalClient.dropdowns());
    }

    public JsonNode updateStatus(String cardId, CmsCardStatusUpdateRequest req) {
        ensurePortal();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("cardStatusCode", req.getCardStatusCode());
        log.info("CMS card status update cardId={} status={}", cardId, req.getCardStatusCode());
        return wrapRaw("updateStatus", portalClient.updateCard(cardId, body));
    }

    public JsonNode inquire(CmsCardInquiryRequest req, String requester) {
        ensureApp();
        boolean unmask = req.getPin() != null && !req.getPin().isBlank();
        log.info("CMS card inquiry relationshipNum={} unmask={} requester={}",
                req.getRelationshipNum(), unmask, requester);
        return wrapRaw("inquiry", appClient.inquire(req.getRelationshipNum(), unmask ? req.getPin() : null));
    }

    public JsonNode appStatusLov() {
        ensureApp();
        return wrapRaw("statusLov", appClient.statusLov());
    }

    private ObjectNode finishSearch(JsonNode raw, List<JsonNode> matched, Scope scope, boolean scoped) {
        ObjectNode out = baseWrap("search");
        out.put("scoped", scoped);
        ArrayNode keys = objectMapper.createArrayNode();
        scope.keys().forEach(keys::add);
        out.set("scopeKeys", keys);
        ArrayNode items = objectMapper.createArrayNode();
        matched.forEach(items::add);
        out.set("items", items);
        out.put("total", matched.size());
        out.set("cms", raw != null ? raw : objectMapper.createObjectNode());
        return out;
    }

    private ObjectNode searchBody(CmsCardSearchRequest req, String scopeKey) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", req.getPage() != null ? req.getPage() : 0);
        body.put("size", req.getSize() != null ? req.getSize() : 50);
        if (req.getSort() != null && !req.getSort().isBlank()) body.put("sort", req.getSort());
        if (req.getSortDir() != null && !req.getSortDir().isBlank()) body.put("sortDir", req.getSortDir());
        if (req.getCardStatusCode() != null && !req.getCardStatusCode().isBlank()) {
            body.put("cardStatusCode", req.getCardStatusCode());
        }
        String account = firstNonBlank(req.getAccountNumber(), scopeKey);
        String rel = firstNonBlank(req.getRelationshipNum(), scopeKey);
        if (account != null) {
            body.put("accountNumber", account);
            body.put("relationshipNum", account);
        }
        if (rel != null && account == null) {
            body.put("relationshipNum", rel);
        }
        return body;
    }

    private Scope resolveScope(AccountPrincipal principal, CmsCardSearchRequest req) {
        Set<String> keys = new LinkedHashSet<>();
        if (req.getAccountNumber() != null && !req.getAccountNumber().isBlank()) {
            keys.add(req.getAccountNumber().trim());
        }
        if (req.getRelationshipNum() != null && !req.getRelationshipNum().isBlank()) {
            keys.add(req.getRelationshipNum().trim());
        }

        boolean admin = principal != null && principal.getRole() == Role.PLATFORM_ADMIN;
        if (principal == null || principal.getPartyId() == null) {
            return new Scope(keys, admin);
        }

        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        addPartyKeys(keys, party);

        if (party.getPartyType() == PartyType.MERCHANT) {
            for (Party child : partyRepository.findByParentPartyIdOrderByCreatedAtDesc(party.getId())) {
                addPartyKeys(keys, child);
            }
        }
        return new Scope(keys, admin);
    }

    private void addPartyKeys(Set<String> keys, Party party) {
        if (party.getDfsAccountId() != null && !party.getDfsAccountId().isBlank()) {
            keys.add(party.getDfsAccountId().trim());
        }
        if (party.getTrackingId() != null && !party.getTrackingId().isBlank()) {
            keys.add(party.getTrackingId().trim());
        }
        String phone = IdentityFormats.phoneDigits(party.getPhone());
        if (phone != null && phone.length() >= 10) {
            keys.add(phone);
        }
    }

    private ObjectNode normalizeCard(JsonNode raw) {
        JsonNode c = raw == null ? objectMapper.createObjectNode() : raw;
        // unwrap nested data/card
        if (c.has("data") && c.get("data").isObject()) c = c.get("data");
        if (c.has("card") && c.get("card").isObject()) c = c.get("card");

        String cardId = firstNonBlank(
                text(c, "cardId"), text(c, "id"), text(c, "card_id"), text(c, "CardId"));
        String pan = firstNonBlank(
                text(c, "maskedPan"), text(c, "pan"), text(c, "cardNumber"), text(c, "cardNo"),
                text(c, "cardPan"), text(c, "maskedCardNumber"), text(c, "card_number"), text(c, "CardNumber"));
        String last4 = firstNonBlank(text(c, "last4"), text(c, "lastFour"), text(c, "last_four"));
        if ((last4 == null || last4.isBlank()) && pan != null) {
            String digits = pan.replaceAll("\\D", "");
            if (digits.length() >= 4) last4 = digits.substring(digits.length() - 4);
        }
        if (last4 == null || last4.isBlank()) last4 = "••••";

        String account = firstNonBlank(
                text(c, "accountNumber"), text(c, "accountNo"), text(c, "account_number"),
                text(c, "relationshipNum"), text(c, "relationshipNumber"), text(c, "relationship_num"),
                text(c, "custAccount"), text(c, "customerAccount"));
        String holder = firstNonBlank(
                text(c, "holderName"), text(c, "cardHolder"), text(c, "cardHolderName"),
                text(c, "customerName"), text(c, "embossedName"), text(c, "name"),
                text(c, "accountTitle"), text(c, "title"));
        if (holder == null || holder.isBlank()) holder = "—";

        String product = firstNonBlank(
                text(c, "productName"), text(c, "cardProductName"), text(c, "product"),
                text(c, "cardType"), text(c, "cardTypeName"), text(c, "productCode"), text(c, "schemeProduct"));
        if (product == null || product.isBlank()) product = "Card";

        String network = firstNonBlank(
                text(c, "network"), text(c, "scheme"), text(c, "brand"), text(c, "cardBrand"), text(c, "paymentNetwork"));
        if (network == null || network.isBlank()) network = "DFS Pay";

        String statusRaw = firstNonBlank(
                text(c, "cardStatusName"), text(c, "statusName"), text(c, "statusLabel"),
                text(c, "cardStatusCode"), text(c, "statusCode"), text(c, "status"), text(c, "cardStatus"));
        String status = mapStatus(statusRaw);

        String expiry = formatExpiry(firstNonBlank(
                text(c, "expiry"), text(c, "expiryDate"), text(c, "cardExpiry"), text(c, "expireDate"),
                text(c, "expiryDateTime"), text(c, "validThru")));
        String expM = firstNonBlank(text(c, "expiryMonth"), text(c, "expMonth"));
        String expY = firstNonBlank(text(c, "expiryYear"), text(c, "expYear"));
        if ((expiry == null || expiry.equals("—")) && expM != null && expY != null) {
            expiry = pad2(expM) + "/" + (expY.length() > 2 ? expY.substring(expY.length() - 2) : expY);
        }

        ObjectNode n = objectMapper.createObjectNode();
        n.put("cardId", cardId != null ? cardId : "");
        n.put("maskedPan", pan != null ? pan : ("************" + (last4.equals("••••") ? "" : last4)));
        n.put("last4", last4);
        n.put("accountNumber", account != null ? account : "");
        n.put("relationshipNum", account != null ? account : "");
        n.put("holderName", holder);
        n.put("productName", product);
        n.put("network", network);
        n.put("status", status);
        n.put("cardStatusCode", statusRaw != null ? statusRaw : "");
        n.put("expiry", expiry != null ? expiry : "—");
        return n;
    }

    private static String mapStatus(String raw) {
        if (raw == null || raw.isBlank()) return "UNKNOWN";
        String r = raw.trim();
        String u = r.toUpperCase(Locale.ROOT);
        if (u.contains("ACTIVE") || r.equals("001") || r.equals("1")) return "Active";
        if (u.contains("INACTIVE") || r.equals("002") || r.equals("2")) return "Inactive";
        if (u.contains("BLOCK") || u.contains("HOT") || r.equals("003") || r.equals("3")) return "Blocked";
        if (u.contains("PEND") || r.equals("004") || r.equals("4")) return "Pending";
        return r;
    }

    private static String formatExpiry(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        String s = raw.trim();
        try {
            if (s.contains("T")) {
                OffsetDateTime odt = OffsetDateTime.parse(s);
                return String.format("%02d/%02d", odt.getMonthValue(), odt.getYear() % 100);
            }
            if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                LocalDate d = LocalDate.parse(s.substring(0, 10));
                return String.format("%02d/%02d", d.getMonthValue(), d.getYear() % 100);
            }
            if (s.matches("\\d{2}/\\d{2}")) return s;
            if (s.matches("\\d{2}/\\d{4}")) {
                return s.substring(0, 2) + "/" + s.substring(5);
            }
        } catch (Exception ignored) {
            /* keep raw shortened */
        }
        if (s.length() > 12) return s.substring(0, 10);
        return s;
    }

    private static String pad2(String m) {
        String d = m.replaceAll("\\D", "");
        if (d.length() == 1) return "0" + d;
        return d.length() >= 2 ? d.substring(0, 2) : d;
    }

    private boolean matchesScope(JsonNode card, Set<String> keys) {
        if (keys == null || keys.isEmpty()) return true;
        String account = firstNonBlank(
                text(card, "accountNumber"), text(card, "relationshipNum"),
                text(card, "accountNo"), text(card, "relationshipNumber"));
        if (account == null || account.isBlank()) return false;
        String a = account.trim();
        for (String k : keys) {
            if (k.equals(a) || a.endsWith(k) || k.endsWith(a)) return true;
        }
        return false;
    }

    private String cardIdentity(JsonNode item) {
        String id = firstNonBlank(text(item, "cardId"), text(item, "id"));
        if (id != null && !id.isBlank()) return id;
        return item != null ? item.toString() : String.valueOf(System.identityHashCode(item));
    }

    private JsonNode firstCardNode(JsonNode raw) {
        List<JsonNode> items = extractItems(raw);
        if (!items.isEmpty()) return items.get(0);
        if (raw != null && raw.has("data")) return raw.get("data");
        return raw != null ? raw : objectMapper.createObjectNode();
    }

    private List<JsonNode> extractItems(JsonNode raw) {
        List<JsonNode> items = new ArrayList<>();
        if (raw == null) return items;
        JsonNode data = raw;
        if (raw.has("data") && !raw.get("data").isNull()) data = raw.get("data");
        else if (raw.has("responseBody") && !raw.get("responseBody").isNull()) data = raw.get("responseBody");
        else if (raw.has("result") && !raw.get("result").isNull()) data = raw.get("result");

        if (data.isArray()) {
            data.forEach(items::add);
            return items;
        }
        for (String key : List.of("items", "content", "cards", "records", "list", "cardList")) {
            JsonNode arr = data.get(key);
            if (arr != null && arr.isArray()) {
                arr.forEach(items::add);
                return items;
            }
        }
        if (data.isObject() && (data.has("cardId") || data.has("id") || data.has("accountNumber"))) {
            items.add(data);
        }
        return items;
    }

    private ObjectNode baseWrap(String operation) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("source", "cms");
        out.put("operation", operation);
        return out;
    }

    private ObjectNode wrapRaw(String operation, JsonNode cms) {
        ObjectNode out = baseWrap(operation);
        out.set("cms", cms != null ? cms : objectMapper.createObjectNode());
        return out;
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
                    "CMS App inquiry disabled. Set DFS_CMS_API_ENABLED=true and DFS_CMS_APP_* credentials.");
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
        return null;
    }

    private record Scope(Set<String> keys, boolean allowUnscopedAdmin) {}
}
