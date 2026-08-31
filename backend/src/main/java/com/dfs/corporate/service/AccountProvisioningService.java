package com.dfs.corporate.service;

import com.dfs.corporate.domain.AccountProvisionStatus;
import com.dfs.corporate.domain.PartnerAppKycStatus;
import com.dfs.corporate.domain.PartnerAppUser;
import com.dfs.corporate.domain.Party;
import com.dfs.corporate.domain.PartyStatus;
import com.dfs.corporate.integration.dfs.DfsAccountClient;
import com.dfs.corporate.integration.dfs.DfsAccountCreateResult;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AccountProvisioningService {

    private final PartyRepository partyRepository;
    private final PartnerAppUserRepository appUserRepository;
    private final DfsAccountClient dfsAccountClient;

    public AccountProvisioningService(PartyRepository partyRepository,
                                      PartnerAppUserRepository appUserRepository,
                                      DfsAccountClient dfsAccountClient) {
        this.partyRepository = partyRepository;
        this.appUserRepository = appUserRepository;
        this.dfsAccountClient = dfsAccountClient;
    }

    @Transactional
    public Party provisionAfterApprove(Long partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Party must be ACTIVE before account provisioning");
        }
        if (party.getAccountProvisionStatus() == AccountProvisionStatus.SUCCESS) {
            return party;
        }
        return runAttempt(party);
    }

    /**
     * Last partner KYC complete → call DFS backend corporateonboarding.
     */
    @Transactional
    public Party provisionAfterKycComplete(Long partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (!allKycCompleted(partyId)) {
            return party;
        }
        if (party.getAccountProvisionStatus() == AccountProvisionStatus.SUCCESS) {
            return party;
        }
        if (party.getStatus() != PartyStatus.PENDING_APPROVAL
                && party.getStatus() != PartyStatus.ACTIVE
                && party.getStatus() != PartyStatus.SUBMITTED) {
            return party;
        }
        if (party.getStatus() == PartyStatus.SUBMITTED) {
            party.setStatus(PartyStatus.PENDING_APPROVAL);
            partyRepository.save(party);
        }
        return runAttempt(party);
    }

    @Transactional
    public Party retry(Long partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getAccountProvisionStatus() == AccountProvisionStatus.SUCCESS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DFS account already provisioned");
        }
        if (party.getStatus() != PartyStatus.ACTIVE
                && party.getStatus() != PartyStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Retry allowed when party is PENDING_APPROVAL or ACTIVE");
        }
        if (!allKycCompleted(partyId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "All partner KYCs must be complete before retry");
        }
        return runAttempt(party);
    }

    private boolean allKycCompleted(Long partyId) {
        List<PartnerAppUser> users = appUserRepository.findByPartyIdOrderByIdAsc(partyId);
        if (users.isEmpty()) return true;
        return users.stream().allMatch(u -> u.getStatus() == PartnerAppKycStatus.KYC_COMPLETED);
    }

    private Party runAttempt(Party party) {
        party.setAccountProvisionStatus(AccountProvisionStatus.PENDING);
        party.setAccountProvisionError(null);
        party.setAccountProvisionAttempts(
                (party.getAccountProvisionAttempts() == null ? 0 : party.getAccountProvisionAttempts()) + 1);
        party.setAccountProvisionLastAttemptAt(Instant.now());
        partyRepository.save(party);

        DfsAccountCreateResult result = dfsAccountClient.createAccount(party);

        if (result.success()) {
            party.setAccountProvisionStatus(AccountProvisionStatus.SUCCESS);
            party.setDfsAccountId(result.dfsAccountId());
            party.setAccountProvisionedAt(Instant.now());
            party.setAccountProvisionError(null);
        } else if (result.deferred()) {
            party.setAccountProvisionStatus(AccountProvisionStatus.PENDING);
            party.setAccountProvisionError(result.errorMessage());
        } else {
            party.setAccountProvisionStatus(AccountProvisionStatus.FAILED);
            party.setAccountProvisionError(result.errorMessage());
        }
        return partyRepository.save(party);
    }
}
