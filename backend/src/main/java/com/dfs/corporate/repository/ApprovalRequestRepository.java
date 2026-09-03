package com.dfs.corporate.repository;

import com.dfs.corporate.domain.ApprovalRequest;
import com.dfs.corporate.domain.ApprovalRequestStatus;
import com.dfs.corporate.domain.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    Optional<ApprovalRequest> findByPublicId(String publicId);
    List<ApprovalRequest> findByPartyIdOrderByCreatedAtDesc(Long partyId);
    List<ApprovalRequest> findByPartyIdAndStatusAndCurrentStepOrderByCreatedAtAsc(
            Long partyId, ApprovalRequestStatus status, ApprovalStep currentStep);
}
