package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AccountRepository;
import com.dfs.corporate.repository.BrandRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.security.JwtService;
import com.dfs.corporate.web.dto.ChangePasswordRequest;
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
    private final FranchiseInviteService franchiseInviteService;
    private final FranchiseCommissionService franchiseCommissionService;
    private final PortalUserService portalUserService;

    public AuthService(PartyRepository partyRepository,
                       AccountRepository accountRepository,
                       BrandRepository brandRepository,
                       OtpService otpService,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       FranchiseInviteService franchiseInviteService,
                       FranchiseCommissionService franchiseCommissionService,
                       PortalUserService portalUserService) {
        this.partyRepository = partyRepository;
        this.accountRepository = accountRepository;
        this.brandRepository = brandRepository;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.franchiseInviteService = franchiseInviteService;
        this.franchiseCommissionService = franchiseCommissionService;
        this.portalUserService = portalUserService;
    }

    @Transactional
    public Map<String, Object> signup(SignupRequest req) {
        boolean viaFranchiseInvite = req.getFranchiseInviteToken() != null && !req.getFranchiseInviteToken().isBlank();
        FranchiseInvite franchiseInvite = null;
        Long parentPartyId = null;
        PartyType partyType = req.getPartyType();

        if (!viaFranchiseInvite && partyType == PartyType.SUB_MERCHANT) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Franchise / child wallet signup requires a secure invite link from the corporate parent "
                            + "(do not enter parent ID manually)");
        }
        if (!viaFranchiseInvite && partyType != PartyType.MERCHANT) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Only Corporate (master) signup is available here. Franchises join via invite link.");
        }

        String email = req.getEmail().trim().toLowerCase();
        if (partyRepository.existsByEmailIgnoreCase(email) || accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }

        if (viaFranchiseInvite) {
            franchiseInvite = franchiseInviteService.consumeForSignup(req.getFranchiseInviteToken().trim());
            parentPartyId = franchiseInvite.getParentPartyId();
            partyType = PartyType.SUB_MERCHANT;
        }

        Party party = new Party();
        party.setPublicId(UUID.randomUUID().toString());
        party.setPartyType(partyType);
        party.setStatus(PartyStatus.DRAFT);
        party.setFullName(req.getFullName().trim());
        String businessName = req.getBusinessName() != null ? req.getBusinessName().trim() : req.getFullName().trim();
        if (viaFranchiseInvite && (req.getBusinessName() == null || req.getBusinessName().isBlank())
                && franchiseInvite.getBusinessName() != null) {
            businessName = franchiseInvite.getBusinessName();
        }
        party.setBusinessName(businessName);
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
        if (parentPartyId != null) {
            party.setParentPartyId(parentPartyId);
        }
        if (viaFranchiseInvite && franchiseInvite.getEntityType() != null && !franchiseInvite.getEntityType().isBlank()) {
            try {
                party.setEntityType(CorporateEntityType.valueOf(franchiseInvite.getEntityType().trim()));
            } catch (IllegalArgumentException ignored) {
                // entity type chosen later in onboarding
            }
        }
        Party savedParty = partyRepository.save(party);

        if (franchiseInvite != null) {
            franchiseInviteService.markCompleted(franchiseInvite, savedParty.getId());
            franchiseCommissionService.proposeFromInvite(franchiseInvite, savedParty.getId());
        }

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
        res.put("partyType", savedParty.getPartyType().name());
        if (parentPartyId != null) {
            res.put("parentLinked", true);
        }
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
            if (party.getStatus() == PartyStatus.DRAFT) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Please complete onboarding first");
            }
            if (party.getStatus() == PartyStatus.REJECTED) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Application rejected: " + party.getRejectionReason());
            }
            // Onboarding in flight: allow portal access to re-upload docs / track KYC
            boolean onboardingInFlight = party.getStatus() == PartyStatus.SUBMITTED
                    || party.getStatus() == PartyStatus.PENDING_APPROVAL
                    || party.getStatus() == PartyStatus.INCOMPLETE;
            if (!onboardingInFlight
                    && (account.getStatus() != AccountStatus.ACTIVE || party.getStatus() != PartyStatus.ACTIVE)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active");
            }
        }

        account.setLastLoginAt(Instant.now());
        accountRepository.save(account);

        portalUserService.ensureOwnerRoles(account, party);
        var portalRoles = portalUserService.rolesOf(account.getId()).stream().map(Enum::name).toList();

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", account.getRole().name());
        claims.put("partyId", party.getId());
        claims.put("partyStatus", party.getStatus().name());
        claims.put("portalRoles", portalRoles);
        String token = jwtService.generateToken(email, claims);

        Map<String, Object> res = new HashMap<>();
        res.put("token", token);
        res.put("role", account.getRole().name());
        res.put("portalRoles", portalRoles);
        res.put("partyStatus", party.getStatus().name());
        res.put("partyType", party.getPartyType().name());
        res.put("partyPublicId", party.getPublicId());
        res.put("fullName", party.getFullName());
        res.put("firstLogin", account.isFirstLogin());
        return res;
    }

    @Transactional
    public Map<String, Object> changePassword(AccountPrincipal principal, ChangePasswordRequest req) {
        if (req.getNewPassword() == null || req.getNewPassword().length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be at least 8 characters");
        }
        if (req.getNewPassword().equals(req.getCurrentPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be different from current password");
        }
        Account account = accountRepository.findById(principal.getAccountId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(req.getCurrentPassword(), account.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        account.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        account.setFirstLogin(false);
        accountRepository.save(account);

        Map<String, Object> res = new HashMap<>();
        res.put("message", "Password updated");
        res.put("firstLogin", false);
        return res;
    }
}
