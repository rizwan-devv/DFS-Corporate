package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AssociatedPersonRepository;
import com.dfs.corporate.repository.PartnerInviteRepository;
import com.dfs.corporate.repository.PartyDocumentRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.PartnerInviteCreateRequest;
import com.dfs.corporate.web.dto.PartnerInviteResponse;
import com.dfs.corporate.web.dto.PartnerKycPublicResponse;
import com.dfs.corporate.web.dto.PartnerKycSubmitRequest;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PartnerInviteService {

    private final PartnerInviteRepository inviteRepository;
    private final AssociatedPersonRepository associatedPersonRepository;
    private final PartyRepository partyRepository;
    private final PartyDocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final MailService mailService;
    private final String frontendBaseUrl;

    public PartnerInviteService(PartnerInviteRepository inviteRepository,
                                AssociatedPersonRepository associatedPersonRepository,
                                PartyRepository partyRepository,
                                PartyDocumentRepository documentRepository,
                                FileStorageService fileStorageService,
                                MailService mailService,
                                @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.inviteRepository = inviteRepository;
        this.associatedPersonRepository = associatedPersonRepository;
        this.partyRepository = partyRepository;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.mailService = mailService;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    public List<PartnerInviteResponse> list(AccountPrincipal principal) {
        Party party = getEditableParty(principal);
        return listForParty(party.getId());
    }

    public List<PartnerInviteResponse> listForParty(Long partyId) {
        return inviteRepository.findByPartyIdOrderByInvitedAtDesc(partyId).stream()
                .filter(i -> i.getStatus() != PartnerInviteStatus.CANCELLED)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PartnerInviteResponse create(AccountPrincipal principal, PartnerInviteCreateRequest req) {
        Party party = getEditableParty(principal);
        if (!ConsolidatedKycRules.needsPartnerInvites(party.getEntityType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Partner KYC invites are only for Partnership / LLP applications");
        }

        AssociatedPerson person = new AssociatedPerson();
        person.setPartyId(party.getId());
        person.setRoleType(req.getRoleType() != null ? req.getRoleType() : AssociatedPersonRole.PARTNER);
        person.setFullName(req.getFullName().trim());
        person.setEmail(req.getEmail().trim().toLowerCase());
        person.setAuthorizedToOperate(Boolean.TRUE.equals(req.getAuthorizedToOperate()));
        associatedPersonRepository.save(person);

        PartnerInvite invite = new PartnerInvite();
        invite.setPublicToken(UUID.randomUUID().toString().replace("-", ""));
        invite.setPartyId(party.getId());
        invite.setAssociatedPersonId(person.getId());
        invite.setEmail(person.getEmail());
        invite.setFullName(person.getFullName());
        invite.setStatus(PartnerInviteStatus.PENDING);
        invite.setExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
        inviteRepository.save(invite);

        String url = inviteUrl(invite.getPublicToken());
        mailService.send(invite.getEmail(),
                "DFS Corporate — complete your partner KYC",
                "Hello " + invite.getFullName() + ",\n\n"
                        + "You have been invited as a partner for " + party.getBusinessName() + ".\n"
                        + "Complete your KYC here (link valid 14 days):\n" + url + "\n\n"
                        + "DFS Corporate Onboarding");

        return toResponse(invite);
    }

    @Transactional
    public void cancel(AccountPrincipal principal, Long inviteId) {
        Party party = getEditableParty(principal);
        PartnerInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invite not found"));
        if (!invite.getPartyId().equals(party.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Invite does not belong to this application");
        }
        if (invite.getStatus() == PartnerInviteStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot cancel a completed invite");
        }
        invite.setStatus(PartnerInviteStatus.CANCELLED);
        inviteRepository.save(invite);
    }

    /** Admin / backoffice: re-send partner KYC (or future app) invite email. */
    @Transactional
    public PartnerInviteResponse adminResend(Long inviteId) {
        PartnerInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invite not found"));
        if (invite.getStatus() == PartnerInviteStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invite was cancelled");
        }
        if (invite.getStatus() == PartnerInviteStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Partner KYC already completed");
        }
        Party party = partyRepository.findById(invite.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found"));

        if (invite.getExpiresAt().isBefore(Instant.now()) || invite.getStatus() == PartnerInviteStatus.EXPIRED) {
            invite.setPublicToken(UUID.randomUUID().toString().replace("-", ""));
            invite.setExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
            invite.setStatus(PartnerInviteStatus.PENDING);
        }

        String url = inviteUrl(invite.getPublicToken());
        // Stub for future mobile app deep-link; currently portal partner KYC URL
        mailService.send(invite.getEmail(),
                "DFS Corporate — complete your partner KYC (reminder)",
                "Hello " + invite.getFullName() + ",\n\n"
                        + "Reminder: complete KYC for " + party.getBusinessName() + ".\n"
                        + "App / KYC link (valid 14 days):\n" + url + "\n\n"
                        + "User ID (phone placeholder): use the mobile number registered with your partner record.\n\n"
                        + "DFS Corporate Backoffice");
        inviteRepository.save(invite);
        return toResponse(invite);
    }

    public PartnerKycPublicResponse getByToken(String token) {
        PartnerInvite invite = requireInvite(token);
        return buildPublic(invite);
    }

    @Transactional
    public PartnerKycPublicResponse saveKyc(String token, PartnerKycSubmitRequest req) {
        PartnerInvite invite = requireOpenInvite(token);
        AssociatedPerson person = associatedPersonRepository.findById(invite.getAssociatedPersonId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Partner record missing"));

        person.setFullName(req.getFullName().trim());
        person.setFatherOrSpouseName(trim(req.getFatherOrSpouseName()));
        person.setDateOfBirth(req.getDateOfBirth());
        person.setMotherMaidenName(req.getMotherMaidenName().trim());
        person.setPlaceOfBirth(req.getPlaceOfBirth().trim());
        person.setIdDocumentType(req.getIdDocumentType());
        person.setIdDocumentNumber(req.getIdDocumentNumber().trim());
        person.setIdIssueDate(req.getIdIssueDate());
        person.setIdExpiryDate(req.getIdExpiryDate());
        person.setPhone(trim(req.getPhone()));
        person.setMailingAddress(trim(req.getMailingAddress()));
        person.setNationalities(trim(req.getNationalities()));
        person.setFatcaCrsDeclared(Boolean.TRUE.equals(req.getFatcaCrsDeclared()));
        person.setFatcaCrsDetails(trim(req.getFatcaCrsDetails()));
        associatedPersonRepository.save(person);

        invite.setFullName(person.getFullName());
        invite.setStatus(PartnerInviteStatus.IN_PROGRESS);
        invite.setKycPayload("saved");
        inviteRepository.save(invite);
        return buildPublic(invite);
    }

    @Transactional
    public PartnerKycPublicResponse uploadDoc(String token, String kind, MultipartFile file) {
        PartnerInvite invite = requireOpenInvite(token);
        Long personId = invite.getAssociatedPersonId();
        String code = switch (kind.toUpperCase()) {
            case "ID_FRONT", "FRONT" -> ConsolidatedKycRules.partnerDocFront(personId);
            case "ID_BACK", "BACK" -> ConsolidatedKycRules.partnerDocBack(personId);
            case "PHOTO", "LIVE_PHOTO" -> ConsolidatedKycRules.partnerDocPhoto(personId);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Use kind=ID_FRONT|ID_BACK|PHOTO");
        };

        String path = fileStorageService.store(invite.getPartyId(), code, file);
        PartyDocument doc = documentRepository.findByPartyIdAndDocumentCode(invite.getPartyId(), code)
                .orElseGet(PartyDocument::new);
        doc.setPartyId(invite.getPartyId());
        doc.setDocumentCode(code);
        doc.setOriginalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : code);
        doc.setStoredPath(path);
        doc.setContentType(file.getContentType());
        doc.setStatus(DocumentStatus.PENDING);
        documentRepository.save(doc);

        invite.setStatus(PartnerInviteStatus.IN_PROGRESS);
        inviteRepository.save(invite);
        return buildPublic(invite);
    }

    @Transactional
    public PartnerKycPublicResponse complete(String token) {
        PartnerInvite invite = requireOpenInvite(token);
        AssociatedPerson person = associatedPersonRepository.findById(invite.getAssociatedPersonId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Partner record missing"));

        if (isBlank(person.getFullName()) || person.getDateOfBirth() == null
                || isBlank(person.getMotherMaidenName()) || isBlank(person.getPlaceOfBirth())
                || person.getIdDocumentType() == null || isBlank(person.getIdDocumentNumber())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Complete partner KYC fields before finishing");
        }

        Set<String> uploaded = documentRepository.findByPartyIdOrderByUploadedAtDesc(invite.getPartyId()).stream()
                .filter(d -> d.getStatus() != DocumentStatus.REJECTED)
                .map(PartyDocument::getDocumentCode)
                .collect(Collectors.toSet());
        List<String> needed = List.of(
                ConsolidatedKycRules.partnerDocFront(person.getId()),
                ConsolidatedKycRules.partnerDocBack(person.getId()),
                ConsolidatedKycRules.partnerDocPhoto(person.getId())
        );
        List<String> missing = needed.stream().filter(c -> !uploaded.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Upload ID front, ID back, and live photo");
        }

        invite.setStatus(PartnerInviteStatus.COMPLETED);
        invite.setCompletedAt(Instant.now());
        inviteRepository.save(invite);
        return buildPublic(invite);
    }

    public void assertAllPartnersCompleted(Party party) {
        if (!ConsolidatedKycRules.needsPartnerInvites(party.getEntityType())) return;
        List<PartnerInvite> invites = inviteRepository.findByPartyIdOrderByInvitedAtDesc(party.getId()).stream()
                .filter(i -> i.getStatus() != PartnerInviteStatus.CANCELLED)
                .toList();
        if (invites.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Invite at least one partner via KYC link before submit (Partnership / LLP)");
        }
        long incomplete = invites.stream().filter(i -> i.getStatus() != PartnerInviteStatus.COMPLETED).count();
        if (incomplete > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    incomplete + " partner KYC invite(s) still incomplete — wait for partners to finish their links");
        }
    }

    private PartnerKycPublicResponse buildPublic(PartnerInvite invite) {
        Party party = partyRepository.findById(invite.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found"));
        AssociatedPerson person = associatedPersonRepository.findById(invite.getAssociatedPersonId()).orElse(null);
        boolean expired = invite.getExpiresAt().isBefore(Instant.now())
                || invite.getStatus() == PartnerInviteStatus.EXPIRED
                || invite.getStatus() == PartnerInviteStatus.CANCELLED;

        Set<String> uploaded = documentRepository.findByPartyIdOrderByUploadedAtDesc(invite.getPartyId()).stream()
                .filter(d -> d.getStatus() != DocumentStatus.REJECTED)
                .map(PartyDocument::getDocumentCode)
                .collect(Collectors.toSet());

        Long personId = invite.getAssociatedPersonId();
        List<Map<String, Object>> docs = List.of(
                Map.of("kind", "ID_FRONT", "label", "ID document — Front",
                        "uploaded", uploaded.contains(ConsolidatedKycRules.partnerDocFront(personId))),
                Map.of("kind", "ID_BACK", "label", "ID document — Back",
                        "uploaded", uploaded.contains(ConsolidatedKycRules.partnerDocBack(personId))),
                Map.of("kind", "PHOTO", "label", "Live / digital photograph",
                        "uploaded", uploaded.contains(ConsolidatedKycRules.partnerDocPhoto(personId)))
        );

        PartnerKycPublicResponse res = new PartnerKycPublicResponse();
        res.setBusinessName(party.getBusinessName());
        res.setEntityType(party.getEntityType() != null ? party.getEntityType().name() : null);
        res.setPartnerFullName(invite.getFullName());
        res.setEmail(invite.getEmail());
        res.setStatus(invite.getStatus());
        res.setExpired(expired);
        res.setRequiredDocs(docs);
        if (person != null) {
            res.setIdDocumentType(person.getIdDocumentType());
            res.setIdDocumentNumber(person.getIdDocumentNumber());
            res.setDateOfBirth(person.getDateOfBirth());
            res.setMotherMaidenName(person.getMotherMaidenName());
            res.setPlaceOfBirth(person.getPlaceOfBirth());
            res.setFatherOrSpouseName(person.getFatherOrSpouseName());
            res.setPhone(person.getPhone());
            res.setMailingAddress(person.getMailingAddress());
            res.setNationalities(person.getNationalities());
            res.setFatcaCrsDeclared(person.getFatcaCrsDeclared());
            res.setFatcaCrsDetails(person.getFatcaCrsDetails());
            boolean docsOk = docs.stream().allMatch(d -> Boolean.TRUE.equals(d.get("uploaded")));
            boolean fieldsOk = person.getDateOfBirth() != null
                    && !isBlank(person.getMotherMaidenName())
                    && !isBlank(person.getPlaceOfBirth())
                    && person.getIdDocumentType() != null
                    && !isBlank(person.getIdDocumentNumber());
            res.setCanComplete(!expired && invite.getStatus() != PartnerInviteStatus.COMPLETED && docsOk && fieldsOk);
        }
        return res;
    }

    private PartnerInviteResponse toResponse(PartnerInvite invite) {
        AssociatedPerson person = associatedPersonRepository.findById(invite.getAssociatedPersonId()).orElse(null);
        PartnerInviteResponse r = new PartnerInviteResponse();
        r.setId(invite.getId());
        r.setEmail(invite.getEmail());
        r.setFullName(invite.getFullName());
        r.setStatus(invite.getStatus());
        r.setInvitedAt(invite.getInvitedAt());
        r.setCompletedAt(invite.getCompletedAt());
        r.setExpiresAt(invite.getExpiresAt());
        r.setAssociatedPersonId(invite.getAssociatedPersonId());
        r.setInviteUrl(inviteUrl(invite.getPublicToken()));
        if (person != null) {
            r.setRoleType(person.getRoleType());
            r.setAuthorizedToOperate(person.getAuthorizedToOperate());
        }
        return r;
    }

    private String inviteUrl(String token) {
        return frontendBaseUrl + "/partner-kyc/" + token;
    }

    private PartnerInvite requireInvite(String token) {
        return inviteRepository.findByPublicToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invalid or unknown KYC link"));
    }

    private PartnerInvite requireOpenInvite(String token) {
        PartnerInvite invite = requireInvite(token);
        if (invite.getStatus() == PartnerInviteStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This partner KYC is already completed");
        }
        if (invite.getStatus() == PartnerInviteStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This invite was cancelled");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(PartnerInviteStatus.EXPIRED);
            inviteRepository.save(invite);
            throw new ApiException(HttpStatus.BAD_REQUEST, "This KYC link has expired");
        }
        Party party = partyRepository.findById(invite.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application not found"));
        if (party.getStatus() != PartyStatus.DRAFT && party.getStatus() != PartyStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Application is locked; partner KYC closed");
        }
        return invite;
    }

    private Party getEditableParty(AccountPrincipal principal) {
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.DRAFT && party.getStatus() != PartyStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Application locked in status " + party.getStatus());
        }
        return party;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String trim(String s) { return isBlank(s) ? null : s.trim(); }
}
