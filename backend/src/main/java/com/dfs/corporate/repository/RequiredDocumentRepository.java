package com.dfs.corporate.repository;

import com.dfs.corporate.domain.PartyType;
import com.dfs.corporate.domain.RequiredDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RequiredDocumentRepository extends JpaRepository<RequiredDocument, Long> {
    List<RequiredDocument> findByPartyTypeOrderByIdAsc(PartyType partyType);
    Optional<RequiredDocument> findByPartyTypeAndDocumentCode(PartyType partyType, String documentCode);
}
