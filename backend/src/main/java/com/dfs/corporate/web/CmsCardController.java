package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.CmsCardService;
import com.dfs.corporate.web.dto.CmsCardInquiryRequest;
import com.dfs.corporate.web.dto.CmsCardSearchRequest;
import com.dfs.corporate.web.dto.CmsCardStatusUpdateRequest;
import com.dfs.corporate.web.dto.CmsRelationshipLinkRequest;
import com.dfs.corporate.web.dto.PartyResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cms/cards")
public class CmsCardController {

    private final CmsCardService cmsCardService;

    public CmsCardController(CmsCardService cmsCardService) {
        this.cmsCardService = cmsCardService;
    }

    @GetMapping("/status")
    public JsonNode status() {
        return cmsCardService.status();
    }

    @PostMapping("/search")
    public JsonNode search(@AuthenticationPrincipal AccountPrincipal principal,
                           @RequestBody(required = false) CmsCardSearchRequest req) {
        return cmsCardService.searchForPrincipal(principal, req != null ? req : new CmsCardSearchRequest());
    }

    /** Same as search — always scoped to the logged-in party. */
    @GetMapping
    public JsonNode list(@AuthenticationPrincipal AccountPrincipal principal) {
        return cmsCardService.searchForPrincipal(principal, new CmsCardSearchRequest());
    }

    @GetMapping("/dropdowns")
    public JsonNode dropdowns() {
        return cmsCardService.dropdowns();
    }

    @GetMapping("/{cardId}")
    public JsonNode get(@AuthenticationPrincipal AccountPrincipal principal,
                        @PathVariable String cardId) {
        return cmsCardService.getForPrincipal(principal, cardId);
    }

    @PutMapping("/{cardId}/status")
    public JsonNode updateStatus(@PathVariable String cardId,
                                 @Valid @RequestBody CmsCardStatusUpdateRequest req) {
        return cmsCardService.updateStatus(cardId, req);
    }

    /** Masked by default; include pin to unmask (audited). */
    @PostMapping("/inquiry")
    public JsonNode inquiry(@AuthenticationPrincipal AccountPrincipal principal,
                            @Valid @RequestBody CmsCardInquiryRequest req) {
        String who = principal != null ? String.valueOf(principal.getUsername()) : "unknown";
        return cmsCardService.inquire(req, who);
    }

    /**
     * Link CMS Relationship # for this merchant (AgentApp /card/inquiry key).
     * Does not change dfs_account_id or transfer rails.
     */
    @PutMapping("/relationship")
    public PartyResponse linkRelationship(@AuthenticationPrincipal AccountPrincipal principal,
                                          @Valid @RequestBody CmsRelationshipLinkRequest req) {
        return cmsCardService.linkRelationshipForPrincipal(principal, req.getRelationshipNum());
    }

    @GetMapping("/lov/status")
    public JsonNode statusLov() {
        return cmsCardService.appStatusLov();
    }
}
