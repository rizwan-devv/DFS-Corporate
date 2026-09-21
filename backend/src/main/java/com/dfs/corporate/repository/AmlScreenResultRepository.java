package com.dfs.corporate.repository;

import com.dfs.corporate.domain.AmlScreenResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AmlScreenResultRepository extends JpaRepository<AmlScreenResult, Long> {
    List<AmlScreenResult> findTop20ByPartyIdOrderByScreenedAtDesc(Long partyId);
}
