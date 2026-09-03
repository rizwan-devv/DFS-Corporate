package com.dfs.corporate.repository;

import com.dfs.corporate.domain.ApprovalAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, Long> {
    List<ApprovalAction> findByRequestIdOrderByCreatedAtAsc(Long requestId);
}
