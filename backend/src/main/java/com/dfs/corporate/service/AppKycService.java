package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.integration.dfs.CorporateOnboardingHttpClient;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyDocumentRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.repository.VideoChallengeRepository;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.web.dto.*;
import com.dfs.corporate.web.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppKycService {

    public static final String DOC_CNIC_FRONT = "CNIC_FRONT";
    public static final String DOC_CNIC_BACK = "CNIC_BACK";
    public static final String DOC_SELFIE = "SELFIE";
    public static final String DOC_VIDEO_READALOUD = "VIDEO_READALOUD";
    public static final String DOC_SIGNATURE = "SIGNATURE";
    public static final String DOC_SIGNATURE_SHEET_PNG = "SIGNATURE_SHEET_PNG";
    public static final String DOC_SIGNATURE_SHEET_JPEG = "SIGNATURE_SHEET_JPEG";
    public static final String DOC_SIGNATURE_SHEET_PDF = "SIGNATURE_SHEET_PDF";
    /**
     * Legacy finger image codes — no longer accepted or required.
     * Fingerprints are verified via NADRA; DFS stores only a biometric/NADRA ref, not images.
     */
    public static final List<String> DOC_FINGERS_LEGACY = List.of(
            "FINGER_L1", "FINGER_L2", "FINGER_L3", "FINGER_L4",
            "FINGER_R1", "FINGER_R2", "FINGER_R3", "FINGER_R4"
    );
    /** Required mobile KYC media stored in DFS (CNIC + selfie). */
    public static final List<String> DOC_KYC_KINDS = List.of(
            DOC_CNIC_FRONT, DOC_CNIC_BACK, DOC_SELFIE
    );

    private static final int VIDEO_CHALLENGE_TTL_MINUTES = 10;
    private static final int VIDEO_DURATION_SECONDS = 10;
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/3gpp", "video/quicktime", "application/octet-stream");

    private final PartnerAppUserRepository appUserRepository;
    private final PartyRepository partyRepository;
    private final PartyDocumentRepository documentRepository;
    private final VideoChallengeRepository videoChallengeRepository;
    private final FileStorageService fileStorageService;
    private final SignatureSheetService signatureSheetService;
    private final VideoScriptService videoScriptService;
    private final PartnerAppUserService partnerAppUserService;
    private final AccountProvisioningService accountProvisioningService;
    private final PartyCmsIdentitySync partyCmsIdentitySync;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final CorporateOnboardingHttpClient corporateOnboardingHttpClient;
    private final ObjectMapper objectMapper;

    public AppKycService(PartnerAppUserRepository appUserRepository,
                         PartyRepository partyRepository,
                         PartyDocumentRepository documentRepository,
                         VideoChallengeRepository videoChallengeRepository,
                         FileStorageService fileStorageService,
                         SignatureSheetService signatureSheetService,
                         VideoScriptService videoScriptService,
                         PartnerAppUserService partnerAppUserService,
                         AccountProvisioningService accountProvisioningService,
                         PartyCmsIdentitySync partyCmsIdentitySync,
                         OtpService otpService,
                         PasswordEncoder passwordEncoder,
                         MailService mailService,
                         CorporateOnboardingHttpClient corporateOnboardingHttpClient,
                         ObjectMapper objectMapper) {
        this.appUserRepository = appUserRepository;
        this.partyRepository = partyRepository;
        this.documentRepository = documentRepository;
        this.videoChallengeRepository = videoChallengeRepository;
        this.fileStorageService = fileStorageService;
        this.signatureSheetService = signatureSheetService;
        this.videoScriptService = videoScriptService;
        this.partnerAppUserService = partnerAppUserService;
        this.accountProvisioningService = accountProvisioningService;
        this.partyCmsIdentitySync = partyCmsIdentitySync;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.corporateOnboardingHttpClient = corporateOnboardingHttpClient;
        this.objectMapper = objectMapper;
    }

    private record IssuedVideoChallenge(VideoChallenge challenge, VideoScriptService.ScriptPick script,
                                        String businessName) {}

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
        if (Boolean.TRUE.equals(user.getBankVisitRequired())
                || user.getStatus() == PartnerAppKycStatus.BANK_VISIT_REQUIRED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Phone KYC failed 3 times. Please visit the bank/office. Backoffice can approve your partner KYC with a reason.");
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

        // OTP OK → fetch DFS getAllLovs + inject video read-aloud text under data.videoKyc
        JsonNode dfsLovs = fetchDfsLovs();
        IssuedVideoChallenge issued = issueVideoChallengeForUser(user);
        JsonNode lovs = mergeVideoKycIntoLovs(dfsLovs, issued);
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

    /** Re-issue read-aloud script if challenge expired (optional; also included in otp/verify lovs). */
    @Transactional
    public AppKycVideoChallengeResponse issueVideoChallenge(String sessionToken) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);
        IssuedVideoChallenge issued = issueVideoChallengeForUser(user);
        return toVideoChallengeResponse(user, issued);
    }

    private IssuedVideoChallenge issueVideoChallengeForUser(PartnerAppUser user) {
        expireOpenChallenges(user.getId());

        Party party = partyRepository.findById(user.getPartyId()).orElse(null);
        String businessName = party != null ? party.getBusinessName() : null;
        VideoScriptService.ScriptPick script = videoScriptService.pickRandom(user.getFullName(), businessName);

        VideoChallenge challenge = new VideoChallenge();
        challenge.setChallengeId("vc_" + UUID.randomUUID().toString().replace("-", ""));
        challenge.setAppUserId(user.getId());
        challenge.setTemplateId(script.templateId());
        challenge.setScriptText(script.scriptText());
        challenge.setExpiresAt(Instant.now().plus(VIDEO_CHALLENGE_TTL_MINUTES, ChronoUnit.MINUTES));
        challenge.setStatus(VideoChallengeStatus.ISSUED);
        videoChallengeRepository.save(challenge);

        user.setVideoVerificationStatus(VideoVerificationStatus.CHALLENGE_ISSUED);
        appUserRepository.save(user);
        return new IssuedVideoChallenge(challenge, script, businessName);
    }

    private AppKycVideoChallengeResponse toVideoChallengeResponse(PartnerAppUser user, IssuedVideoChallenge issued) {
        AppKycVideoChallengeResponse res = new AppKycVideoChallengeResponse();
        res.setChallengeId(issued.challenge().getChallengeId());
        res.setScriptText(issued.challenge().getScriptText());
        res.setDurationSeconds(VIDEO_DURATION_SECONDS);
        res.setExpiresAt(issued.challenge().getExpiresAt());
        res.setFullName(user.getFullName());
        res.setBusinessName(issued.businessName());
        res.setTemplateId(issued.script().templateId());
        return res;
    }

    /** Adds data.videoKyc to DFS getAllLovs copy (city/province unchanged). */
    private JsonNode mergeVideoKycIntoLovs(JsonNode dfsLovs, IssuedVideoChallenge issued) {
        ObjectNode root = dfsLovs != null && dfsLovs.isObject()
                ? (ObjectNode) dfsLovs.deepCopy()
                : objectMapper.createObjectNode();
        ObjectNode data;
        if (root.has("data") && root.get("data").isObject()) {
            data = (ObjectNode) root.get("data");
        } else {
            data = objectMapper.createObjectNode();
            root.set("data", data);
        }

        VideoChallenge challenge = issued.challenge();
        ObjectNode videoKyc = objectMapper.createObjectNode();
        videoKyc.put("challengeId", challenge.getChallengeId());
        videoKyc.put("scriptText", challenge.getScriptText());
        videoKyc.put("durationSeconds", VIDEO_DURATION_SECONDS);
        videoKyc.put("expiresAt", challenge.getExpiresAt().toString());
        videoKyc.put("templateId", issued.script().templateId());
        ArrayNode lines = videoKyc.putArray("lines");
        for (String line : videoScriptService.toDisplayLines(challenge.getScriptText())) {
            lines.add(line);
        }
        data.set("videoKyc", videoKyc);
        return root;
    }

    /** Upload recorded read-aloud video for the issued challenge. */
    @Transactional
    public AppKycVideoVerificationResponse uploadVideoVerification(String sessionToken,
                                                                     String challengeId,
                                                                     MultipartFile video,
                                                                     String durationMs) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);

        if (isBlank(challengeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "challengeId is required");
        }
        if (video == null || video.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "video file is required");
        }
        validateVideoFile(video);

        VideoChallenge challenge = videoChallengeRepository.findByChallengeId(challengeId.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid challengeId"));
        if (!challenge.getAppUserId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Challenge does not belong to this session");
        }
        if (challenge.getStatus() != VideoChallengeStatus.ISSUED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Challenge already used");
        }
        if (Instant.now().isAfter(challenge.getExpiresAt())) {
            challenge.setStatus(VideoChallengeStatus.EXPIRED);
            videoChallengeRepository.save(challenge);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Challenge expired — request a new video-challenge");
        }

        String code = docCode(user.getId(), DOC_VIDEO_READALOUD);
        String path = fileStorageService.store(user.getPartyId(), code, video);

        PartyDocument doc = documentRepository.findByPartyIdAndDocumentCode(user.getPartyId(), code)
                .orElseGet(PartyDocument::new);
        doc.setPartyId(user.getPartyId());
        doc.setDocumentCode(code);
        doc.setOriginalName(video.getOriginalFilename() != null ? video.getOriginalFilename() : "video-readaloud");
        doc.setStoredPath(path);
        doc.setContentType(video.getContentType());
        doc.setStatus(DocumentStatus.PENDING);
        documentRepository.save(doc);

        challenge.setVideoPath(path);
        challenge.setConsumedAt(Instant.now());
        challenge.setStatus(VideoChallengeStatus.UPLOADED);
        videoChallengeRepository.save(challenge);

        user.setVideoKycRef("APP_" + user.getId() + "_VIDEO_" + challenge.getChallengeId());
        user.setVideoVerificationStatus(VideoVerificationStatus.UPLOADED);
        appUserRepository.save(user);

        String msg = "Video received"
                + (durationMs != null && !durationMs.isBlank() ? " (" + durationMs.trim() + " ms)" : "")
                + "; complete CNIC front/back + selfie submit next";
        return new AppKycVideoVerificationResponse(toSession(user), msg);
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
     * All-in-one KYC submit: profile + CNIC front/back + selfie → KYC_COMPLETED.
     * Fingerprints are not stored; NADRA verification sets biometricRef separately.
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
            MultipartFile selfie) {

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

        List<String> missingFiles = new ArrayList<>();
        for (Map.Entry<String, MultipartFile> e : files.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                missingFiles.add(e.getKey());
            }
        }
        if (!missingFiles.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Missing files: " + String.join(", ", missingFiles)
                            + " (need cnicFront, cnicBack, selfie)");
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
        appUserRepository.save(user);

        validateReady(user);
        return finalizeCompleted(user);
    }

    private void storeDoc(PartnerAppUser user, String kind, MultipartFile file) {
        if (DOC_FINGERS_LEGACY.contains(kind) || kind.startsWith("FINGER_")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Fingerprint images are not stored in DFS; verify via NADRA and set biometricRef only");
        }
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
        if (DOC_SIGNATURE.equals(kind)) {
            user.setSignatureUploaded(true);
        }
    }

    private void expireOpenChallenges(Long appUserId) {
        for (VideoChallenge open : videoChallengeRepository.findByAppUserIdAndStatus(
                appUserId, VideoChallengeStatus.ISSUED)) {
            open.setStatus(VideoChallengeStatus.EXPIRED);
            videoChallengeRepository.save(open);
        }
    }

    private void validateVideoFile(MultipartFile video) {
        String contentType = video.getContentType();
        if (contentType != null && !ALLOWED_VIDEO_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Unsupported video type: " + contentType + " (use mp4 or webm)");
        }
        String name = video.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".mp4") && !lower.endsWith(".webm") && !lower.endsWith(".mov")
                    && !lower.endsWith(".3gp")) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Video file must be .mp4, .webm, .mov, or .3gp");
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

    /**
     * Placeholder until live NADRA BV is wired — stores a verification ref only (no finger images).
     */
    @Transactional
    public AppKycSessionResponse stubBiometric(String sessionToken) {
        PartnerAppUser user = requireEditable(sessionToken);
        requireMobileGate(user);
        user.setBiometricRef("NADRA-STUB-" + UUID.randomUUID().toString().substring(0, 8));
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
                "Use POST /submit as multipart with profile fields + cnicFront, cnicBack, selfie");
    }

    private AppKycSessionResponse finalizeCompleted(PartnerAppUser user) {
        user.setStatus(PartnerAppKycStatus.KYC_COMPLETED);
        user.setCompletedAt(Instant.now());
        user.setFailureReason(null);
        if (isBlank(user.getBiometricRef())) {
            user.setBiometricRef("NADRA_PENDING");
        }
        user.setSelfieUploaded(true);
        appUserRepository.save(user);

        // Mobile KYC media stays PENDING until backoffice View + Approve/Reject each file.
        partyCmsIdentitySync.applyKycCnic(user.getPartyId(), user.getCnicNumber());

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
        if (Boolean.TRUE.equals(user.getBankVisitRequired())
                || user.getStatus() == PartnerAppKycStatus.BANK_VISIT_REQUIRED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Bank visit already required — phone KYC retries are closed");
        }

        int fails = (user.getKycFailCount() != null ? user.getKycFailCount() : 0) + 1;
        user.setKycFailCount(fails);
        String failReason = reason != null && !reason.isBlank() ? reason.trim() : "KYC verification failed";
        user.setFailureReason(failReason);
        user.setSessionToken(null);

        Party party = partyRepository.findById(user.getPartyId()).orElse(null);
        String to = user.getEmail() != null ? user.getEmail() : (party != null ? party.getEmail() : null);

        if (fails >= PartnerAppUserService.MAX_PHONE_KYC_FAILS) {
            user.setStatus(PartnerAppKycStatus.BANK_VISIT_REQUIRED);
            user.setBankVisitRequired(true);
            user.setTempPin(String.valueOf(100000 + new Random().nextInt(900000)));
            user.setAppInviteToken(UUID.randomUUID().toString().replace("-", ""));
            user.setMustChangePassword(true);
            user.setMobileVerified(false);
            user.setPasswordHash(null);
            user.setVideoVerificationStatus(VideoVerificationStatus.NONE);
            user.setVideoKycRef(null);
            appUserRepository.save(user);
            if (to != null) {
                mailService.send(to, "DFS Corporate — Visit bank/office for KYC",
                        "Hello " + user.getFullName() + ",\n\n"
                                + "Your phone KYC failed " + fails + " times.\n"
                                + "Reason: " + failReason + "\n\n"
                                + "Please visit the bank/office. Backoffice can complete your partner KYC with a written reason.\n\n"
                                + "— DFS Corporate");
            }
            return toSession(user);
        }

        user.setStatus(PartnerAppKycStatus.FAILED);
        user.setTempPin(String.valueOf(100000 + new Random().nextInt(900000)));
        user.setAppInviteToken(UUID.randomUUID().toString().replace("-", ""));
        user.setMustChangePassword(true);
        user.setMobileVerified(false);
        user.setPasswordHash(null);
        user.setVideoVerificationStatus(VideoVerificationStatus.NONE);
        user.setVideoKycRef(null);
        appUserRepository.save(user);

        int remaining = PartnerAppUserService.MAX_PHONE_KYC_FAILS - fails;
        if (to != null) {
            mailService.send(to, "DFS Corporate — KYC failed, please retry",
                    "Hello " + user.getFullName() + ",\n\n"
                            + "Your KYC could not be completed.\n"
                            + "Reason: " + failReason + "\n"
                            + "Attempts used: " + fails + " of " + PartnerAppUserService.MAX_PHONE_KYC_FAILS
                            + " (" + remaining + " left).\n\n"
                            + "User ID (phone): " + user.getPhone() + "\n"
                            + "New temporary PIN: " + user.getTempPin() + "\n\n"
                            + "Open the app and try again.\n\n— DFS Corporate");
        }
        return toSession(user);
    }

    /**
     * Capture one signature image. Stored once; backend tiles it into a 4-up printable
     * PNG / JPEG / PDF sheet. Allowed after KYC so partners can finish the end-of-flow step.
     */
    @Transactional
    public AppKycSessionResponse uploadSignature(String sessionToken, MultipartFile signature) {
        PartnerAppUser user = requireSession(sessionToken);
        persistSignature(user, signature);
        return toSession(user);
    }

    /**
     * Portal path: party owner uploads a partner signature image when the app step was skipped.
     */
    @Transactional
    public PartnerAppUserResponse uploadSignatureForParty(Long partyId, Long appUserId, MultipartFile signature) {
        PartnerAppUser user = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Partner app user not found"));
        if (!partyId.equals(user.getPartyId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Partner does not belong to this party");
        }
        persistSignature(user, signature);
        return partnerAppUserService.toResponse(user);
    }

    private void persistSignature(PartnerAppUser user, MultipartFile signature) {
        if (signature == null || signature.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "signature file is required");
        }
        if (user.getStatus() == PartnerAppKycStatus.BANK_VISIT_REQUIRED
                || Boolean.TRUE.equals(user.getBankVisitRequired())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bank visit required — signature upload is closed");
        }
        byte[] bytes;
        try {
            bytes = signature.getBytes();
        } catch (java.io.IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read signature file");
        }
        String originalName = signature.getOriginalFilename() != null
                ? signature.getOriginalFilename() : "signature.jpg";
        String contentType = signature.getContentType() != null
                ? signature.getContentType() : "image/jpeg";

        String code = docCode(user.getId(), DOC_SIGNATURE);
        String path = fileStorageService.storeBytes(user.getPartyId(), code, bytes,
                extensionOf(originalName, ".jpg"));
        upsertDoc(user.getPartyId(), code, originalName, path, contentType, DocumentStatus.PENDING);

        SignatureSheetService.Sheets sheets = signatureSheetService.createFromImageBytes(bytes);
        storeSheet(user, DOC_SIGNATURE_SHEET_PNG, "signature-sheet.png", "image/png", sheets.png());
        storeSheet(user, DOC_SIGNATURE_SHEET_JPEG, "signature-sheet.jpg", "image/jpeg", sheets.jpeg());
        storeSheet(user, DOC_SIGNATURE_SHEET_PDF, "signature-sheet.pdf", "application/pdf", sheets.pdf());

        user.setSignatureUploaded(true);
        appUserRepository.save(user);
    }

    private void storeSheet(PartnerAppUser user, String kind, String fileName, String contentType, byte[] bytes) {
        String code = docCode(user.getId(), kind);
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
        String path = fileStorageService.storeBytes(user.getPartyId(), code, bytes, ext);
        // Derivatives are generated — auto-approve so they do not block party approve.
        upsertDoc(user.getPartyId(), code, fileName, path, contentType, DocumentStatus.APPROVED);
    }

    private void upsertDoc(Long partyId, String code, String originalName, String path,
                           String contentType, DocumentStatus status) {
        PartyDocument doc = documentRepository.findByPartyIdAndDocumentCode(partyId, code)
                .orElseGet(PartyDocument::new);
        doc.setPartyId(partyId);
        doc.setDocumentCode(code);
        doc.setOriginalName(originalName);
        doc.setStoredPath(path);
        doc.setContentType(contentType);
        doc.setStatus(status);
        doc.setReviewNote(null);
        doc.setUploadedAt(Instant.now());
        documentRepository.save(doc);
    }

    private static String extensionOf(String name, String fallback) {
        if (name == null || !name.contains(".")) return fallback;
        return name.substring(name.lastIndexOf('.'));
    }

    private AppKycSessionResponse startSession(PartnerAppUser user) {
        if (user.getStatus() == PartnerAppKycStatus.BANK_VISIT_REQUIRED
                || Boolean.TRUE.equals(user.getBankVisitRequired())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Phone KYC failed 3 times. Please visit the bank/office for assisted verification.");
        }
        if (user.getStatus() == PartnerAppKycStatus.KYC_COMPLETED) {
            user.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
            appUserRepository.save(user);
            return toSession(user);
        }
        if (user.getStatus() == PartnerAppKycStatus.FAILED || user.getStatus() == PartnerAppKycStatus.INVITED) {
            if (Boolean.TRUE.equals(user.getMobileVerified())) {
                user.setStatus(PartnerAppKycStatus.KYC_IN_PROGRESS);
            }
            // Keep failureReason for display until they progress; clear on successful complete
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
        if (user.getStatus() == PartnerAppKycStatus.BANK_VISIT_REQUIRED
                || Boolean.TRUE.equals(user.getBankVisitRequired())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Phone KYC closed after 3 failures — visit bank/office");
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
                            + " (CNIC front/back and selfie required; fingerprints verified via NADRA)");
        }
        if (isBlank(user.getCnicNumber()) || isBlank(user.getCnicFullName()) || user.getDateOfBirth() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Set CNIC number, full name, and date of birth (PUT /profile) before submit");
        }
        if (user.getVideoVerificationStatus() != VideoVerificationStatus.UPLOADED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Video verification required: POST /video-challenge then POST /video-verification before submit");
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
        docs.add(docItem(DOC_VIDEO_READALOUD, "Video read-aloud",
                user.getVideoVerificationStatus() == VideoVerificationStatus.UPLOADED
                        || uploaded.contains(docCode(user.getId(), DOC_VIDEO_READALOUD))));
        boolean signatureOk = Boolean.TRUE.equals(user.getSignatureUploaded())
                || uploaded.contains(docCode(user.getId(), DOC_SIGNATURE));
        docs.add(docItem(DOC_SIGNATURE, "Signature", signatureOk));

        boolean canSubmit = false;
        try {
            if (user.getStatus() != PartnerAppKycStatus.KYC_COMPLETED
                    && user.getStatus() != PartnerAppKycStatus.BANK_VISIT_REQUIRED
                    && !Boolean.TRUE.equals(user.getBankVisitRequired())
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
        r.setVideoVerificationStatus(user.getVideoVerificationStatus() != null
                ? user.getVideoVerificationStatus().name() : VideoVerificationStatus.NONE.name());
        r.setVideoUploaded(user.getVideoVerificationStatus() == VideoVerificationStatus.UPLOADED);
        r.setBiometricRef(user.getBiometricRef());
        r.setSelfieUploaded(Boolean.TRUE.equals(user.getSelfieUploaded())
                || uploaded.contains(docCode(user.getId(), DOC_SELFIE)));
        r.setSignatureUploaded(signatureOk);
        r.setFailureReason(user.getFailureReason());
        int fails = user.getKycFailCount() != null ? user.getKycFailCount() : 0;
        r.setKycFailCount(fails);
        r.setKycAttemptsRemaining(Math.max(0, PartnerAppUserService.MAX_PHONE_KYC_FAILS - fails));
        r.setBankVisitRequired(Boolean.TRUE.equals(user.getBankVisitRequired())
                || user.getStatus() == PartnerAppKycStatus.BANK_VISIT_REQUIRED);
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
        if (DOC_FINGERS_LEGACY.contains(k) || k.startsWith("FINGER_")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Fingerprint images are not stored; verify via NADRA");
        }
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
