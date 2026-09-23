package com.dfs.corporate.repository;

import com.dfs.corporate.domain.EmployeeBulkBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeBulkBatchRepository extends JpaRepository<EmployeeBulkBatch, Long> {
    Optional<EmployeeBulkBatch> findByPublicId(String publicId);
    List<EmployeeBulkBatch> findByPartyIdOrderByCreatedAtDesc(Long partyId);
}
