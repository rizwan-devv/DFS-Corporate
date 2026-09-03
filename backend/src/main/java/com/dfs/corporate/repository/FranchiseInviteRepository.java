package com.dfs.corporate.repository;

import com.dfs.corporate.domain.FranchiseInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FranchiseInviteRepository extends JpaRepository<FranchiseInvite, Long> {
    Optional<FranchiseInvite> findByPublicToken(String publicToken);
    List<FranchiseInvite> findByParentPartyIdOrderByInvitedAtDesc(Long parentPartyId);
}
