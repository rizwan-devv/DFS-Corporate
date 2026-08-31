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

    /** Verify OTP; response includes session + provinces/cities LOVs. */
    @PostMapping("/otp/verify")
    public AppKycOtpVerifyResponse verifyOtp(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                             @Valid @RequestBody AppKycOtpVerifyRequest req) {
        return appKycService.verifyMobileOtp(bearer(authorization), req);
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
     * Single KYC finish call: profile fields + CNIC front/back + selfie + 8 fingers.
     * multipart/form-data — see AppKycService.submitAll.
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
            @RequestParam("selfie") MultipartFile selfie,
            @RequestParam("fingerL1") MultipartFile fingerL1,
            @RequestParam("fingerL2") MultipartFile fingerL2,
            @RequestParam("fingerL3") MultipartFile fingerL3,
            @RequestParam("fingerL4") MultipartFile fingerL4,
            @RequestParam("fingerR1") MultipartFile fingerR1,
            @RequestParam("fingerR2") MultipartFile fingerR2,
            @RequestParam("fingerR3") MultipartFile fingerR3,
            @RequestParam("fingerR4") MultipartFile fingerR4) {
        return appKycService.submitAll(
                bearer(authorization),
                cnicNumber, cnicFullName, dateOfBirth,
                fatherName, gender, permanentAddress, presentAddress, nidIssuanceDate,
                cityId, provinceId, walletPin, imeiNo, deviceModel, appVersion,
                cnicFront, cnicBack, selfie,
                fingerL1, fingerL2, fingerL3, fingerL4,
                fingerR1, fingerR2, fingerR3, fingerR4);
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
