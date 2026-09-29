package com.dfs.corporate.repository;

import com.dfs.corporate.domain.EntityOnboardingDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntityOnboardingDocumentRepository extends JpaRepository<EntityOnboardingDocument, Long> {
    List<EntityOnboardingDocument> findByEntityTypeAndActiveTrueOrderBySortOrderAscIdAsc(String entityType);
    boolean existsByEntityType(String entityType);
    Optional<EntityOnboardingDocument> findByEntityTypeAndDocumentCode(String entityType, String documentCode);
    Optional<EntityOnboardingDocument> findFirstByEntityTypeOrderBySortOrderDesc(String entityType);
}
