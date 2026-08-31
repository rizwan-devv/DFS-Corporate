package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.OnboardingService;
import com.dfs.corporate.web.dto.AssociatedPersonRequest;
import com.dfs.corporate.web.dto.PartyResponse;
import com.dfs.corporate.web.dto.ProfileUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/me")
    public PartyResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return onboardingService.me(principal);
    }

    @PutMapping("/profile")
    public PartyResponse profile(@AuthenticationPrincipal AccountPrincipal principal,
                                 @Valid @RequestBody ProfileUpdateRequest req,
                                 HttpServletRequest http) {
        return onboardingService.updateProfile(principal, req, clientIp(http), http.getHeader("User-Agent"));
    }

    @PostMapping("/associated-persons")
    public PartyResponse addPerson(@AuthenticationPrincipal AccountPrincipal principal,
                                   @Valid @RequestBody AssociatedPersonRequest req) {
        return onboardingService.addAssociatedPerson(principal, req);
    }

    @PutMapping("/associated-persons/{id}")
    public PartyResponse updatePerson(@AuthenticationPrincipal AccountPrincipal principal,
                                      @PathVariable Long id,
                                      @Valid @RequestBody AssociatedPersonRequest req) {
        return onboardingService.updateAssociatedPerson(principal, id, req);
    }

    @DeleteMapping("/associated-persons/{id}")
    public PartyResponse deletePerson(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable Long id) {
        return onboardingService.removeAssociatedPerson(principal, id);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PartyResponse upload(@AuthenticationPrincipal AccountPrincipal principal,
                                @RequestParam String documentCode,
                                @RequestParam("file") MultipartFile file) {
        return onboardingService.uploadDocument(principal, documentCode, file);
    }

    @PostMapping("/submit")
    public PartyResponse submit(@AuthenticationPrincipal AccountPrincipal principal) {
        return onboardingService.submit(principal);
    }

    @GetMapping("/status")
    public ResponseEntity<PartyResponse> status(@AuthenticationPrincipal AccountPrincipal principal) {
        return ResponseEntity.ok(onboardingService.me(principal));
    }

    private String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}
