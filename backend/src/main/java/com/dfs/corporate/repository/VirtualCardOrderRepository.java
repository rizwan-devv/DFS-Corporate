package com.dfs.corporate.repository;

import com.dfs.corporate.domain.VirtualCardOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VirtualCardOrderRepository extends JpaRepository<VirtualCardOrder, Long> {
    List<VirtualCardOrder> findByPartyIdOrderByIdDesc(Long partyId);
    List<VirtualCardOrder> findByStatusOrderByCreatedAtAsc(String status);
    Optional<VirtualCardOrder> findByPublicId(String publicId);
    boolean existsByPartyIdAndStatusIn(Long partyId, List<String> statuses);
}
