package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AssociatedPersonRepository;
import com.dfs.corporate.repository.PartyDocumentRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.repository.RequiredDocumentRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.AssociatedPersonRequest;
import com.dfs.corporate.web.dto.PartyResponse;
import com.dfs.corporate.web.dto.ProfileUpdateRequest;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OnboardingService {

    private final PartyRepository partyRepository;
    private final PartyDocumentRepository documentRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final AssociatedPersonRepository associatedPersonRepository;
    private final PartnerAppUserService partnerAppUserService;
    private final FileStorageService fileStorageService;

    public OnboardingService(PartyRepository partyRepository,
                             PartyDocumentRepository documentRepository,
                             RequiredDocumentRepository requiredDocumentRepository,
                             AssociatedPersonRepository associatedPersonRepository,
                             PartnerAppUserService partnerAppUserService,
                             FileStorageService fileStorageService) {
        this.partyRepository = partyRepository;
        this.documentRepository = documentRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.associatedPersonRepository = associatedPersonRepository;
        this.partnerAppUserService = partnerAppUserService;
        this.fileStorageService = fileStorageService;
    }

    public PartyResponse me(AccountPrincipal principal) {
        return enrich(getParty(principal));
    }

    @Transactional
    public PartyResponse updateProfile(AccountPrincipal principal, ProfileUpdateRequest req, String clientIp, String userAgent) {
        Party party = getParty(principal);
        assertEditable(party);
        assertCorporateParty(party);

        if (!Boolean.TRUE.equals(req.getTermsAccepted())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Terms & conditions must be accepted");
        }

        party.setFullName(req.getFullName().trim());
        party.setBusinessName(req.getBusinessName().trim());
        party.setEntityType(req.getEntityType());
        if (req.getPartnershipUnregistered() != null) {
            party.setPartnershipUnregistered(req.getPartnershipUnregistered());
        }
        if (req.getApplicantIsPartner() != null) {
            party.setApplicantIsPartner(req.getApplicantIsPartner());
        }
        party.setIncorporationNumber(trim(req.getIncorporationNumber()));
        party.setIncorporationDate(req.getIncorporationDate());
        party.setIncorporationCountry(trim(req.getIncorporationCountry()) != null ? trim(req.getIncorporationCountry()) : "Pakistan");
        party.setIncorporationAuthority(trim(req.getIncorporationAuthority()));
        party.setNtnNumber(trim(req.getNtnNumber()));
        party.setTaxCountry(trim(req.getTaxCountry()) != null ? trim(req.getTaxCountry()) : "Pakistan");
        party.setFatcaCrsDeclared(Boolean.TRUE.equals(req.getFatcaCrsDeclared()));
        party.setFatcaCrsDetails(trim(req.getFatcaCrsDetails()));
        party.setRegisteredAddress(req.getRegisteredAddress().trim());
        party.setMailingAddress(trim(req.getMailingAddress()));
        party.setPlaceOfBusiness(trim(req.getPlaceOfBusiness()));
        party.setAddressDifferenceReason(trim(req.getAddressDifferenceReason()));
        party.setBusinessAddress(party.getPlaceOfBusiness() != null ? party.getPlaceOfBusiness() : party.getRegisteredAddress());
        party.setAddressLine(party.getRegisteredAddress());
        if (req.getCity() != null) party.setCity(req.getCity().trim());
        if (req.getCountry() != null) party.setCountry(req.getCountry().trim());
        else party.setCountry("Pakistan");
        if (req.getPhone() != null && !req.getPhone().isBlank()) party.setPhone(req.getPhone().trim());
        party.setNatureOfBusiness(req.getNatureOfBusiness().trim());
        party.setBusinessLicenseDetails(trim(req.getBusinessLicenseDetails()));
        party.setPurposeOfAccount(req.getPurposeOfAccount().trim());
        party.setIntendedRelationship(trim(req.getIntendedRelationship()));
        party.setTermsAccepted(true);
        party.setTermsAcceptedAt(Instant.now());
        party.setKycTier("ENTITY_CONSOLIDATED");
        party.setClientIp(clientIp);
        party.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
        if (req.getGeoLocation() != null) party.setGeoLocation(req.getGeoLocation());
        if (req.getOnboardingStep() != null) party.setOnboardingStep(req.getOnboardingStep());
        if (req.getRiskRating() != null) party.setRiskRating(req.getRiskRating());
        if (req.getEddRequired() != null) party.setEddRequired(req.getEddRequired());
        if (req.getEddNotes() != null) party.setEddNotes(req.getEddNotes());
        if (req.getVideoKycRef() != null) party.setVideoKycRef(req.getVideoKycRef());

        // Extend draft resume window (Framework §J — up to 30 days)
        party.setDraftExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));

        boolean addressesDiffer = !eq(party.getRegisteredAddress(), party.getMailingAddress())
                || !eq(party.getRegisteredAddress(), party.getPlaceOfBusiness());
        if (addressesDiffer && isBlank(party.getAddressDifferenceReason())
                && !isBlank(party.getMailingAddress()) && !isBlank(party.getPlaceOfBusiness())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide reason when registered / mailing / place of business addresses differ");
        }

        return enrich(partyRepository.save(party));
    }

    @Transactional
    public PartyResponse addAssociatedPerson(AccountPrincipal principal, AssociatedPersonRequest req) {
        Party party = getParty(principal);
        assertEditable(party);
        if (ConsolidatedKycRules.needsPartnerRoster(party.getEntityType())) {
            if (isBlank(req.getPhone()) || isBlank(req.getEmail())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Partner roster requires phone (app user ID) and email");
            }
        }
        AssociatedPerson p = new AssociatedPerson();
        applyPerson(p, party.getId(), req);
        associatedPersonRepository.save(p);
        return enrich(party);
    }

    @Transactional
    public PartyResponse updateAssociatedPerson(AccountPrincipal principal, Long personId, AssociatedPersonRequest req) {
        Party party = getParty(principal);
        assertEditable(party);
        AssociatedPerson p = associatedPersonRepository.findById(personId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Associated person not found"));
        if (!p.getPartyId().equals(party.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Person does not belong to this application");
        }
        applyPerson(p, party.getId(), req);
        associatedPersonRepository.save(p);
        return enrich(party);
    }

    @Transactional
    public PartyResponse removeAssociatedPerson(AccountPrincipal principal, Long personId) {
        Party party = getParty(principal);
        assertEditable(party);
        associatedPersonRepository.deleteByPartyIdAndId(party.getId(), personId);
        return enrich(party);
    }

    @Transactional
    public PartyResponse uploadDocument(AccountPrincipal principal, String documentCode, MultipartFile file) {
        Party party = getParty(principal);
        assertEditable(party);
        List<RequiredDocument> catalog = requiredDocumentRepository.findByPartyTypeOrderByIdAsc(party.getPartyType());
        boolean known = catalog.stream().anyMatch(r -> r.getDocumentCode().equalsIgnoreCase(documentCode));
        boolean partnerSlot = ConsolidatedKycRules.isPartnerUploadCode(documentCode);
        if (!known && !partnerSlot) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown document code for entity KYC (Annex-C)");
        }
        String path = fileStorageService.store(party.getId(), documentCode.toUpperCase(), file);
        PartyDocument doc = documentRepository.findByPartyIdAndDocumentCode(party.getId(), documentCode.toUpperCase())
                .orElseGet(PartyDocument::new);
        doc.setPartyId(party.getId());
        doc.setDocumentCode(documentCode.toUpperCase());
        doc.setOriginalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : documentCode);
        doc.setStoredPath(path);
        doc.setContentType(file.getContentType());
        doc.setStatus(DocumentStatus.PENDING);
        documentRepository.save(doc);
        party.setDraftExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        partyRepository.save(party);
        return enrich(party);
    }

    @Transactional
    public PartyResponse submit(AccountPrincipal principal) {
        Party party = getParty(principal);
        assertEditable(party);
        validateReadyToSubmit(party);

        // Framework §F.4 — sanctions screening stub (manual CLEAR until vendor integrated)
        if (party.getSanctionsStatus() == null || party.getSanctionsStatus() == ScreeningStatus.PENDING) {
            party.setSanctionsStatus(ScreeningStatus.MANUAL_REVIEW);
            party.setSanctionsNotes("Queued for UNSC/ATA sanctions pre-screening (manual until screening API connected)");
            party.setSanctionsScreenedAt(Instant.now());
        }
        for (AssociatedPerson ap : associatedPersonRepository.findByPartyIdOrderByIdAsc(party.getId())) {
            if (ap.getSanctionsStatus() == ScreeningStatus.PENDING) {
                ap.setSanctionsStatus(ScreeningStatus.MANUAL_REVIEW);
                associatedPersonRepository.save(ap);
            }
        }

        // Framework §F.1 — identity verification stub for digital EMI path
        if (party.getIdentityVerificationStatus() == IdentityVerificationStatus.PENDING) {
            party.setIdentityVerificationStatus(IdentityVerificationStatus.DEBIT_BLOCKED);
            party.setIdentityVerificationMethod("PENDING_BV_OR_VERISYS");
        }

        if (ConsolidatedKycRules.needsEdd(party.getRiskRating(), Boolean.TRUE.equals(party.getEddRequired()))) {
            party.setEddRequired(true);
            if (isBlank(party.getVideoKycRef()) && isBlank(party.getEddNotes())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "EDD required for HIGH risk: provide video KYC reference or EDD notes (Framework §G)");
            }
        }

        Instant now = Instant.now();
        party.setSubmittedAt(now);
        party.setDecisionDueAt(addWorkingDays(now, 5)); // Framework §I — entity TAT 5 working days
        party.setStatus(PartyStatus.SUBMITTED);
        party.setRejectionReason(null);
        party.setOnboardingStep(5);
        partyRepository.save(party);

        // Provision mobile-app users + email links/PINs (partners + lead if applicantIsPartner)
        partnerAppUserService.provisionOnSubmit(party);
        partnerAppUserService.tryAdvanceParty(party.getId());

        return enrich(partyRepository.findById(party.getId()).orElse(party));
    }

    public List<RequiredDocument> requiredFor(PartyType type) {
        if (type != PartyType.MERCHANT && type != PartyType.SUB_MERCHANT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only corporate entity types supported");
        }
        return requiredDocumentRepository.findByPartyTypeOrderByIdAsc(type);
    }

    private void validateReadyToSubmit(Party party) {
        if (party.getEntityType() == null
                || isBlank(party.getBusinessName())
                || isBlank(party.getRegisteredAddress())
                || isBlank(party.getNatureOfBusiness())
                || isBlank(party.getPurposeOfAccount())
                || !Boolean.TRUE.equals(party.getTermsAccepted())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Complete entity information (Framework §E Table-B) before submit");
        }

        if (Boolean.TRUE.equals(party.getApplicantIsPartner()) && isBlank(party.getPhone())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Phone required when you are also a partner (used as mobile app user ID)");
        }

        List<AssociatedPerson> persons = associatedPersonRepository.findByPartyIdOrderByIdAsc(party.getId());
        boolean hasOperator = persons.stream().anyMatch(p -> Boolean.TRUE.equals(p.getAuthorizedToOperate()));
        if (!hasOperator && !Boolean.TRUE.equals(party.getApplicantIsPartner())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Add at least one natural person authorized to open/operate the account (Framework §E)");
        }
        if (!hasOperator && Boolean.TRUE.equals(party.getApplicantIsPartner())) {
            // Lead is a partner — treat as operator for portal purposes
        }

        if (ConsolidatedKycRules.needsPartnerRoster(party.getEntityType())) {
            List<AssociatedPerson> partners = persons.stream()
                    .filter(p -> p.getRoleType() == AssociatedPersonRole.PARTNER
                            || p.getRoleType() == AssociatedPersonRole.AUTHORIZED_SIGNATORY
                            || Boolean.TRUE.equals(p.getAuthorizedToOperate()))
                    .toList();
            if (partners.isEmpty() && !Boolean.TRUE.equals(party.getApplicantIsPartner())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Add partner roster (name, phone, email) — partners complete KYC in the mobile app");
            }
            for (AssociatedPerson p : partners) {
                if (isBlank(p.getPhone()) || isBlank(p.getEmail())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Each partner needs phone (app user ID) and email: " + p.getFullName());
                }
            }
        } else {
            BigDecimal boThreshold = ConsolidatedKycRules.needsEdd(party.getRiskRating(), Boolean.TRUE.equals(party.getEddRequired()))
                    ? new BigDecimal("10") : new BigDecimal("20");
            boolean hasBo = persons.stream().anyMatch(p ->
                    p.getRoleType() == AssociatedPersonRole.BENEFICIAL_OWNER
                            || (p.getOwnershipPercent() != null && p.getOwnershipPercent().compareTo(boThreshold) >= 0)
                            || p.getRoleType() == AssociatedPersonRole.SENIOR_MANAGING_OFFICIAL
                            || p.getRoleType() == AssociatedPersonRole.PARTNER);
            if (party.getEntityType() != CorporateEntityType.SOLE_PROPRIETORSHIP && !hasBo) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Declare beneficial owner(s) ≥" + boThreshold + "% or senior managing official (Framework §E)");
            }
            for (AssociatedPerson op : persons.stream().filter(p -> Boolean.TRUE.equals(p.getAuthorizedToOperate())).toList()) {
                if (isBlank(op.getMotherMaidenName()) || isBlank(op.getPlaceOfBirth()) || op.getDateOfBirth() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Authorized operators require mother's maiden name, place of birth and DOB (Framework §E.2)");
                }
            }
        }

        Set<String> uploaded = documentRepository.findByPartyIdOrderByUploadedAtDesc(party.getId()).stream()
                .filter(d -> d.getStatus() != DocumentStatus.REJECTED)
                .map(PartyDocument::getDocumentCode)
                .collect(Collectors.toSet());

        boolean unreg = Boolean.TRUE.equals(party.getPartnershipUnregistered());
        Set<String> mandatory = ConsolidatedKycRules.mandatoryDocuments(party.getPartyType(), party.getEntityType(), unreg);
        List<String> missing = mandatory.stream().filter(c -> !uploaded.contains(c)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Missing Annex-C mandatory documents: " + String.join(", ", missing));
        }

        if (ConsolidatedKycRules.needsPartnerRoster(party.getEntityType())) {
            for (AssociatedPerson p : persons) {
                if (p.getRoleType() != AssociatedPersonRole.PARTNER
                        && p.getRoleType() != AssociatedPersonRole.AUTHORIZED_SIGNATORY
                        && !Boolean.TRUE.equals(p.getAuthorizedToOperate())) {
                    continue;
                }
                List<String> need = List.of(
                        ConsolidatedKycRules.partnerCnicFront(p.getId()),
                        ConsolidatedKycRules.partnerCnicBack(p.getId()),
                        ConsolidatedKycRules.partnerAgreement(p.getId())
                );
                List<String> miss = need.stream().filter(c -> !uploaded.contains(c)).toList();
                if (!miss.isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Upload CNIC front/back + agreement for partner " + p.getFullName()
                                    + " (codes: " + String.join(", ", miss) + ")");
                }
            }
        }

        if (party.getEntityType() == CorporateEntityType.SOLE_PROPRIETORSHIP) {
            boolean anyAlt = ConsolidatedKycRules.solePropAlternatives().stream().anyMatch(uploaded::contains);
            if (!anyAlt) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Sole prop: upload at least one of NTN, trade body, letterhead declaration, or account requisition (Annex-C)");
            }
        }
        if (party.getEntityType() == CorporateEntityType.SMALL_BUSINESS) {
            boolean anyAlt = ConsolidatedKycRules.smallBusinessAlternatives().stream().anyMatch(uploaded::contains);
            if (!anyAlt) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Small business: upload at least one of registration cert, NTN, trade body, or proof of funds (Annex-C)");
            }
        }
    }

    private PartyResponse enrich(Party party) {
        PartyResponse res = PartyResponse.from(party);
        List<PartyDocument> docs = documentRepository.findByPartyIdOrderByUploadedAtDesc(party.getId());
        res.setDocuments(docs.stream().map(PartyResponse.DocumentItem::from).toList());
        res.setAssociatedPersons(associatedPersonRepository.findByPartyIdOrderByIdAsc(party.getId()).stream()
                .map(PartyResponse.AssociatedPersonItem::from).toList());
        res.setPartnerInvites(List.of()); // portal partner KYC invites disabled
        List<com.dfs.corporate.web.dto.PartnerAppUserResponse> appUsers = partnerAppUserService.listForParty(party.getId());
        res.setPartnerAppUsers(appUsers);
        res.setPartnerKycTotal(appUsers.size());
        res.setPartnerKycCompleted((int) appUsers.stream()
                .filter(u -> u.getStatus() == PartnerAppKycStatus.KYC_COMPLETED)
                .count());

        // Dynamic required docs for partner CNIC/agreement slots
        List<AssociatedPerson> persons = associatedPersonRepository.findByPartyIdOrderByIdAsc(party.getId());
        Set<String> uploadedCodes = docs.stream()
                .filter(d -> d.getStatus() != DocumentStatus.REJECTED)
                .map(PartyDocument::getDocumentCode)
                .collect(Collectors.toSet());
        boolean unreg = Boolean.TRUE.equals(party.getPartnershipUnregistered());
        Set<String> mandatory = ConsolidatedKycRules.mandatoryDocuments(party.getPartyType(), party.getEntityType(), unreg);

        List<RequiredDocument> catalog = requiredDocumentRepository.findByPartyTypeOrderByIdAsc(party.getPartyType());
        Set<String> alt = party.getEntityType() == CorporateEntityType.SOLE_PROPRIETORSHIP
                ? ConsolidatedKycRules.solePropAlternatives()
                : (party.getEntityType() == CorporateEntityType.SMALL_BUSINESS
                ? ConsolidatedKycRules.smallBusinessAlternatives() : Set.of());
        List<PartyResponse.RequiredItem> required = new java.util.ArrayList<>(catalog.stream()
                .filter(r -> mandatory.contains(r.getDocumentCode()) || alt.contains(r.getDocumentCode()))
                .map(r -> new PartyResponse.RequiredItem(
                        r.getDocumentCode(), r.getDocumentLabel(),
                        mandatory.contains(r.getDocumentCode()) || alt.contains(r.getDocumentCode()),
                        uploadedCodes.contains(r.getDocumentCode())))
                .toList());
        if (ConsolidatedKycRules.needsPartnerRoster(party.getEntityType())) {
            for (AssociatedPerson p : persons) {
                if (p.getRoleType() != AssociatedPersonRole.PARTNER
                        && p.getRoleType() != AssociatedPersonRole.AUTHORIZED_SIGNATORY
                        && !Boolean.TRUE.equals(p.getAuthorizedToOperate())) {
                    continue;
                }
                String front = ConsolidatedKycRules.partnerCnicFront(p.getId());
                String back = ConsolidatedKycRules.partnerCnicBack(p.getId());
                String agr = ConsolidatedKycRules.partnerAgreement(p.getId());
                required.add(new PartyResponse.RequiredItem(front, "Partner CNIC front — " + p.getFullName(), true, uploadedCodes.contains(front)));
                required.add(new PartyResponse.RequiredItem(back, "Partner CNIC back — " + p.getFullName(), true, uploadedCodes.contains(back)));
                required.add(new PartyResponse.RequiredItem(agr, "Partner agreement — " + p.getFullName(), true, uploadedCodes.contains(agr)));
            }
        }
        res.setRequiredDocuments(required);

        try {
            validateReadyToSubmit(party);
            res.setCanSubmit(party.getStatus() == PartyStatus.DRAFT || party.getStatus() == PartyStatus.REJECTED);
        } catch (ApiException ex) {
            res.setCanSubmit(false);
        }
        return res;
    }

    private void applyPerson(AssociatedPerson p, Long partyId, AssociatedPersonRequest req) {
        p.setPartyId(partyId);
        p.setRoleType(req.getRoleType());
        p.setFullName(req.getFullName().trim());
        p.setFatherOrSpouseName(trim(req.getFatherOrSpouseName()));
        p.setDateOfBirth(req.getDateOfBirth());
        p.setMotherMaidenName(trim(req.getMotherMaidenName()));
        p.setPlaceOfBirth(trim(req.getPlaceOfBirth()));
        p.setIdDocumentType(req.getIdDocumentType());
        p.setIdDocumentNumber(trim(req.getIdDocumentNumber()));
        p.setIdIssueDate(req.getIdIssueDate());
        p.setIdExpiryDate(req.getIdExpiryDate());
        p.setPassportNumber(trim(req.getPassportNumber()));
        p.setPassportCountry(trim(req.getPassportCountry()));
        p.setNationalities(trim(req.getNationalities()));
        p.setTaxResidencies(trim(req.getTaxResidencies()));
        p.setEmail(trim(req.getEmail()));
        p.setPhone(trim(req.getPhone()));
        p.setMailingAddress(trim(req.getMailingAddress()));
        p.setOccupation(trim(req.getOccupation()));
        p.setOwnershipPercent(req.getOwnershipPercent());
        p.setAuthorizedToOperate(Boolean.TRUE.equals(req.getAuthorizedToOperate()));
        p.setFatcaCrsDeclared(Boolean.TRUE.equals(req.getFatcaCrsDeclared()));
        p.setFatcaCrsDetails(trim(req.getFatcaCrsDetails()));
    }

    private Instant addWorkingDays(Instant from, int days) {
        LocalDate d = LocalDate.ofInstant(from, ZoneId.systemDefault());
        int added = 0;
        while (added < days) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Party getParty(AccountPrincipal principal) {
        return partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
    }

    private void assertCorporateParty(Party party) {
        if (party.getPartyType() != PartyType.MERCHANT && party.getPartyType() != PartyType.SUB_MERCHANT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only MERCHANT / SUB_MERCHANT entity accounts");
        }
    }

    private void assertEditable(Party party) {
        if (party.getStatus() != PartyStatus.DRAFT && party.getStatus() != PartyStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Application locked in status " + party.getStatus());
        }
        if (party.getDraftExpiresAt() != null && party.getDraftExpiresAt().isBefore(Instant.now())
                && party.getStatus() == PartyStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Draft expired (30-day resume window). Please start a new application.");
        }
        if (party.getStatus() == PartyStatus.REJECTED) {
            party.setStatus(PartyStatus.DRAFT);
        }
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String trim(String s) { return isBlank(s) ? null : s.trim(); }
    private boolean eq(String a, String b) {
        if (isBlank(a) && isBlank(b)) return true;
        if (isBlank(a) || isBlank(b)) return true; // treat blank as "same / not provided"
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
