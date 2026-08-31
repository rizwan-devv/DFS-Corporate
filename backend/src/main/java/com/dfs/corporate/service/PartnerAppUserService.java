package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AssociatedPersonRepository;
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

    private final PartnerAppUserRepository appUserRepository;
    private final AssociatedPersonRepository associatedPersonRepository;
    private final PartyRepository partyRepository;
    private final MailService mailService;
    private final AccountProvisioningService accountProvisioningService;
    private final String mobileAppBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public PartnerAppUserService(PartnerAppUserRepository appUserRepository,
                                 AssociatedPersonRepository associatedPersonRepository,
                                 PartyRepository partyRepository,
                                 MailService mailService,
                                 @Lazy AccountProvisioningService accountProvisioningService,
                                 @Value("${app.mobile-app-base-url:https://app.dfscorporate.local/kyc}") String mobileAppBaseUrl) {
        this.appUserRepository = appUserRepository;
        this.associatedPersonRepository = associatedPersonRepository;
        this.partyRepository = partyRepository;
        this.mailService = mailService;
        this.accountProvisioningService = accountProvisioningService;
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
            for (AssociatedPerson p : persons) {
                if (p.getRoleType() == AssociatedPersonRole.PARTNER
                        || p.getRoleType() == AssociatedPersonRole.AUTHORIZED_SIGNATORY
                        || Boolean.TRUE.equals(p.getAuthorizedToOperate())) {
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
        user.setStatus(PartnerAppKycStatus.KYC_COMPLETED);
        user.setCompletedAt(Instant.now());
        user.setMustChangePassword(false);
        user.setMobileVerified(true);
        appUserRepository.save(user);
        tryAdvanceParty(user.getPartyId());
        accountProvisioningService.provisionAfterKycComplete(user.getPartyId());
        return toResponse(user);
    }

    @Transactional
    public void tryAdvanceParty(Long partyId) {
        Party party = partyRepository.findById(partyId).orElse(null);
        if (party == null || party.getStatus() != PartyStatus.SUBMITTED) return;
        List<PartnerAppUser> users = appUserRepository.findByPartyIdOrderByIdAsc(partyId);
        // No app KYC invitees (e.g. sole prop without applicant-is-partner) → ready for backoffice
        boolean allDone = users.isEmpty()
                || users.stream().allMatch(u -> u.getStatus() == PartnerAppKycStatus.KYC_COMPLETED);
        if (allDone) {
            party.setStatus(PartyStatus.PENDING_APPROVAL);
            partyRepository.save(party);
        }
    }

    public boolean allKycCompleted(Long partyId) {
        List<PartnerAppUser> users = appUserRepository.findByPartyIdOrderByIdAsc(partyId);
        if (users.isEmpty()) return true;
        return users.stream().allMatch(u -> u.getStatus() == PartnerAppKycStatus.KYC_COMPLETED);
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
        return r;
    }

    private String generatePin() {
        int n = 100000 + random.nextInt(900000);
        return String.valueOf(n);
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9+]", "");
    }
}
