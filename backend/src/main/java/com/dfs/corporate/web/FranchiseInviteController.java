package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.FranchiseInviteService;
import com.dfs.corporate.web.dto.FranchiseChildSummary;
import com.dfs.corporate.web.dto.FranchiseInviteCreateRequest;
import com.dfs.corporate.web.dto.FranchiseInvitePublicResponse;
import com.dfs.corporate.web.dto.FranchiseInviteResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class FranchiseInviteController {

    private final FranchiseInviteService franchiseInviteService;

    public FranchiseInviteController(FranchiseInviteService franchiseInviteService) {
        this.franchiseInviteService = franchiseInviteService;
    }

    /** Master corporate: list franchise invites. */
    @GetMapping("/api/franchises/invites")
    public List<FranchiseInviteResponse> listInvites(@AuthenticationPrincipal AccountPrincipal principal) {
        return franchiseInviteService.listInvites(principal);
    }

    /** Master corporate: list onboarded child franchises. */
    @GetMapping("/api/franchises/children")
    public List<FranchiseChildSummary> listChildren(@AuthenticationPrincipal AccountPrincipal principal) {
        return franchiseInviteService.listChildren(principal);
    }

    @PostMapping("/api/franchises/invites")
    public FranchiseInviteResponse create(@AuthenticationPrincipal AccountPrincipal principal,
                                          @Valid @RequestBody FranchiseInviteCreateRequest req) {
        return franchiseInviteService.create(principal, req);
    }

    @PostMapping("/api/franchises/invites/{id}/resend")
    public FranchiseInviteResponse resend(@AuthenticationPrincipal AccountPrincipal principal,
                                          @PathVariable Long id) {
        return franchiseInviteService.resend(principal, id);
    }

    @PostMapping("/api/franchises/invites/{id}/cancel")
    public Map<String, Object> cancel(@AuthenticationPrincipal AccountPrincipal principal,
                                      @PathVariable Long id) {
        franchiseInviteService.cancel(principal, id);
        return Map.of("cancelled", true);
    }

    /** Public: open invite by token (parent already bound). */
    @GetMapping("/api/public/franchise-invite/{token}")
    public FranchiseInvitePublicResponse getPublic(@PathVariable String token) {
        return franchiseInviteService.getPublic(token);
    }
}
