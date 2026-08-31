package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AccountRepository;
import com.dfs.corporate.repository.BrandRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.JwtService;
import com.dfs.corporate.web.dto.LoginRequest;
import com.dfs.corporate.web.dto.SignupRequest;
import com.dfs.corporate.web.dto.VerifyOtpRequest;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {

    private final PartyRepository partyRepository;
    private final AccountRepository accountRepository;
    private final BrandRepository brandRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(PartyRepository partyRepository,
                       AccountRepository accountRepository,
                       BrandRepository brandRepository,
                       OtpService otpService,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder) {
        this.partyRepository = partyRepository;
        this.accountRepository = accountRepository;
        this.brandRepository = brandRepository;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Map<String, Object> signup(SignupRequest req) {
        if (req.getPartyType() != PartyType.MERCHANT && req.getPartyType() != PartyType.SUB_MERCHANT) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Only corporate accounts are supported: MERCHANT or SUB_MERCHANT");
        }
        String email = req.getEmail().trim().toLowerCase();
        if (partyRepository.existsByEmailIgnoreCase(email) || accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (req.getPartyType() == PartyType.SUB_MERCHANT) {
            if (req.getParentPartyPublicId() == null || req.getParentPartyPublicId().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Parent merchant is required for sub-merchant");
            }
            Party parent = partyRepository.findByPublicId(req.getParentPartyPublicId().trim())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Parent merchant not found"));
            if (parent.getPartyType() != PartyType.MERCHANT || parent.getStatus() != PartyStatus.ACTIVE) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Parent must be an active merchant");
            }
        }

        Party party = new Party();
        party.setPublicId(UUID.randomUUID().toString());
        party.setPartyType(req.getPartyType());
        party.setStatus(PartyStatus.DRAFT);
        party.setFullName(req.getFullName().trim());
        party.setBusinessName(req.getBusinessName() != null ? req.getBusinessName().trim() : req.getFullName().trim());
        party.setEmail(email);
        party.setPhone(req.getPhone().trim());
        party.setKycTier("ENTITY_CONSOLIDATED");
        party.setTrackingId(generateTrackingId());
        party.setDraftExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        party.setOnboardingStep(1);
        party.setSanctionsStatus(ScreeningStatus.PENDING);
        party.setIdentityVerificationStatus(IdentityVerificationStatus.PENDING);
        party.setRiskRating(RiskRating.MEDIUM);
        brandRepository.findByCodeIgnoreCase("DFS")
                .map(Brand::getId)
                .ifPresent(party::setBrandId);
        if (req.getPartyType() == PartyType.SUB_MERCHANT) {
            party.setParentPartyId(partyRepository.findByPublicId(req.getParentPartyPublicId().trim()).orElseThrow().getId());
        }
        Party savedParty = partyRepository.save(party);

        Account account = new Account();
        account.setPublicId(UUID.randomUUID().toString());
        account.setPartyId(savedParty.getId());
        account.setEmail(email);
        account.setRole(Role.PARTY_USER);
        account.setStatus(AccountStatus.PENDING_VERIFICATION);
        accountRepository.save(account);

        String code = otpService.issue(email, OtpService.PURPOSE_SIGNUP);
        Map<String, Object> res = new HashMap<>();
        res.put("message", "OTP sent to email");
        res.put("email", email);
        res.put("partyPublicId", savedParty.getPublicId());
        res.put("trackingId", savedParty.getTrackingId());
        // Exposed only when mail is disabled — helps parallel local testing
        res.put("devOtpHint", code);
        return res;
    }

    private String generateTrackingId() {
        int n = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "DFS-" + java.time.LocalDate.now().toString().replace("-", "") + "-" + n;
    }

    @Transactional
    public Map<String, Object> verifyOtp(VerifyOtpRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        otpService.verify(email, OtpService.PURPOSE_SIGNUP, req.getCode());

        Party party = partyRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", account.getRole().name());
        claims.put("partyId", party.getId());
        claims.put("partyStatus", party.getStatus().name());
        claims.put("onboarding", true);
        String token = jwtService.generateToken(email, claims);

        Map<String, Object> res = new HashMap<>();
        res.put("token", token);
        res.put("partyStatus", party.getStatus().name());
        res.put("partyType", party.getPartyType().name());
        res.put("partyPublicId", party.getPublicId());
        res.put("trackingId", party.getTrackingId());
        res.put("role", account.getRole().name());
        return res;
    }

    public Map<String, Object> login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (account.getPasswordHash() == null || !passwordEncoder.matches(req.getPassword(), account.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        Party party = partyRepository.findById(account.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Party not found"));

        if (account.getRole() != Role.PLATFORM_ADMIN) {
            if (party.getStatus() == PartyStatus.PENDING_APPROVAL) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Your application is pending admin approval");
            }
            if (party.getStatus() == PartyStatus.DRAFT) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Please complete onboarding first");
            }
            if (party.getStatus() == PartyStatus.REJECTED) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Application rejected: " + party.getRejectionReason());
            }
            if (account.getStatus() != AccountStatus.ACTIVE || party.getStatus() != PartyStatus.ACTIVE) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active");
            }
        }

        account.setLastLoginAt(Instant.now());
        accountRepository.save(account);

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", account.getRole().name());
        claims.put("partyId", party.getId());
        claims.put("partyStatus", party.getStatus().name());
        String token = jwtService.generateToken(email, claims);

        Map<String, Object> res = new HashMap<>();
        res.put("token", token);
        res.put("role", account.getRole().name());
        res.put("partyStatus", party.getStatus().name());
        res.put("partyType", party.getPartyType().name());
        res.put("partyPublicId", party.getPublicId());
        res.put("fullName", party.getFullName());
        res.put("firstLogin", account.isFirstLogin());
        return res;
    }
}
