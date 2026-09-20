package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AssociatedPersonRepository;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyDocumentRepository;
import com.dfs.corporate.repository.PartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps party status in sync with document completeness after submit.
 * <ul>
 *   <li>INCOMPLETE — required docs missing or any document REJECTED (re-upload only; not full reject)</li>
 *   <li>SUBMITTED — docs OK, waiting on partner mobile KYC</li>
 *   <li>PENDING_APPROVAL — docs OK and all partner KYC completed</li>
 * </ul>
 */
@Service
public class PartyStatusSyncService {

    private final PartyRepository partyRepository;
    private final PartyDocumentRepository documentRepository;
    private final AssociatedPersonRepository associatedPersonRepository;
    private final PartnerAppUserRepository appUserRepository;

    public PartyStatusSyncService(PartyRepository partyRepository,
                                  PartyDocumentRepository documentRepository,
                                  AssociatedPersonRepository associatedPersonRepository,
                                  PartnerAppUserRepository appUserRepository) {
        this.partyRepository = partyRepository;
        this.documentRepository = documentRepository;
        this.associatedPersonRepository = associatedPersonRepository;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Recompute status for post-submit parties. Does not touch DRAFT / ACTIVE / REJECTED / SUSPENDED.
     */
    @Transactional
    public Party syncAfterDocumentChange(Long partyId) {
        Party party = partyRepository.findById(partyId).orElse(null);
        if (party == null) {
            return null;
        }
        return sync(party);
    }

    @Transactional
    public Party sync(Party party) {
        PartyStatus current = party.getStatus();
        if (current != PartyStatus.SUBMITTED
                && current != PartyStatus.PENDING_APPROVAL
                && current != PartyStatus.INCOMPLETE) {
            return party;
        }

        boolean docsIncomplete = hasRejectedOrMissingDocs(party);
        if (docsIncomplete) {
            if (current != PartyStatus.INCOMPLETE) {
                party.setStatus(PartyStatus.INCOMPLETE);
                return partyRepository.save(party);
            }
            return party;
        }

        boolean allKyc = allPartnerKycCompleted(party.getId());
        PartyStatus target = allKyc ? PartyStatus.PENDING_APPROVAL : PartyStatus.SUBMITTED;
        if (current != target) {
            party.setStatus(target);
            return partyRepository.save(party);
        }
        return party;
    }

    public boolean hasRejectedOrMissingDocs(Party party) {
        List<PartyDocument> docs = documentRepository.findByPartyIdOrderByUploadedAtDesc(party.getId());
        if (docs.stream().anyMatch(d -> d.getStatus() == DocumentStatus.REJECTED)) {
            return true;
        }
        Set<String> uploadedOk = docs.stream()
                .filter(d -> d.getStatus() != DocumentStatus.REJECTED)
                .map(PartyDocument::getDocumentCode)
                .collect(Collectors.toSet());

        if (party.getEntityType() == null) {
            return docs.isEmpty();
        }

        boolean unreg = Boolean.TRUE.equals(party.getPartnershipUnregistered());
        Set<String> mandatory = ConsolidatedKycRules.mandatoryDocuments(
                party.getPartyType(), party.getEntityType(), unreg);
        for (String code : mandatory) {
            if (!uploadedOk.contains(code)) {
                return true;
            }
        }

        if (ConsolidatedKycRules.needsPartnerRoster(party.getEntityType())) {
            for (AssociatedPerson p : associatedPersonRepository.findByPartyIdOrderByIdAsc(party.getId())) {
                if (p.getRoleType() != AssociatedPersonRole.PARTNER) {
                    continue;
                }
                List<String> need = List.of(
                        ConsolidatedKycRules.partnerCnicFront(p.getId()),
                        ConsolidatedKycRules.partnerCnicBack(p.getId()),
                        ConsolidatedKycRules.partnerAgreement(p.getId())
                );
                for (String code : need) {
                    if (!uploadedOk.contains(code)) {
                        return true;
                    }
                }
            }
        }

        if (party.getEntityType() == CorporateEntityType.SOLE_PROPRIETORSHIP) {
            boolean anyAlt = ConsolidatedKycRules.solePropAlternatives().stream().anyMatch(uploadedOk::contains);
            if (!anyAlt) {
                return true;
            }
        }
        if (party.getEntityType() == CorporateEntityType.SMALL_BUSINESS) {
            boolean anyAlt = ConsolidatedKycRules.smallBusinessAlternatives().stream().anyMatch(uploadedOk::contains);
            if (!anyAlt) {
                return true;
            }
        }

        return false;
    }

    public boolean allPartnerKycCompleted(Long partyId) {
        List<PartnerAppUser> users = appUserRepository.findByPartyIdOrderByIdAsc(partyId);
        if (users.isEmpty()) {
            return true;
        }
        return users.stream().allMatch(u -> u.getStatus() == PartnerAppKycStatus.KYC_COMPLETED);
    }
}
