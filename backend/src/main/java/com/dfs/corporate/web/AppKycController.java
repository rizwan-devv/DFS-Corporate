package com.dfs.corporate.web;

import com.dfs.corporate.service.AppKycService;
import com.dfs.corporate.web.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/public/app-kyc")
public class AppKycController {

    private final AppKycService appKycService;

    public AppKycController(AppKycService appKycService) {
        this.appKycService = appKycService;
    }

    @GetMapping("/invite/{token}")
    public AppKycSessionResponse openByToken(@PathVariable String token) {
        return appKycService.openByToken(token);
    }

    @PostMapping("/login")
    public AppKycSessionResponse login(@RequestBody AppKycLoginRequest req) {
        return appKycService.login(req);
    }

    @GetMapping("/me")
    public AppKycSessionResponse me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return appKycService.me(bearer(authorization));
    }

    /** Force password change after first login (temp PIN → password). */
    @PostMapping("/change-password")
    public AppKycSessionResponse changePassword(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                @Valid @RequestBody AppKycChangePasswordRequest req) {
        return appKycService.changePassword(bearer(authorization), req);
    }

    /** Send OTP to partner mobile (after password change). */
    @PostMapping("/otp/send")
    public Map<String, Object> sendOtp(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return appKycService.sendMobileOtp(bearer(authorization));
    }

    @PostMapping("/otp/verify")
    public AppKycSessionResponse verifyOtp(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                           @Valid @RequestBody AppKycOtpVerifyRequest req) {
        return appKycService.verifyMobileOtp(bearer(authorization), req);
    }

    /** Proxy DFS backend getAllSegments for KYC app dropdowns. */
    @GetMapping("/segments")
    public JsonNode segments() {
        return appKycService.segments();
    }

    @PutMapping("/profile")
    public AppKycSessionResponse profile(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                         @RequestBody AppKycProfileRequest req) {
        return appKycService.updateProfile(bearer(authorization), req);
    }

    @PostMapping("/documents")
    public AppKycSessionResponse upload(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                        @RequestParam String kind,
                                        @RequestParam("file") MultipartFile file) {
        return appKycService.uploadDocument(bearer(authorization), kind, file);
    }

    @PostMapping("/video-stub")
    public AppKycSessionResponse videoStub(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return appKycService.stubVideo(bearer(authorization));
    }

    @PostMapping("/biometric-stub")
    public AppKycSessionResponse biometricStub(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return appKycService.stubBiometric(bearer(authorization));
    }

    @PostMapping("/submit")
    public AppKycSessionResponse submit(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return appKycService.submit(bearer(authorization));
    }

    @PostMapping("/complete")
    public AppKycSessionResponse completeNative(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return appKycService.completeNative(bearer(authorization));
    }

    @PostMapping("/fail")
    public AppKycSessionResponse fail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                      @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return appKycService.markFailed(bearer(authorization), reason);
    }

    private String bearer(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
