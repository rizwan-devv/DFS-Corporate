package com.dfs.corporate.repository;

import com.dfs.corporate.domain.LiveBulkBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveBulkBatchRepository extends JpaRepository<LiveBulkBatch, Long> {
    Optional<LiveBulkBatch> findByPublicId(String publicId);
    List<LiveBulkBatch> findByPartyIdOrderByCreatedAtDesc(Long partyId);
}
