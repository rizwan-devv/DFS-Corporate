package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.CmsCardService;
import com.dfs.corporate.web.dto.CmsCardInquiryRequest;
import com.dfs.corporate.web.dto.CmsCardSearchRequest;
import com.dfs.corporate.web.dto.CmsCardStatusUpdateRequest;
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
    public JsonNode search(@RequestBody(required = false) CmsCardSearchRequest req) {
        return cmsCardService.search(req != null ? req : new CmsCardSearchRequest());
    }

    @GetMapping
    public JsonNode list() {
        return cmsCardService.list();
    }

    @GetMapping("/dropdowns")
    public JsonNode dropdowns() {
        return cmsCardService.dropdowns();
    }

    @GetMapping("/{cardId}")
    public JsonNode get(@PathVariable String cardId) {
        return cmsCardService.get(cardId);
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

    @GetMapping("/lov/status")
    public JsonNode statusLov() {
        return cmsCardService.appStatusLov();
    }
}
