package com.dfs.corporate.repository;

import com.dfs.corporate.domain.EmployeeBulkRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeBulkRowRepository extends JpaRepository<EmployeeBulkRow, Long> {
    List<EmployeeBulkRow> findByBatchIdOrderByLineNoAsc(Long batchId);
    Optional<EmployeeBulkRow> findByPublicId(String publicId);
    Optional<EmployeeBulkRow> findByParkRef(String parkRef);
    long countByBatchIdAndStatus(Long batchId, com.dfs.corporate.domain.EmployeeBulkRowStatus status);
}
