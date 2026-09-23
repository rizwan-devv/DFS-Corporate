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

    /** Send OTP to partner mobile right after login (phone+PIN). No password change required first. */
    @PostMapping("/otp/send")
    public Map<String, Object> sendOtp(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return appKycService.sendMobileOtp(bearer(authorization));
    }

    /** Verify OTP; response includes session + provinces/cities LOVs. */
    @PostMapping("/otp/verify")
    public AppKycOtpVerifyResponse verifyOtp(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                             @Valid @RequestBody AppKycOtpVerifyRequest req) {
        return appKycService.verifyMobileOtp(bearer(authorization), req);
    }

    /** Temp PIN → password (after OTP). Needed for DFS Account API / agent login before admin approve. */
    @PostMapping("/change-password")
    public AppKycSessionResponse changePassword(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                @Valid @RequestBody AppKycChangePasswordRequest req) {
        return appKycService.changePassword(bearer(authorization), req);
    }

    /** Random read-aloud text (~10 sec) for video verification. Call before recording. */
    @PostMapping("/video-challenge")
    public AppKycVideoChallengeResponse videoChallenge(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return appKycService.issueVideoChallenge(bearer(authorization));
    }

    /** Upload recorded read-aloud video (multipart). Requires challengeId from video-challenge. */
    @PostMapping(value = "/video-verification", consumes = "multipart/form-data")
    public AppKycVideoVerificationResponse videoVerification(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam String challengeId,
            @RequestParam("video") MultipartFile video,
            @RequestParam(required = false) String durationMs) {
        return appKycService.uploadVideoVerification(
                bearer(authorization), challengeId, video, durationMs);
    }

    /** Provinces/cities/etc from DFS getAllLovs (same as otp/verify.lovs). */
    @GetMapping("/lovs")
    public JsonNode lovs() {
        return appKycService.lovs();
    }

    /** Proxy DFS backend getAllSegments for optional dropdowns. */
    @GetMapping("/segments")
    public JsonNode segments() {
        return appKycService.segments();
    }

    /**
     * Single KYC finish call: profile fields + CNIC front/back + selfie.
     * Fingerprints are not accepted — verify via NADRA (biometricRef only).
     */
    @PostMapping(value = "/submit", consumes = "multipart/form-data")
    public AppKycSessionResponse submit(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam String cnicNumber,
            @RequestParam String cnicFullName,
            @RequestParam String dateOfBirth,
            @RequestParam(required = false) String fatherName,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String permanentAddress,
            @RequestParam(required = false) String presentAddress,
            @RequestParam(required = false) String nidIssuanceDate,
            @RequestParam(required = false) String cityId,
            @RequestParam(required = false) String provinceId,
            @RequestParam(required = false) String walletPin,
            @RequestParam(required = false) String imeiNo,
            @RequestParam(required = false) String deviceModel,
            @RequestParam(required = false) String appVersion,
            @RequestParam("cnicFront") MultipartFile cnicFront,
            @RequestParam("cnicBack") MultipartFile cnicBack,
            @RequestParam("selfie") MultipartFile selfie) {
        return appKycService.submitAll(
                bearer(authorization),
                cnicNumber, cnicFullName, dateOfBirth,
                fatherName, gender, permanentAddress, presentAddress, nidIssuanceDate,
                cityId, provinceId, walletPin, imeiNo, deviceModel, appVersion,
                cnicFront, cnicBack, selfie);
    }

    @PostMapping("/fail")
    public AppKycSessionResponse fail(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                      @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return appKycService.markFailed(bearer(authorization), reason);
    }

    /** End-of-flow signature photo (one image; backend builds 4-up PNG/JPEG/PDF sheet). */
    @PostMapping(value = "/signature", consumes = "multipart/form-data")
    public AppKycSessionResponse signature(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                           @RequestParam("signature") MultipartFile signature) {
        return appKycService.uploadSignature(bearer(authorization), signature);
    }

    private String bearer(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
