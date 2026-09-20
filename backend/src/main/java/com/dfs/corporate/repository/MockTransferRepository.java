package com.dfs.corporate.repository;

import com.dfs.corporate.domain.MockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MockTransferRepository extends JpaRepository<MockTransfer, Long> {
    List<MockTransfer> findByPartyIdOrderByCreatedAtDesc(Long partyId);
    Optional<MockTransfer> findByPublicIdAndPartyId(String publicId, Long partyId);
}
