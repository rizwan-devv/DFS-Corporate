package com.dfs.corporate.repository;

import com.dfs.corporate.domain.FranchiseCommissionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FranchiseCommissionEntryRepository extends JpaRepository<FranchiseCommissionEntry, Long> {
    Optional<FranchiseCommissionEntry> findByPublicId(String publicId);
    Optional<FranchiseCommissionEntry> findByExternalTxnRef(String externalTxnRef);
    List<FranchiseCommissionEntry> findByParentPartyIdOrderByPostedAtDesc(Long parentPartyId);
    List<FranchiseCommissionEntry> findByChildPartyIdOrderByPostedAtDesc(Long childPartyId);
    boolean existsByReverseOfId(Long reverseOfId);
}
