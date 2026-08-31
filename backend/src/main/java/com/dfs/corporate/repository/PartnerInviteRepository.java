package com.dfs.corporate.repository;

import com.dfs.corporate.domain.PartnerInvite;
import com.dfs.corporate.domain.PartnerInviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerInviteRepository extends JpaRepository<PartnerInvite, Long> {
    Optional<PartnerInvite> findByPublicToken(String publicToken);
    List<PartnerInvite> findByPartyIdOrderByInvitedAtDesc(Long partyId);
    long countByPartyIdAndStatus(Long partyId, PartnerInviteStatus status);
}
