package com.dfs.corporate.service;

import com.dfs.corporate.domain.PartnerAppUser;
import com.dfs.corporate.domain.Party;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.util.IdentityFormats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Keeps party CNIC / CMS relationship in sync for AgentApp-style card inquiry.
 * In this CMS env Relationship # is often the 13-digit CNIC (or a 13-digit DFS account id).
 */
@Service
public class PartyCmsIdentitySync {

    private static final Logger log = LoggerFactory.getLogger(PartyCmsIdentitySync.class);

    private final PartyRepository partyRepository;
    private final PartnerAppUserRepository appUserRepository;

    public PartyCmsIdentitySync(PartyRepository partyRepository,
                                PartnerAppUserRepository appUserRepository) {
        this.partyRepository = partyRepository;
        this.appUserRepository = appUserRepository;
    }

    /**
     * After mobile KYC: copy CNIC onto party; if CMS relationship empty, use CNIC
     * (CMS Relationship # is frequently the CNIC).
     */
    @Transactional
    public void applyKycCnic(Long partyId, String cnicRaw) {
        String cnic = IdentityFormats.cnicDigits(cnicRaw);
        if (partyId == null || cnic == null || cnic.length() < 12) {
            return;
        }
        Party party = partyRepository.findById(partyId).orElse(null);
        if (party == null) {
            return;
        }
        boolean changed = false;
        if (party.getCnicNumber() == null || party.getCnicNumber().isBlank()
                || !DfsWalletIdentityService.isUsableCnic(IdentityFormats.cnicDigits(party.getCnicNumber()))) {
            party.setCnicNumber(cnic);
            changed = true;
        }
        if (party.getCmsRelationshipNum() == null || party.getCmsRelationshipNum().isBlank()) {
            party.setCmsRelationshipNum(cnic);
            party.setCmsRelationshipLinkedAt(Instant.now());
            party.setCmsRelationshipSource("KYC_CNIC");
            changed = true;
            log.info("Auto-linked CMS relationship from KYC CNIC partyId={} rel={}", partyId, cnic);
        }
        if (changed) {
            partyRepository.save(party);
        }
    }

    /**
     * Backfill from completed partner KYC users when party CNIC / relationship still empty.
     */
    @Transactional
    public Party ensureFromPartnerUsers(Party party) {
        if (party == null || party.getId() == null) {
            return party;
        }
        boolean needCnic = party.getCnicNumber() == null || party.getCnicNumber().isBlank();
        boolean needRel = party.getCmsRelationshipNum() == null || party.getCmsRelationshipNum().isBlank();
        if (!needCnic && !needRel) {
            return party;
        }
        List<PartnerAppUser> users = appUserRepository.findByPartyIdOrderByIdAsc(party.getId());
        for (PartnerAppUser u : users) {
            String cnic = IdentityFormats.cnicDigits(u.getCnicNumber());
            if (cnic == null || cnic.length() < 12) {
                continue;
            }
            if (needCnic) {
                party.setCnicNumber(cnic);
                needCnic = false;
            }
            if (needRel) {
                party.setCmsRelationshipNum(cnic);
                party.setCmsRelationshipLinkedAt(Instant.now());
                party.setCmsRelationshipSource("KYC_CNIC_BACKFILL");
                needRel = false;
                log.info("Backfilled CMS relationship from partner KYC partyId={} rel={}", party.getId(), cnic);
            }
            partyRepository.save(party);
            break;
        }
        return party;
    }
}
