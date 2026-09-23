package com.dfs.corporate.service;

import com.dfs.corporate.domain.PartnerAppUser;
import com.dfs.corporate.domain.Party;
import com.dfs.corporate.integration.dfs.CorporatePortalAppClient;
import com.dfs.corporate.integration.dfs.DfsAccountCreateResult;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.util.IdentityFormats;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persist DFS wallet identity (APP_USER_ID + NID) so corporate FT / commission
 * do not fall back to local partner_app_users.id.
 */
@Service
public class DfsWalletIdentityService {

    private static final Logger log = LoggerFactory.getLogger(DfsWalletIdentityService.class);

    private final PartyRepository partyRepository;
    private final PartnerAppUserRepository partnerAppUserRepository;
    private final CorporatePortalAppClient appClient;

    public DfsWalletIdentityService(PartyRepository partyRepository,
                                    PartnerAppUserRepository partnerAppUserRepository,
                                    CorporatePortalAppClient appClient) {
        this.partyRepository = partyRepository;
        this.partnerAppUserRepository = partnerAppUserRepository;
        this.appClient = appClient;
    }

    @Transactional
    public Party applyCreateResult(Party party, DfsAccountCreateResult result) {
        if (party == null || result == null || !result.success()) {
            return party;
        }
        boolean changed = applyFields(party, result.dfsAppUserId(), result.nidNo(), result.dfsAccountId());
        copyWalletPinFromPartners(party);
        if (changed) {
            party = partyRepository.save(party);
        }
        return party;
    }

    /**
     * Pull nidNo / appUserId / accountNo from DFS accountDetails (live wallets).
     */
    @Transactional
    public Party refreshFromDfs(Party party) {
        if (party == null || party.getId() == null) {
            return party;
        }
        copyWalletPinFromPartners(party);
        String mobile = IdentityFormats.phoneDigits(party.getPhone());
        if (mobile == null || mobile.length() < 10 || !appClient.isEnabled()) {
            return partyRepository.save(party);
        }
        try {
            JsonNode root = appClient.accountDetails(mobile);
            JsonNode data = root != null && root.has("data") && !root.get("data").isNull()
                    ? root.get("data") : root;
            String nid = firstText(data, "nidNo", "cnic", "cnicNumber", "nationalId");
            String appUserId = firstText(data,
                    "appUserId", "customerAppUserId", "APP_USER_ID", "userId", "app_user_id");
            String accountNo = firstText(data, "accountNo", "accountNumber", "mobileNo", "mobileNumber");
            if (applyFields(party, appUserId, nid, accountNo)) {
                log.info("DFS identity synced partyId={} mobile={} appUserId={} nid={}",
                        party.getId(), mobile, party.getDfsAppUserId(), party.getCnicNumber());
            }
        } catch (Exception ex) {
            log.warn("DFS accountDetails identity sync failed partyId={}: {}", party.getId(), ex.getMessage());
        }
        return partyRepository.save(party);
    }

    private boolean applyFields(Party party, String appUserId, String nid, String accountNo) {
        boolean changed = false;
        String uid = cleanAppUserId(appUserId);
        if (uid != null && (party.getDfsAppUserId() == null || party.getDfsAppUserId().isBlank())) {
            party.setDfsAppUserId(uid);
            changed = true;
        } else if (uid != null && !uid.equals(party.getDfsAppUserId())) {
            party.setDfsAppUserId(uid);
            changed = true;
        }
        String cnic = IdentityFormats.cnicDigits(nid);
        if (isUsableCnic(cnic) && !cnic.equals(IdentityFormats.cnicDigits(party.getCnicNumber()))) {
            party.setCnicNumber(cnic);
            changed = true;
        }
        if (accountNo != null && !accountNo.isBlank()
                && (party.getDfsAccountId() == null || party.getDfsAccountId().isBlank()
                || party.getDfsAccountId().startsWith("DFS-"))) {
            party.setDfsAccountId(accountNo.trim());
            changed = true;
        }
        return changed;
    }

    private void copyWalletPinFromPartners(Party party) {
        if (party.getWalletPin() != null && !party.getWalletPin().isBlank()) {
            return;
        }
        List<PartnerAppUser> users = partnerAppUserRepository.findByPartyIdOrderByIdAsc(party.getId());
        for (PartnerAppUser u : users) {
            String pin = IdentityFormats.pinPlain(u.getWalletPin());
            if (pin != null && pin.length() >= 4) {
                party.setWalletPin(pin);
                return;
            }
        }
    }

    /** DFS APP_USER_ID only — never a guessed local PK. */
    public static String requireDfsAppUserId(Party party) {
        if (party == null) return null;
        return cleanAppUserId(party.getDfsAppUserId());
    }

    public static boolean isUsableCnic(String cnic) {
        if (cnic == null || cnic.length() != 13 || !cnic.matches("\\d{13}")) return false;
        return !"0000000000000".equals(cnic);
    }

    private static String cleanAppUserId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        if (t.matches("\\d{1,12}")) return t;
        return t.length() <= 32 ? t : null;
    }

    private static String firstText(JsonNode n, String... keys) {
        if (n == null || n.isNull()) return null;
        for (String k : keys) {
            if (n.has(k) && !n.get(k).isNull()) {
                String v = n.get(k).asText(null);
                if (v != null && !v.isBlank()) return v.trim();
            }
        }
        return null;
    }
}
