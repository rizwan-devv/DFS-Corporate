package com.dfs.corporate.service;

import com.dfs.corporate.domain.Party;
import com.dfs.corporate.domain.PartyType;
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
    private final PartyCmsIdentitySync partyCmsIdentitySync;
    private final ObjectMapper objectMapper;

    public CmsCardService(CmsPortalClient portalClient,
                          CmsAppClient appClient,
                          PartyRepository partyRepository,
                          PartyCmsIdentitySync partyCmsIdentitySync,
                          ObjectMapper objectMapper) {
        this.portalClient = portalClient;
        this.appClient = appClient;
        this.partyRepository = partyRepository;
        this.partyCmsIdentitySync = partyCmsIdentitySync;
        this.objectMapper = objectMapper;
    }

    public ObjectNode status() {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("enabled", portalClient.isEnabled() || appClient.isEnabled());
        n.put("portalReady", portalClient.isEnabled());
        n.put("appReady", appClient.isEnabled());
        n.put("appConfigured", appClient.isConfigured());
        n.put("mode", "agentapp-inquiry-preferred");
        return n;
    }

    /**
     * Load cards like AgentApp: CMS App /card/inquiry for this party's own KYC CNIC / relationship only.
     * Falls back to CMS Portal search if App inquiry yields nothing.
     */
    public JsonNode searchForPrincipal(AccountPrincipal principal, CmsCardSearchRequest req) {
        ensureCms();
        Scope scope = resolveScope(principal);
        List<JsonNode> matched = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        ArrayNode inquiryRaw = objectMapper.createArrayNode();
        String mode = "none";

        if (scope.keys().isEmpty()) {
            ObjectNode empty = baseWrap("search");
            empty.put("scoped", true);
            empty.put("mode", "none");
            empty.put("message",
                    "No CNIC / CMS relationship on this party yet. Complete KYC so your CNIC can be used "
                            + "for AgentApp-style card inquiry. Cards appear after CMS activates a card for that CNIC.");
            empty.set("scopeKeys", objectMapper.createArrayNode());
            empty.set("items", objectMapper.createArrayNode());
            empty.put("cmsRelationshipNum", "");
            return empty;
        }

        // 1) AgentApp-style CMS App inquiry
        if (appClient.isConfigured()) {
            mode = "cms-app-inquiry";
            for (String rel : relationshipCandidates(scope.keys())) {
                try {
                    JsonNode raw = appClient.inquire(rel, null);
                    inquiryRaw.add(raw);
                    List<JsonNode> found = extractItems(raw);
                    if (found.isEmpty()) {
                        JsonNode single = unwrapData(raw);
                        if (single != null && single.isObject() && looksLikeCard(single)) {
                            found = List.of(single);
                        }
                    }
                    for (JsonNode item : found) {
                        ObjectNode n = normalizeCard(item);
                        if (text(n, "relationshipNum").isBlank()) n.put("relationshipNum", rel);
                        if (text(n, "accountNumber").isBlank()) n.put("accountNumber", rel);
                        String id = cardIdentity(n) + "|" + rel;
                        if (seen.add(id)) matched.add(n);
                    }
                } catch (Exception ex) {
                    log.warn("CMS App inquiry relationshipNum={} failed: {}", rel, ex.getMessage());
                }
            }
        }

        // 2) Fallback: CMS Portal search — only for this party's keys (never unscoped all-cards)
        JsonNode lastPortal = objectMapper.createObjectNode();
        if (matched.isEmpty() && portalClient.isEnabled()) {
            mode = mode.equals("cms-app-inquiry") ? "cms-app-empty-portal-fallback" : "cms-portal-search";
            for (String key : scope.keys()) {
                try {
                    lastPortal = portalClient.searchCards(searchBody(req, key));
                    for (JsonNode item : extractItems(lastPortal)) {
                        ObjectNode n = normalizeCard(item);
                        if (!matchesScope(n, scope.keys())) continue;
                        String id = cardIdentity(n);
                        if (seen.add(id)) matched.add(n);
                    }
                } catch (Exception ex) {
                    log.warn("CMS Portal search key={} failed: {}", key, ex.getMessage());
                }
            }
        }

        ObjectNode out = finishSearch(
                inquiryRaw.size() > 0 ? inquiryRaw : lastPortal,
                matched,
                scope,
                true);
        out.put("mode", mode);
        if (matched.isEmpty()) {
            if (!appClient.isConfigured()) {
                out.put("message",
                        "No cards found. Set DFS_CMS_APP_API_KEY / USERNAME / PASSWORD "
                                + "(same as AgentApp) so portal can call /card/inquiry by relationshipNum.");
            } else {
                out.put("message",
                        "No card for this account yet (keys "
                                + scope.keys()
                                + "). When AgentApp orders a card and CMS activates it for your CNIC, it appears here.");
            }
        }
        String linked = scope.keys().stream()
                .findFirst()
                .orElse("");
        if (principal != null && principal.getPartyId() != null) {
            partyRepository.findById(principal.getPartyId()).ifPresent(p -> {
                if (p.getCmsRelationshipNum() != null && !p.getCmsRelationshipNum().isBlank()) {
                    out.put("cmsRelationshipNum", p.getCmsRelationshipNum().trim());
                } else if (p.getCnicNumber() != null && !p.getCnicNumber().isBlank()) {
                    out.put("cmsRelationshipNum", IdentityFormats.cnicDigits(p.getCnicNumber()));
                }
            });
        }
        if (!out.has("cmsRelationshipNum")) {
            out.put("cmsRelationshipNum", linked);
        }
        return out;
    }

    public JsonNode getForPrincipal(AccountPrincipal principal, String cardId) {
        ensureCms();
        Scope scope = resolveScope(principal);
        JsonNode raw = null;
        ObjectNode normalized = null;

        CmsCardSearchRequest listReq = new CmsCardSearchRequest();
        listReq.setSize(50);
        JsonNode search = searchForPrincipal(principal, listReq);
        JsonNode items = search.get("items");
        if (items != null && items.isArray()) {
            for (JsonNode s : items) {
                if (cardId.equals(text(s, "cardId")) || cardId.equals(text(s, "relationshipNum"))
                        || cardId.equals(text(s, "accountNumber")) || cardId.equals(text(s, "maskedPan"))) {
                    normalized = s.isObject() ? (ObjectNode) s : normalizeCard(s);
                    raw = search.get("cms");
                    break;
                }
            }
        }

        if (normalized == null && portalClient.isEnabled()) {
            raw = portalClient.getCard(cardId);
            normalized = normalizeCard(firstCardNode(raw));
        }

        if (normalized == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Card not found for this account");
        }
        if (scope.keys().isEmpty() || !matchesScope(normalized, scope.keys())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Card does not belong to this corporate account");
        }
        ObjectNode out = baseWrap("detail");
        out.set("item", normalized);
        out.set("cms", raw != null ? raw : objectMapper.createObjectNode());
        ArrayNode siblings = objectMapper.createArrayNode();
        String account = text(normalized, "accountNumber");
        if (!account.isBlank() && items != null && items.isArray()) {
            for (JsonNode s : items) {
                if (!text(s, "cardId").equals(text(normalized, "cardId"))
                        && account.equals(text(s, "accountNumber"))) {
                    siblings.add(s);
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

    public JsonNode inquire(AccountPrincipal principal, CmsCardInquiryRequest req, String requester) {
        ensureApp();
        String rel = req.getRelationshipNum() != null ? req.getRelationshipNum().trim() : "";
        if (rel.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "relationshipNum is required");
        }
        Scope scope = resolveScope(principal);
        if (scope.keys().isEmpty() || !ownsRelationship(rel, scope.keys())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Card relationship does not belong to this corporate account");
        }
        boolean requestedUnmask = req.getPin() != null && !req.getPin().isBlank();
        log.info("CMS card inquiry relationshipNum={} unmask={} requester={}",
                rel, requestedUnmask, requester);
        JsonNode raw = appClient.inquire(rel, requestedUnmask ? req.getPin() : null);
        JsonNode body = unwrapData(raw);
        ObjectNode item = normalizeCard(body != null ? body : raw);
        if (text(item, "relationshipNum").isBlank()) {
            item.put("relationshipNum", rel);
        }
        if (text(item, "accountNumber").isBlank()) {
            item.put("accountNumber", rel);
        }

        String pan = firstNonBlank(text(body, "pan"), text(body, "PAN"), text(item, "maskedPan"));
        String cvv = firstNonBlank(text(body, "cvv"), text(body, "cvv2"), text(body, "CVV"));
        boolean panClear = pan != null && pan.matches("\\d{12,19}");
        boolean cvvClear = cvv != null && cvv.matches("\\d{3,4}");
        boolean unmasked = requestedUnmask && (panClear || cvvClear);

        if (pan != null && !pan.isBlank()) item.put("pan", pan);
        if (cvvClear) item.put("cvv", cvv);
        else item.putNull("cvv");

        ObjectNode out = baseWrap("inquiry");
        out.put("unmaskRequested", requestedUnmask);
        out.put("unmasked", unmasked);
        out.put("message", unmasked
                ? "Card details revealed for this session. Refresh to re-mask."
                : requestedUnmask
                    ? "CMS returned masked data — check PIN or card status (e.g. COLD)."
                    : "Masked inquiry OK.");
        out.set("item", item);
        out.set("cms", raw != null ? raw : objectMapper.createObjectNode());
        return out;
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

    private Scope resolveScope(AccountPrincipal principal) {
        Set<String> keys = new LinkedHashSet<>();
        if (principal == null || principal.getPartyId() == null) {
            return new Scope(keys);
        }

        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        party = partyCmsIdentitySync.ensureFromPartnerUsers(party);
        addPartyKeys(keys, party);

        if (party.getPartyType() == PartyType.MERCHANT) {
            for (Party child : partyRepository.findByParentPartyIdOrderByCreatedAtDesc(party.getId())) {
                addPartyKeys(keys, partyCmsIdentitySync.ensureFromPartnerUsers(child));
            }
        }
        return new Scope(keys);
    }

    private void addPartyKeys(Set<String> keys, Party party) {
        // Auto only: KYC-derived CMS relationship / CNIC (CMS Relationship # = CNIC in this env)
        if (party.getCmsRelationshipNum() != null && !party.getCmsRelationshipNum().isBlank()) {
            keys.add(party.getCmsRelationshipNum().trim());
        }
        String cnic = IdentityFormats.cnicDigits(party.getCnicNumber());
        if (cnic != null && cnic.length() >= 12) {
            keys.add(cnic);
        }
        if (party.getDfsAccountId() != null && !party.getDfsAccountId().isBlank()) {
            String dfs = party.getDfsAccountId().trim();
            if (looksLikeCmsRelationship(dfs)) {
                keys.add(dfs);
            }
        }
    }

    private static boolean ownsRelationship(String rel, Set<String> keys) {
        if (rel == null || keys == null || keys.isEmpty()) return false;
        String r = rel.trim();
        for (String k : keys) {
            if (k != null && (k.equals(r) || r.endsWith(k) || k.endsWith(r))) return true;
        }
        return false;
    }

    /** CMS Relationship keys are typically 12–14 digits (CNIC or DFS account), not short ids like 106. */
    private static boolean looksLikeCmsRelationship(String v) {
        if (v == null) return false;
        String t = v.trim();
        return t.matches("\\d{12,14}");
    }

    private ObjectNode normalizeCard(JsonNode raw) {
        JsonNode c = raw == null ? objectMapper.createObjectNode() : raw;
        // unwrap nested data/card
        if (c.has("data") && c.get("data").isObject()) c = c.get("data");
        if (c.has("card") && c.get("card").isObject()) c = c.get("card");

        String cardId = firstNonBlank(
                text(c, "cardId"), text(c, "id"), text(c, "card_id"), text(c, "CardId"));
        String pan = firstNonBlank(
                text(c, "maskedPan"), text(c, "pan"), text(c, "PAN"), text(c, "cardNumber"), text(c, "cardNo"),
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
                text(c, "Relationship"), text(c, "RelationshipNum"), text(c, "custAccount"), text(c, "customerAccount"));
        String relationship = firstNonBlank(
                text(c, "relationshipNum"), text(c, "relationshipNumber"), text(c, "Relationship"),
                text(c, "RelationshipNum"), account);
        String holder = firstNonBlank(
                text(c, "holderName"), text(c, "cardHolder"), text(c, "cardHolderName"),
                text(c, "customerName"), text(c, "embossedName"), text(c, "name"),
                text(c, "cardTitle"), text(c, "CardTitle"),
                text(c, "accountTitle"), text(c, "title"), text(c, "Title"));
        if (holder == null || holder.isBlank()) holder = "—";

        String product = firstNonBlank(
                text(c, "productName"), text(c, "cardProductName"), text(c, "product"),
                text(c, "cardType"), text(c, "cardTypeName"), text(c, "productCode"), text(c, "schemeProduct"),
                text(c, "Product"), text(c, "productDesc"));
        if (product == null || product.isBlank()) product = "Card";

        String network = firstNonBlank(
                text(c, "network"), text(c, "scheme"), text(c, "brand"), text(c, "cardBrand"), text(c, "paymentNetwork"));
        if (network == null || network.isBlank()) {
            String p = product.toUpperCase(Locale.ROOT);
            if (p.contains("MASTER")) network = "Mastercard";
            else if (p.contains("VISA")) network = "Visa";
            else network = "DFS Pay";
        }

        String statusRaw = firstNonBlank(
                text(c, "cardStatusName"), text(c, "statusName"), text(c, "statusLabel"),
                text(c, "cardStatusCode"), text(c, "statusCode"), text(c, "status"), text(c, "cardStatus"),
                text(c, "Status"), text(c, "CardStatus"));
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
        n.put("relationshipNum", relationship != null ? relationship : "");
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
        if (u.contains("COLD")) return "Cold";
        return r;
    }

    /** Prefer 13-digit CMS relationship numbers (AgentApp style); keep other keys as fallback. */
    private List<String> relationshipCandidates(Set<String> keys) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String k : keys) {
            if (k == null) continue;
            String t = k.trim();
            if (t.matches("\\d{12,14}")) ordered.add(t);
        }
        for (String k : keys) {
            if (k == null) continue;
            String t = k.trim();
            if (!t.isBlank()) ordered.add(t);
        }
        return new ArrayList<>(ordered);
    }

    private JsonNode unwrapData(JsonNode raw) {
        if (raw == null) return null;
        if (raw.has("responseBody") && !raw.get("responseBody").isNull()) return raw.get("responseBody");
        if (raw.has("data") && !raw.get("data").isNull()) return raw.get("data");
        if (raw.has("result") && !raw.get("result").isNull()) return raw.get("result");
        return raw;
    }

    private boolean looksLikeCard(JsonNode n) {
        if (n == null || !n.isObject()) return false;
        return n.has("cardId") || n.has("id") || n.has("pan") || n.has("PAN") || n.has("maskedPan")
                || n.has("cardNumber") || n.has("relationshipNum") || n.has("Title") || n.has("title")
                || n.has("accountNumber") || n.has("Status") || n.has("status");
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
        if (data.isObject() && looksLikeCard(data)) {
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

    private void ensureCms() {
        if (!portalClient.isEnabled() && !appClient.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CMS integration disabled. Set DFS_CMS_API_ENABLED=true and CMS App "
                            + "(DFS_CMS_APP_API_KEY / USERNAME / PASSWORD) and/or Portal credentials.");
        }
    }

    private void ensurePortal() {
        if (!portalClient.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CMS Portal disabled. Set DFS_CMS_API_ENABLED=true and portal credentials.");
        }
    }

    private void ensureApp() {
        if (!appClient.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CMS App inquiry not configured. Set DFS_CMS_API_ENABLED=true and "
                            + "DFS_CMS_APP_API_KEY / DFS_CMS_APP_USERNAME / DFS_CMS_APP_PASSWORD.");
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

    private record Scope(Set<String> keys) {}
}
