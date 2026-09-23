package com.dfs.corporate.repository;

import com.dfs.corporate.domain.LiveBulkRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveBulkRowRepository extends JpaRepository<LiveBulkRow, Long> {
    List<LiveBulkRow> findByBatchIdOrderByLineNoAsc(Long batchId);
}
