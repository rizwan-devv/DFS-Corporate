package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.integration.dfs.CorporateOnboardingHttpClient;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyDocumentRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.util.IdentityFormats;
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
    /** 8 non-thumb fingers (KycApp left/right × 4). */
    public static final List<String> DOC_FINGERS = List.of(
            "FINGER_L1", "FINGER_L2", "FINGER_L3", "FINGER_L4",
            "FINGER_R1", "FINGER_R2", "FINGER_R3", "FINGER_R4"
    );
    public static final List<String> DOC_KYC_KINDS;

    static {
        List<String> kinds = new ArrayList<>();
        kinds.add(DOC_CNIC_FRONT);
        kinds.add(DOC_CNIC_BACK);
        kinds.add(DOC_SELFIE);
        kinds.addAll(DOC_FINGERS);
        DOC_KYC_KINDS = List.copyOf(kinds);
    }

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
        user.setPasswordPlain(IdentityFormats.passwordPlain(req.getNewPassword())); // plain for DFS Account API
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(Instant.now());
        // Keep temp pin for invite retries; password is primary after change
        appUserRepository.save(user);
        return toSession(user);
    }

    @Transactional
    public Map<String, Object> sendMobileOtp(String sessionToken) {
        PartnerAppUser user = requireSession(sessionToken);
        // OTP is the first gate after login (phone+PIN). Password change comes later.
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
    public AppKycOtpVerifyResponse verifyMobileOtp(String sessionToken, AppKycOtpVerifyRequest req) {
        PartnerAppUser user = requireSession(sessionToken);
        // No password change required before OTP verify
        otpService.verifyMobile(user.getPhone(), OtpService.PURPOSE_APP_MOBILE, req.getCode());
        user.setMobileVerified(true);
        if (user.getStatus() == PartnerAppKycStatus.INVITED
                || user.getStatus() == PartnerAppKycStatus.FAILED) {
            user.setStatus(PartnerAppKycStatus.KYC_IN_PROGRESS);
        }
        appUserRepository.save(user);

        // OTP OK → fetch DFS getAllLovs for app dropdowns
        JsonNode lovs = fetchDfsLovs();
        return new AppKycOtpVerifyResponse(toSession(user), lovs);
    }

    /** Proxy DFS getAllLovs (same payload as otp/verify.lovs). */
    public JsonNode lovs() {
        return fetchDfsLovs();
    }

    private JsonNode fetchDfsLovs() {
        try {
            return corporateOnboardingHttpClient.getAllLovs();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "OTP verified but failed to load LOVs from DFS: " + e.getMessage());
        }
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
        if (req.getCnicNumber() != null) user.setCnicNumber(normalizeRequiredCnic(req.getCnicNumber()));
        if (req.getCnicFullName() != null) user.setCnicFullName(trim(req.getCnicFullName()));
        if (req.getDateOfBirth() != null) user.setDateOfBirth(req.getDateOfBirth());
        if (req.getVideoKycRef() != null) user.setVideoKycRef(trim(req.getVideoKycRef()));
        if (req.getBiometricRef() != null) user.setBiometricRef(trim(req.getBiometricRef()));
        if (req.getFatherName() != null) user.setFatherName(trim(req.getFatherName()));
        if (req.getGender() != null) user.setGender(trim(req.getGender()));
        if (req.getPermanentAddress() != null) user.setPermanentAddress(trim(req.getPermanentAddress()));
        if (req.getPresentAddress() != null) user.setPresentAddress(trim(req.getPresentAddress()));
        if (req.getNidIssuanceDate() != null) user.setNidIssuanceDate(req.getNidIssuanceDate());
        if (req.getWalletPin() != null) user.setWalletPin(IdentityFormats.pinPlain(req.getWalletPin()));
        if (req.getImeiNo() != null) user.setImeiNo(trim(req.getImeiNo()));
        if (req.getDeviceModel() != null) user.setDeviceModel(trim(req.getDeviceModel()));
        if (req.getAppVersion() != null) user.setAppVersion(trim(req.getAppVersion()));
        if (req.getProvinceId() != null) user.setProvinceId(trim(req.getProvinceId()));
        if (req.getCityId() != null) user.setCityId(trim(req.getCityId()));
        appUserRepository.save(user);
        return toSession(user);
    }

    @Transactional
    public AppKycSessionResponse uploadDocument(String sessionToken, String kind, MultipartFile file) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);
        storeDoc(user, normalizeDocKind(kind), file);
        appUserRepository.save(user);
        return toSession(user);
    }

    /**
     * All-in-one KYC submit: profile + CNIC F/B + selfie + 8 fingers → KYC_COMPLETED.
     */
    @Transactional
    public AppKycSessionResponse submitAll(
            String sessionToken,
            String cnicNumber,
            String cnicFullName,
            String dateOfBirth,
            String fatherName,
            String gender,
            String permanentAddress,
            String presentAddress,
            String nidIssuanceDate,
            String cityId,
            String provinceId,
            String walletPin,
            String imeiNo,
            String deviceModel,
            String appVersion,
            MultipartFile cnicFront,
            MultipartFile cnicBack,
            MultipartFile selfie,
            MultipartFile fingerL1,
            MultipartFile fingerL2,
            MultipartFile fingerL3,
            MultipartFile fingerL4,
            MultipartFile fingerR1,
            MultipartFile fingerR2,
            MultipartFile fingerR3,
            MultipartFile fingerR4) {

        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);

        if (isBlank(cnicNumber) || isBlank(cnicFullName) || isBlank(dateOfBirth)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "cnicNumber, cnicFullName, and dateOfBirth are required");
        }

        Map<String, MultipartFile> files = new LinkedHashMap<>();
        files.put(DOC_CNIC_FRONT, cnicFront);
        files.put(DOC_CNIC_BACK, cnicBack);
        files.put(DOC_SELFIE, selfie);
        files.put("FINGER_L1", fingerL1);
        files.put("FINGER_L2", fingerL2);
        files.put("FINGER_L3", fingerL3);
        files.put("FINGER_L4", fingerL4);
        files.put("FINGER_R1", fingerR1);
        files.put("FINGER_R2", fingerR2);
        files.put("FINGER_R3", fingerR3);
        files.put("FINGER_R4", fingerR4);

        List<String> missingFiles = new ArrayList<>();
        for (Map.Entry<String, MultipartFile> e : files.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                missingFiles.add(e.getKey());
            }
        }
        if (!missingFiles.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Missing files: " + String.join(", ", missingFiles)
                            + " (need cnicFront, cnicBack, selfie, fingerL1..L4, fingerR1..R4)");
        }

        user.setCnicNumber(normalizeRequiredCnic(cnicNumber));
        user.setCnicFullName(trim(cnicFullName));
        try {
            user.setDateOfBirth(java.time.LocalDate.parse(dateOfBirth.trim()));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "dateOfBirth must be yyyy-MM-dd");
        }
        if (!isBlank(fatherName)) user.setFatherName(trim(fatherName));
        if (!isBlank(gender)) user.setGender(trim(gender));
        if (!isBlank(permanentAddress)) user.setPermanentAddress(trim(permanentAddress));
        if (!isBlank(presentAddress)) user.setPresentAddress(trim(presentAddress));
        if (!isBlank(nidIssuanceDate)) {
            try {
                user.setNidIssuanceDate(java.time.LocalDate.parse(nidIssuanceDate.trim()));
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "nidIssuanceDate must be yyyy-MM-dd");
            }
        }
        if (!isBlank(cityId)) user.setCityId(trim(cityId));
        if (!isBlank(provinceId)) user.setProvinceId(trim(provinceId));
        if (!isBlank(walletPin)) user.setWalletPin(IdentityFormats.pinPlain(walletPin));
        if (!isBlank(imeiNo)) user.setImeiNo(trim(imeiNo));
        if (!isBlank(deviceModel)) user.setDeviceModel(trim(deviceModel));
        if (!isBlank(appVersion)) user.setAppVersion(trim(appVersion));

        for (Map.Entry<String, MultipartFile> e : files.entrySet()) {
            storeDoc(user, e.getKey(), e.getValue());
        }
        user.setSelfieUploaded(true);
        user.setVideoKycRef("APP-CNIC-CAPTURE");
        user.setBiometricRef("FINGERS-8/8");
        appUserRepository.save(user);

        return finalizeCompleted(user);
    }

    private void storeDoc(PartnerAppUser user, String kind, MultipartFile file) {
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
        if (DOC_SELFIE.equals(kind)) {
            user.setSelfieUploaded(true);
        }
        if (DOC_FINGERS.contains(kind)) {
            user.setBiometricRef("FINGERS-CAPTURED");
        }
        if (DOC_CNIC_FRONT.equals(kind) || DOC_CNIC_BACK.equals(kind)) {
            if (isBlank(user.getVideoKycRef())) {
                user.setVideoKycRef("APP-CNIC-CAPTURE");
            }
        }
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

    /** @deprecated Prefer submitAll multipart. Kept for internal/tests. */
    @Transactional
    public AppKycSessionResponse submit(String sessionToken) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);
        validateReady(user);
        return finalizeCompleted(user);
    }

    @Transactional
    public AppKycSessionResponse completeNative(String sessionToken) {
        throw new ApiException(HttpStatus.BAD_REQUEST,
                "Use POST /submit as multipart with profile fields + CNIC/selfie/8 fingers");
    }

    private AppKycSessionResponse finalizeCompleted(PartnerAppUser user) {
        user.setStatus(PartnerAppKycStatus.KYC_COMPLETED);
        user.setCompletedAt(Instant.now());
        user.setFailureReason(null);
        if (isBlank(user.getBiometricRef())) {
            user.setBiometricRef("FINGERS-8/8");
        }
        if (isBlank(user.getVideoKycRef())) {
            user.setVideoKycRef("APP-CNIC-CAPTURE");
        }
        user.setSelfieUploaded(true);
        appUserRepository.save(user);

        // App KYC media accepted as submitted — admin still reviews portal business docs + KYC status
        for (PartyDocument d : documentRepository.findByPartyIdOrderByUploadedAtDesc(user.getPartyId())) {
            if (d.getDocumentCode() != null
                    && d.getDocumentCode().startsWith("APP_" + user.getId() + "_")
                    && d.getStatus() == DocumentStatus.PENDING) {
                d.setStatus(DocumentStatus.APPROVED);
                documentRepository.save(d);
            }
        }

        partnerAppUserService.tryAdvanceParty(user.getPartyId());

        // Advance to PENDING_APPROVAL when all KYCs done — Account API only on admin approve
        Party partyAfter = accountProvisioningService.provisionAfterKycComplete(user.getPartyId());

        Party party = partyRepository.findById(user.getPartyId()).orElse(partyAfter);
        String to = user.getEmail() != null ? user.getEmail() : (party != null ? party.getEmail() : null);
        if (to != null) {
            mailService.send(to, "DFS Corporate — KYC submitted",
                    "Hello " + user.getFullName() + ",\n\n"
                            + "Your mobile KYC was submitted successfully"
                            + (party != null && party.getBusinessName() != null
                            ? " for " + party.getBusinessName() : "")
                            + ".\n\nYour application is now with back-office for verification. "
                            + "After approval you can use the agent app with the password you set.\n\n— DFS Corporate");
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
        if (!Boolean.TRUE.equals(user.getMobileVerified())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mobile OTP verification required first");
        }
        // Password change is after OTP (mustChangePassword flag for app UX / agent credentials).
        // Not required to submit KYC media; set before admin approve for DFS Account API.
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
        List<String> missing = new ArrayList<>();
        for (String kind : DOC_KYC_KINDS) {
            if (!uploaded.contains(docCode(user.getId(), kind))) {
                missing.add(kind);
            }
        }
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Upload before submit: " + String.join(", ", missing)
                            + " (CNIC front/back, selfie, and 8 fingers)");
        }
        if (isBlank(user.getCnicNumber()) || isBlank(user.getCnicFullName()) || user.getDateOfBirth() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Set CNIC number, full name, and date of birth (PUT /profile) before submit");
        }
    }

    private AppKycSessionResponse toSession(PartnerAppUser user) {
        Party party = partyRepository.findById(user.getPartyId()).orElse(null);
        Set<String> uploaded = documentRepository.findByPartyIdOrderByUploadedAtDesc(user.getPartyId()).stream()
                .filter(d -> d.getStatus() != DocumentStatus.REJECTED)
                .map(PartyDocument::getDocumentCode)
                .collect(Collectors.toSet());

        List<Map<String, Object>> docs = new ArrayList<>();
        docs.add(docItem(DOC_CNIC_FRONT, "CNIC front", uploaded.contains(docCode(user.getId(), DOC_CNIC_FRONT))));
        docs.add(docItem(DOC_CNIC_BACK, "CNIC back", uploaded.contains(docCode(user.getId(), DOC_CNIC_BACK))));
        docs.add(docItem(DOC_SELFIE, "Live selfie", uploaded.contains(docCode(user.getId(), DOC_SELFIE))));
        for (String finger : DOC_FINGERS) {
            docs.add(docItem(finger, finger.replace('_', ' '), uploaded.contains(docCode(user.getId(), finger))));
        }

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
        r.setProvinceId(user.getProvinceId());
        r.setCityId(user.getCityId());
        return r;
    }

    private String normalizeDocKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document kind required");
        }
        String k = kind.trim().toUpperCase();
        if (!DOC_KYC_KINDS.contains(k)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Invalid kind. Allowed: " + String.join(", ", DOC_KYC_KINDS));
        }
        return k;
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

    private String normalizeRequiredCnic(String cnic) {
        String digits = IdentityFormats.cnicDigits(cnic);
        if (digits == null || digits.length() != 13) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "cnicNumber must be 13 digits (dashes optional; stored/sent without dashes)");
        }
        return digits;
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : IdentityFormats.phoneDigits(phone);
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String trim(String s) { return isBlank(s) ? null : s.trim(); }
}
