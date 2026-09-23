package com.dfs.corporate.repository;

import com.dfs.corporate.domain.FranchiseCommissionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FranchiseCommissionEntryRepository extends JpaRepository<FranchiseCommissionEntry, Long> {
    boolean existsByChildPartyIdAndSourceRef(Long childPartyId, String sourceRef);

    List<FranchiseCommissionEntry> findByParentPartyIdOrderByPostedAtDesc(Long parentPartyId);

    List<FranchiseCommissionEntry> findByChildPartyIdAndStatusIn(Long childPartyId, List<String> statuses);
}
