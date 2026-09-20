package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AssociatedPersonRepository;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.web.dto.PartnerAppUserResponse;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PartnerAppUserService {

    public static final int MAX_PHONE_KYC_FAILS = 3;

    private final PartnerAppUserRepository appUserRepository;
    private final AssociatedPersonRepository associatedPersonRepository;
    private final PartyRepository partyRepository;
    private final MailService mailService;
    private final AccountProvisioningService accountProvisioningService;
    private final PartyStatusSyncService partyStatusSyncService;
    private final String mobileAppBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public PartnerAppUserService(PartnerAppUserRepository appUserRepository,
                                 AssociatedPersonRepository associatedPersonRepository,
                                 PartyRepository partyRepository,
                                 MailService mailService,
                                 @Lazy AccountProvisioningService accountProvisioningService,
                                 PartyStatusSyncService partyStatusSyncService,
                                 @Value("${app.mobile-app-base-url:https://app.dfscorporate.local/kyc}") String mobileAppBaseUrl) {
        this.appUserRepository = appUserRepository;
        this.associatedPersonRepository = associatedPersonRepository;
        this.partyRepository = partyRepository;
        this.mailService = mailService;
        this.accountProvisioningService = accountProvisioningService;
        this.partyStatusSyncService = partyStatusSyncService;
        this.mobileAppBaseUrl = mobileAppBaseUrl.endsWith("/")
                ? mobileAppBaseUrl.substring(0, mobileAppBaseUrl.length() - 1)
                : mobileAppBaseUrl;
    }

    public List<PartnerAppUserResponse> listForParty(Long partyId) {
        return appUserRepository.findByPartyIdOrderByIdAsc(partyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<PartnerAppUserResponse> provisionOnSubmit(Party party) {
        List<AssociatedPerson> persons = associatedPersonRepository.findByPartyIdOrderByIdAsc(party.getId());
        List<PartnerAppUser> created = new ArrayList<>();

        if (ConsolidatedKycRules.needsPartnerRoster(party.getEntityType())) {
            // Only PARTNER roster entries — each partner self-KYCs in the app (no authorized-person invites)
            for (AssociatedPerson p : persons) {
                if (p.getRoleType() == AssociatedPersonRole.PARTNER) {
                    created.add(upsertUser(party, p.getId(), p.getFullName(), p.getPhone(), p.getEmail()));
                }
            }
        }

        if (Boolean.TRUE.equals(party.getApplicantIsPartner())) {
            boolean leadAlready = created.stream()
                    .anyMatch(u -> normalizePhone(u.getPhone()).equals(normalizePhone(party.getPhone())));
            if (!leadAlready) {
                created.add(upsertUser(party, null, party.getFullName(), party.getPhone(), party.getEmail()));
            }
        }

        if (created.isEmpty() && ConsolidatedKycRules.needsPartnerRoster(party.getEntityType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No partners with phone numbers to invite to the KYC app");
        }

        for (PartnerAppUser u : created) {
            sendInviteEmail(party, u);
        }
        return created.stream().map(this::toResponse).toList();
    }

    @Transactional
    public PartnerAppUserResponse resend(Long appUserId) {
        PartnerAppUser user = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "App user not found"));
        if (user.getStatus() == PartnerAppKycStatus.KYC_COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Partner app KYC already completed");
        }
        user.setTempPin(generatePin());
        user.setAppInviteToken(UUID.randomUUID().toString().replace("-", ""));
        user.setStatus(PartnerAppKycStatus.INVITED);
        appUserRepository.save(user);
        Party party = partyRepository.findById(user.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        sendInviteEmail(party, user);
        return toResponse(user);
    }

    /**
     * Stub until mobile app exists — mark partner KYC complete and maybe advance party.
     * Does not call DFS Account API (that runs on admin approve).
     */
    @Transactional
    public PartnerAppUserResponse markKycCompleted(Long appUserId) {
        PartnerAppUser user = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "App user not found"));
        if (Boolean.TRUE.equals(user.getBankVisitRequired())
                || user.getStatus() == PartnerAppKycStatus.BANK_VISIT_REQUIRED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Partner requires bank visit — use manual KYC approve with a written reason");
        }
        user.setStatus(PartnerAppKycStatus.KYC_COMPLETED);
        user.setCompletedAt(Instant.now());
        user.setMustChangePassword(false);
        user.setMobileVerified(true);
        user.setFailureReason(null);
        appUserRepository.save(user);
        tryAdvanceParty(user.getPartyId());
        accountProvisioningService.provisionAfterKycComplete(user.getPartyId());
        return toResponse(user);
    }

    /**
     * After 3 phone KYC failures: backoffice may complete this partner only, with reason (bank visit).
     */
    @Transactional
    public PartnerAppUserResponse manualKycApprove(Long appUserId, String reason, String approvedBy) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Manual KYC approve reason is required");
        }
        PartnerAppUser user = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "App user not found"));
        if (user.getStatus() == PartnerAppKycStatus.KYC_COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Partner app KYC already completed");
        }
        user.setStatus(PartnerAppKycStatus.KYC_COMPLETED);
        user.setCompletedAt(Instant.now());
        user.setBankVisitRequired(false);
        user.setFailureReason(null);
        user.setMustChangePassword(false);
        user.setMobileVerified(true);
        user.setManualKycApproveReason(reason.trim());
        user.setManualKycApprovedBy(approvedBy != null ? approvedBy : "BACKOFFICE");
        user.setManualKycApprovedAt(Instant.now());
        appUserRepository.save(user);
        tryAdvanceParty(user.getPartyId());
        accountProvisioningService.provisionAfterKycComplete(user.getPartyId());

        Party party = partyRepository.findById(user.getPartyId()).orElse(null);
        String to = user.getEmail() != null ? user.getEmail() : (party != null ? party.getEmail() : null);
        if (to != null) {
            mailService.send(to, "DFS Corporate — KYC approved at bank/office",
                    "Hello " + user.getFullName() + ",\n\n"
                            + "Your partner KYC was completed after bank/office verification.\n"
                            + "Reason on file: " + user.getManualKycApproveReason() + "\n\n"
                            + "— DFS Corporate");
        }
        return toResponse(user);
    }

    @Transactional
    public void tryAdvanceParty(Long partyId) {
        Party party = partyRepository.findById(partyId).orElse(null);
        if (party == null) {
            return;
        }
        // Doc reject / missing → INCOMPLETE; else SUBMITTED or PENDING_APPROVAL
        if (party.getStatus() == PartyStatus.SUBMITTED
                || party.getStatus() == PartyStatus.PENDING_APPROVAL
                || party.getStatus() == PartyStatus.INCOMPLETE) {
            partyStatusSyncService.sync(party);
        }
    }

    public boolean allKycCompleted(Long partyId) {
        return partyStatusSyncService.allPartnerKycCompleted(partyId);
    }

    private PartnerAppUser upsertUser(Party party, Long personId, String fullName, String phone, String email) {
        if (phone == null || phone.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Partner phone (app user ID) is required: " + fullName);
        }
        String normalized = normalizePhone(phone);
        PartnerAppUser user = appUserRepository.findByPartyIdAndPhone(party.getId(), normalized)
                .orElseGet(PartnerAppUser::new);
        user.setPartyId(party.getId());
        user.setAssociatedPersonId(personId);
        user.setFullName(fullName != null ? fullName.trim() : "Partner");
        user.setPhone(normalized);
        user.setEmail(email != null ? email.trim().toLowerCase() : party.getEmail());
        if (user.getTempPin() == null || user.getStatus() == PartnerAppKycStatus.INVITED) {
            user.setTempPin(generatePin());
        }
        if (user.getAppInviteToken() == null) {
            user.setAppInviteToken(UUID.randomUUID().toString().replace("-", ""));
        }
        user.setStatus(PartnerAppKycStatus.INVITED);
        return appUserRepository.save(user);
    }

    private void sendInviteEmail(Party party, PartnerAppUser user) {
        String link = mobileAppBaseUrl + "?token=" + user.getAppInviteToken();
        String to = user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : party.getEmail();
        mailService.send(to,
                "DFS Corporate — complete KYC in the mobile app",
                "Hello " + user.getFullName() + ",\n\n"
                        + "You are invited to complete biometric / video KYC for "
                        + party.getBusinessName() + ".\n\n"
                        + "Mobile app link (opens KycApp):\n" + link + "\n\n"
                        + "User ID (phone): " + user.getPhone() + "\n"
                        + "Temporary PIN: " + user.getTempPin() + "\n\n"
                        + "Complete biometric / OCR KYC in the DFS Corporate KycApp.\n\n"
                        + "— DFS Corporate");
    }

    private PartnerAppUserResponse toResponse(PartnerAppUser u) {
        PartnerAppUserResponse r = new PartnerAppUserResponse();
        r.setId(u.getId());
        r.setAssociatedPersonId(u.getAssociatedPersonId());
        r.setPhone(u.getPhone());
        r.setEmail(u.getEmail());
        r.setFullName(u.getFullName());
        r.setStatus(u.getStatus());
        r.setAppInviteUrl(mobileAppBaseUrl + "?token=" + u.getAppInviteToken());
        r.setInvitedAt(u.getInvitedAt());
        r.setCompletedAt(u.getCompletedAt());
        r.setFailureReason(u.getFailureReason());
        r.setKycFailCount(u.getKycFailCount() != null ? u.getKycFailCount() : 0);
        r.setKycAttemptsRemaining(Math.max(0, MAX_PHONE_KYC_FAILS - (u.getKycFailCount() != null ? u.getKycFailCount() : 0)));
        r.setBankVisitRequired(Boolean.TRUE.equals(u.getBankVisitRequired())
                || u.getStatus() == PartnerAppKycStatus.BANK_VISIT_REQUIRED);
        r.setSignatureUploaded(Boolean.TRUE.equals(u.getSignatureUploaded()));
        r.setManualKycApproveReason(u.getManualKycApproveReason());
        r.setManualKycApprovedBy(u.getManualKycApprovedBy());
        r.setManualKycApprovedAt(u.getManualKycApprovedAt());
        return r;
    }

    private String generatePin() {
        int n = 100000 + random.nextInt(900000);
        return String.valueOf(n);
    }

    private String normalizePhone(String phone) {
        String digits = IdentityFormats.phoneDigits(phone);
        return digits != null ? digits : "";
    }
}
