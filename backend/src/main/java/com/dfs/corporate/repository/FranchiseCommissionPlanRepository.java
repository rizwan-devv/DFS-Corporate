package com.dfs.corporate.repository;

import com.dfs.corporate.domain.FranchiseCommissionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FranchiseCommissionPlanRepository extends JpaRepository<FranchiseCommissionPlan, Long> {
    Optional<FranchiseCommissionPlan> findByChildPartyId(Long childPartyId);
    List<FranchiseCommissionPlan> findByParentPartyIdOrderByProposedAtDesc(Long parentPartyId);
}
