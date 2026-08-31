package com.dfs.corporate.repository;

import com.dfs.corporate.domain.PartyDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PartyDocumentRepository extends JpaRepository<PartyDocument, Long> {
    List<PartyDocument> findByPartyIdOrderByUploadedAtDesc(Long partyId);
    Optional<PartyDocument> findByPartyIdAndDocumentCode(Long partyId, String documentCode);
}
