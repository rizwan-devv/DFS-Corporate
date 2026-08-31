package com.dfs.corporate.web;

import com.dfs.corporate.domain.PartyType;
import com.dfs.corporate.service.AuthService;
import com.dfs.corporate.service.OnboardingService;
import com.dfs.corporate.web.dto.LoginRequest;
import com.dfs.corporate.web.dto.SignupRequest;
import com.dfs.corporate.web.dto.VerifyOtpRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final OnboardingService onboardingService;

    public AuthController(AuthService authService, OnboardingService onboardingService) {
        this.authService = authService;
        this.onboardingService = onboardingService;
    }

    @GetMapping("/party-types")
    public List<Map<String, String>> partyTypes() {
        // Corporate accounts only (SBP EMI KYC scope for DFS Corporate)
        return List.of(
                Map.of("code", PartyType.MERCHANT.name(), "label", "Corporate Merchant"),
                Map.of("code", PartyType.SUB_MERCHANT.name(), "label", "Corporate Sub-merchant")
        );
    }

    @GetMapping("/entity-types")
    public List<Map<String, String>> entityTypes() {
        return Arrays.stream(com.dfs.corporate.domain.CorporateEntityType.values())
                .map(t -> Map.of("code", t.name(), "label", switch (t) {
                    case SOLE_PROPRIETORSHIP -> "Sole Proprietorship";
                    case SMALL_BUSINESS -> "Small business / freelance profession";
                    case PARTNERSHIP -> "Partnership";
                    case LLP -> "Limited Liability Partnership (LLP)";
                }))
                .toList();
    }

    @GetMapping("/id-document-types")
    public List<Map<String, String>> idDocumentTypes() {
        return Arrays.stream(com.dfs.corporate.domain.IdDocumentType.values())
                .map(t -> Map.of("code", t.name(), "label", t.name().replace('_', ' ')))
                .toList();
    }

    @GetMapping("/party-types/{type}/documents")
    public List<Map<String, Object>> requiredDocs(@PathVariable PartyType type) {
        return onboardingService.requiredFor(type).stream()
                .map(r -> Map.<String, Object>of(
                        "documentCode", r.getDocumentCode(),
                        "documentLabel", r.getDocumentLabel(),
                        "mandatory", r.isMandatory()))
                .toList();
    }

    @PostMapping("/auth/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.ok(authService.signup(req));
    }

    @PostMapping("/auth/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return ResponseEntity.ok(authService.verifyOtp(req));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
