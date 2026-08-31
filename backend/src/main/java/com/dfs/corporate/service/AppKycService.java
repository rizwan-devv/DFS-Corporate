package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.integration.dfs.CorporateOnboardingHttpClient;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyDocumentRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.web.dto.*;
import com.dfs.corporate.web.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppKycService {

    public static final String DOC_CNIC_FRONT = "CNIC_FRONT";
    public static final String DOC_CNIC_BACK = "CNIC_BACK";
    public static final String DOC_SELFIE = "SELFIE";

    private final PartnerAppUserRepository appUserRepository;
    private final PartyRepository partyRepository;
    private final PartyDocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final PartnerAppUserService partnerAppUserService;
    private final AccountProvisioningService accountProvisioningService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final CorporateOnboardingHttpClient corporateOnboardingHttpClient;

    public AppKycService(PartnerAppUserRepository appUserRepository,
                         PartyRepository partyRepository,
                         PartyDocumentRepository documentRepository,
                         FileStorageService fileStorageService,
                         PartnerAppUserService partnerAppUserService,
                         AccountProvisioningService accountProvisioningService,
                         OtpService otpService,
                         PasswordEncoder passwordEncoder,
                         MailService mailService,
                         CorporateOnboardingHttpClient corporateOnboardingHttpClient) {
        this.appUserRepository = appUserRepository;
        this.partyRepository = partyRepository;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.partnerAppUserService = partnerAppUserService;
        this.accountProvisioningService = accountProvisioningService;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.corporateOnboardingHttpClient = corporateOnboardingHttpClient;
    }

    @Transactional
    public AppKycSessionResponse openByToken(String inviteToken) {
        PartnerAppUser user = appUserRepository.findByAppInviteToken(inviteToken)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invalid or expired invite link"));
        return startSession(user);
    }

    @Transactional
    public AppKycSessionResponse login(AppKycLoginRequest req) {
        PartnerAppUser user;
        if (req.getEmail() != null && !req.getEmail().isBlank()
                && req.getPassword() != null && !req.getPassword().isBlank()) {
            user = appUserRepository.findByEmailIgnoreCase(req.getEmail().trim())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
            if (user.getPasswordHash() == null
                    || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
            }
        } else if (req.getPhone() != null && req.getPin() != null) {
            String phone = normalizePhone(req.getPhone());
            user = appUserRepository.findByPhoneAndTempPin(phone, req.getPin().trim())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid phone or PIN"));
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Provide phone+pin (invite) or email+password (after change)");
        }
        return startSession(user);
    }

    public AppKycSessionResponse me(String sessionToken) {
        return toSession(requireSession(sessionToken));
    }

    @Transactional
    public AppKycSessionResponse changePassword(String sessionToken, AppKycChangePasswordRequest req) {
        PartnerAppUser user = requireSession(sessionToken);
        if (user.getStatus() == PartnerAppKycStatus.KYC_COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "KYC already completed");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "newPassword and confirmPassword do not match");
        }
        boolean currentOk = req.getCurrentPassword().equals(user.getTempPin())
                || (user.getPasswordHash() != null
                && passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash()));
        if (!currentOk) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password / PIN is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(Instant.now());
        // Keep temp pin for invite retries; password is primary after change
        appUserRepository.save(user);
        return toSession(user);
    }

    @Transactional
    public Map<String, Object> sendMobileOtp(String sessionToken) {
        PartnerAppUser user = requireSession(sessionToken);
        if (Boolean.TRUE.equals(user.getMustChangePassword()) || user.getPasswordHash() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Change password before requesting mobile OTP");
        }
        if (Boolean.TRUE.equals(user.getMobileVerified())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mobile already verified");
        }
        String code = otpService.issueMobile(user.getPhone(), OtpService.PURPOSE_APP_MOBILE);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("sent", true);
        res.put("phone", user.getPhone());
        res.put("message", "OTP sent to mobile (logged if SMS gateway not configured)");
        res.put("devOtpHint", code);
        return res;
    }

    @Transactional
    public AppKycSessionResponse verifyMobileOtp(String sessionToken, AppKycOtpVerifyRequest req) {
        PartnerAppUser user = requireSession(sessionToken);
        if (Boolean.TRUE.equals(user.getMustChangePassword()) || user.getPasswordHash() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Change password before verifying OTP");
        }
        otpService.verifyMobile(user.getPhone(), OtpService.PURPOSE_APP_MOBILE, req.getCode());
        user.setMobileVerified(true);
        if (user.getStatus() == PartnerAppKycStatus.INVITED
                || user.getStatus() == PartnerAppKycStatus.FAILED) {
            user.setStatus(PartnerAppKycStatus.KYC_IN_PROGRESS);
        }
        appUserRepository.save(user);
        return toSession(user);
    }

    public JsonNode segments() {
        try {
            return corporateOnboardingHttpClient.getAllSegments();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Failed to load segments: " + e.getMessage());
        }
    }

    @Transactional
    public AppKycSessionResponse updateProfile(String sessionToken, AppKycProfileRequest req) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);
        if (req.getCnicNumber() != null) user.setCnicNumber(trim(req.getCnicNumber()));
        if (req.getCnicFullName() != null) user.setCnicFullName(trim(req.getCnicFullName()));
        if (req.getDateOfBirth() != null) user.setDateOfBirth(req.getDateOfBirth());
        if (req.getVideoKycRef() != null) user.setVideoKycRef(trim(req.getVideoKycRef()));
        if (req.getBiometricRef() != null) user.setBiometricRef(trim(req.getBiometricRef()));
        if (req.getFatherName() != null) user.setFatherName(trim(req.getFatherName()));
        if (req.getGender() != null) user.setGender(trim(req.getGender()));
        if (req.getPermanentAddress() != null) user.setPermanentAddress(trim(req.getPermanentAddress()));
        if (req.getPresentAddress() != null) user.setPresentAddress(trim(req.getPresentAddress()));
        if (req.getNidIssuanceDate() != null) user.setNidIssuanceDate(req.getNidIssuanceDate());
        if (req.getWalletPin() != null) user.setWalletPin(trim(req.getWalletPin()));
        if (req.getImeiNo() != null) user.setImeiNo(trim(req.getImeiNo()));
        if (req.getDeviceModel() != null) user.setDeviceModel(trim(req.getDeviceModel()));
        if (req.getAppVersion() != null) user.setAppVersion(trim(req.getAppVersion()));
        appUserRepository.save(user);
        return toSession(user);
    }

    @Transactional
    public AppKycSessionResponse uploadDocument(String sessionToken, String kind, MultipartFile file) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);
        String code = docCode(user.getId(), kind);
        String path = fileStorageService.store(user.getPartyId(), code, file);
        PartyDocument doc = documentRepository.findByPartyIdAndDocumentCode(user.getPartyId(), code)
                .orElseGet(PartyDocument::new);
        doc.setPartyId(user.getPartyId());
        doc.setDocumentCode(code);
        doc.setOriginalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : kind);
        doc.setStoredPath(path);
        doc.setContentType(file.getContentType());
        doc.setStatus(DocumentStatus.PENDING);
        documentRepository.save(doc);
        if (DOC_SELFIE.equalsIgnoreCase(kind)) {
            user.setSelfieUploaded(true);
            appUserRepository.save(user);
        }
        return toSession(user);
    }

    @Transactional
    public AppKycSessionResponse stubVideo(String sessionToken) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);
        user.setVideoKycRef("STUB-VIDEO-" + UUID.randomUUID().toString().substring(0, 8));
        appUserRepository.save(user);
        return toSession(user);
    }

    @Transactional
    public AppKycSessionResponse stubBiometric(String sessionToken) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);
        user.setBiometricRef("STUB-BIO-" + UUID.randomUUID().toString().substring(0, 8));
        appUserRepository.save(user);
        return toSession(user);
    }

    @Transactional
    public AppKycSessionResponse submit(String sessionToken) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);
        validateReady(user);
        return finalizeCompleted(user);
    }

    @Transactional
    public AppKycSessionResponse completeNative(String sessionToken) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);
        if (isBlank(user.getVideoKycRef())) {
            user.setVideoKycRef("NATIVE-VIDEO-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (isBlank(user.getBiometricRef())) {
            user.setBiometricRef("NATIVE-BIO-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (isBlank(user.getCnicFullName())) {
            user.setCnicFullName(user.getFullName());
        }
        if (isBlank(user.getCnicNumber())) {
            user.setCnicNumber("PENDING-OCR");
        }
        if (user.getDateOfBirth() == null) {
            user.setDateOfBirth(java.time.LocalDate.of(1990, 1, 1));
        }
        user.setSelfieUploaded(true);
        appUserRepository.save(user);
        return finalizeCompleted(user);
    }

    private AppKycSessionResponse finalizeCompleted(PartnerAppUser user) {
        user.setStatus(PartnerAppKycStatus.KYC_COMPLETED);
        user.setCompletedAt(Instant.now());
        user.setFailureReason(null);
        appUserRepository.save(user);
        partnerAppUserService.tryAdvanceParty(user.getPartyId());

        // When all partners done → call DFS backend account API
        Party partyAfter = accountProvisioningService.provisionAfterKycComplete(user.getPartyId());

        Party party = partyRepository.findById(user.getPartyId()).orElse(partyAfter);
        String to = user.getEmail() != null ? user.getEmail() : (party != null ? party.getEmail() : null);
        if (to != null) {
            String provision = party != null && party.getAccountProvisionStatus() != null
                    ? party.getAccountProvisionStatus().name() : "N/A";
            mailService.send(to, "DFS Corporate — KYC submitted",
                    "Hello " + user.getFullName() + ",\n\n"
                            + "Your mobile KYC was submitted successfully"
                            + (party != null && party.getBusinessName() != null
                            ? " for " + party.getBusinessName() : "")
                            + ".\n\nAccount provision status: " + provision + "\n\n— DFS Corporate");
        }
        return toSession(user);
    }

    @Transactional
    public AppKycSessionResponse markFailed(String sessionToken, String reason) {
        PartnerAppUser user = requireSession(sessionToken);
        if (user.getStatus() == PartnerAppKycStatus.KYC_COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "KYC already completed");
        }
        user.setStatus(PartnerAppKycStatus.FAILED);
        user.setFailureReason(reason != null && !reason.isBlank() ? reason.trim() : "KYC verification failed");
        user.setTempPin(String.valueOf(100000 + new Random().nextInt(900000)));
        user.setAppInviteToken(UUID.randomUUID().toString().replace("-", ""));
        user.setSessionToken(null);
        user.setMustChangePassword(true);
        user.setMobileVerified(false);
        user.setPasswordHash(null);
        appUserRepository.save(user);

        Party party = partyRepository.findById(user.getPartyId()).orElse(null);
        String to = user.getEmail() != null ? user.getEmail() : (party != null ? party.getEmail() : null);
        if (to != null) {
            mailService.send(to, "DFS Corporate — KYC failed, please retry",
                    "Hello " + user.getFullName() + ",\n\n"
                            + "Your KYC could not be completed.\n"
                            + "Reason: " + user.getFailureReason() + "\n\n"
                            + "User ID (phone): " + user.getPhone() + "\n"
                            + "New temporary PIN: " + user.getTempPin() + "\n\n"
                            + "Open the app and try again.\n\n— DFS Corporate");
        }
        return toSession(user);
    }

    private AppKycSessionResponse startSession(PartnerAppUser user) {
        if (user.getStatus() == PartnerAppKycStatus.KYC_COMPLETED) {
            user.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
            appUserRepository.save(user);
            return toSession(user);
        }
        if (user.getStatus() == PartnerAppKycStatus.FAILED || user.getStatus() == PartnerAppKycStatus.INVITED) {
            if (Boolean.TRUE.equals(user.getMobileVerified())) {
                user.setStatus(PartnerAppKycStatus.KYC_IN_PROGRESS);
            }
            user.setFailureReason(null);
        }
        user.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
        appUserRepository.save(user);
        return toSession(user);
    }

    private void requireMobileGate(PartnerAppUser user) {
        if (Boolean.TRUE.equals(user.getMustChangePassword()) || user.getPasswordHash() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Force password change required first");
        }
        if (!Boolean.TRUE.equals(user.getMobileVerified())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mobile OTP verification required first");
        }
    }

    private PartnerAppUser requireSession(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "App session required");
        }
        return appUserRepository.findBySessionToken(sessionToken.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired app session"));
    }

    private PartnerAppUser requireEditable(String sessionToken) {
        PartnerAppUser user = requireSession(sessionToken);
        if (user.getStatus() == PartnerAppKycStatus.KYC_COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "KYC already completed");
        }
        if (user.getStatus() != PartnerAppKycStatus.KYC_IN_PROGRESS
                && user.getStatus() != PartnerAppKycStatus.INVITED
                && user.getStatus() != PartnerAppKycStatus.FAILED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "KYC not editable in status " + user.getStatus());
        }
        if (Boolean.TRUE.equals(user.getMobileVerified())
                && user.getStatus() != PartnerAppKycStatus.KYC_IN_PROGRESS) {
            user.setStatus(PartnerAppKycStatus.KYC_IN_PROGRESS);
            appUserRepository.save(user);
        }
        return user;
    }

    private void validateReady(PartnerAppUser user) {
        Set<String> uploaded = documentRepository.findByPartyIdOrderByUploadedAtDesc(user.getPartyId()).stream()
                .filter(d -> d.getStatus() != DocumentStatus.REJECTED)
                .map(PartyDocument::getDocumentCode)
                .collect(Collectors.toSet());
        List<String> need = List.of(
                docCode(user.getId(), DOC_CNIC_FRONT),
                docCode(user.getId(), DOC_CNIC_BACK),
                docCode(user.getId(), DOC_SELFIE)
        );
        List<String> missing = need.stream().filter(c -> !uploaded.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Upload CNIC front/back and selfie before submit");
        }
        if (isBlank(user.getCnicNumber()) || isBlank(user.getCnicFullName()) || user.getDateOfBirth() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Complete CNIC fields (number, name, DOB) before submit");
        }
        if (isBlank(user.getVideoKycRef())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Complete video KYC step (use stub in MVP)");
        }
        if (isBlank(user.getBiometricRef())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Complete biometric step (use stub in MVP)");
        }
    }

    private AppKycSessionResponse toSession(PartnerAppUser user) {
        Party party = partyRepository.findById(user.getPartyId()).orElse(null);
        Set<String> uploaded = documentRepository.findByPartyIdOrderByUploadedAtDesc(user.getPartyId()).stream()
                .filter(d -> d.getStatus() != DocumentStatus.REJECTED)
                .map(PartyDocument::getDocumentCode)
                .collect(Collectors.toSet());

        List<Map<String, Object>> docs = List.of(
                docItem(DOC_CNIC_FRONT, "CNIC front", uploaded.contains(docCode(user.getId(), DOC_CNIC_FRONT))),
                docItem(DOC_CNIC_BACK, "CNIC back", uploaded.contains(docCode(user.getId(), DOC_CNIC_BACK))),
                docItem(DOC_SELFIE, "Live selfie", uploaded.contains(docCode(user.getId(), DOC_SELFIE)))
        );

        boolean canSubmit = false;
        try {
            if (user.getStatus() != PartnerAppKycStatus.KYC_COMPLETED
                    && !Boolean.TRUE.equals(user.getMustChangePassword())
                    && Boolean.TRUE.equals(user.getMobileVerified())) {
                validateReady(user);
                canSubmit = true;
            }
        } catch (ApiException ignored) {
            canSubmit = false;
        }

        AppKycSessionResponse r = new AppKycSessionResponse();
        r.setSessionToken(user.getSessionToken());
        r.setAppUserId(user.getId());
        r.setPhone(user.getPhone());
        r.setFullName(user.getFullName());
        r.setEmail(user.getEmail());
        r.setStatus(user.getStatus());
        r.setBusinessName(party != null ? party.getBusinessName() : null);
        r.setTrackingId(party != null ? party.getTrackingId() : null);
        r.setCnicNumber(user.getCnicNumber());
        r.setCnicFullName(user.getCnicFullName());
        r.setDateOfBirth(user.getDateOfBirth());
        r.setVideoKycRef(user.getVideoKycRef());
        r.setBiometricRef(user.getBiometricRef());
        r.setSelfieUploaded(Boolean.TRUE.equals(user.getSelfieUploaded())
                || uploaded.contains(docCode(user.getId(), DOC_SELFIE)));
        r.setFailureReason(user.getFailureReason());
        r.setRequiredDocuments(docs);
        r.setCanSubmit(canSubmit);
        r.setCompletedAt(user.getCompletedAt());
        r.setMustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()) || user.getPasswordHash() == null);
        r.setMobileVerified(Boolean.TRUE.equals(user.getMobileVerified()));
        r.setFatherName(user.getFatherName());
        r.setGender(user.getGender());
        r.setPermanentAddress(user.getPermanentAddress());
        r.setPresentAddress(user.getPresentAddress());
        r.setNidIssuanceDate(user.getNidIssuanceDate());
        r.setAccountProvisionStatus(party != null && party.getAccountProvisionStatus() != null
                ? party.getAccountProvisionStatus().name() : null);
        r.setDfsAccountId(party != null ? party.getDfsAccountId() : null);
        return r;
    }

    private Map<String, Object> docItem(String kind, String label, boolean uploaded) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("label", label);
        m.put("uploaded", uploaded);
        return m;
    }

    public static String docCode(Long appUserId, String kind) {
        return "APP_" + appUserId + "_" + kind.toUpperCase();
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9+]", "");
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String trim(String s) { return isBlank(s) ? null : s.trim(); }
}
