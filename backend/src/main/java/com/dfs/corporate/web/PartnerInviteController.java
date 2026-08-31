package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.PartnerInviteService;
import com.dfs.corporate.web.dto.PartnerInviteCreateRequest;
import com.dfs.corporate.web.dto.PartnerInviteResponse;
import com.dfs.corporate.web.error.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Legacy portal partner-invite endpoints kept for admin compatibility.
 * Public partner KYC on portal is disabled — partners use the mobile app.
 */
@RestController
public class PartnerInviteController {

    private final PartnerInviteService partnerInviteService;

    public PartnerInviteController(PartnerInviteService partnerInviteService) {
        this.partnerInviteService = partnerInviteService;
    }

    @GetMapping("/api/onboarding/partner-invites")
    public List<PartnerInviteResponse> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return partnerInviteService.list(principal);
    }

    @PostMapping("/api/onboarding/partner-invites")
    public PartnerInviteResponse create(@AuthenticationPrincipal AccountPrincipal principal,
                                        @Valid @RequestBody PartnerInviteCreateRequest req) {
        throw new ApiException(HttpStatus.GONE,
                "Portal partner KYC invites are disabled. Add partners to the roster and upload their CNIC/agreement; they receive a mobile app invite on submit.");
    }

    @DeleteMapping("/api/onboarding/partner-invites/{id}")
    public Map<String, String> cancel(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Long id) {
        partnerInviteService.cancel(principal, id);
        return Map.of("status", "CANCELLED");
    }

    @GetMapping("/api/public/partner-kyc/{token}")
    public Map<String, Object> get(@PathVariable String token) {
        return disabled();
    }

    @PutMapping("/api/public/partner-kyc/{token}")
    public Map<String, Object> save(@PathVariable String token) {
        return disabled();
    }

    @PostMapping("/api/public/partner-kyc/{token}/documents")
    public Map<String, Object> upload(@PathVariable String token) {
        return disabled();
    }

    @PostMapping("/api/public/partner-kyc/{token}/complete")
    public Map<String, Object> complete(@PathVariable String token) {
        return disabled();
    }

    private Map<String, Object> disabled() {
        throw new ApiException(HttpStatus.GONE,
                "Partner KYC on this portal is disabled. Use the DFS Corporate mobile app (link emailed after merchant submit).");
    }
}
