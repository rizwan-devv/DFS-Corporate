package com.dfs.corporate.repository;

import com.dfs.corporate.domain.Party;
import com.dfs.corporate.domain.PartyStatus;
import com.dfs.corporate.domain.PartyType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {
    Optional<Party> findByPublicId(String publicId);
    Optional<Party> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<Party> findByStatusOrderByCreatedAtDesc(PartyStatus status);
    List<Party> findByPartyTypeAndStatusOrderByCreatedAtDesc(PartyType partyType, PartyStatus status);
    List<Party> findAllByOrderByCreatedAtDesc();
    List<Party> findByBrandIdOrderByCreatedAtDesc(Long brandId);
    List<Party> findByBrandIdAndStatusOrderByCreatedAtDesc(Long brandId, PartyStatus status);
}
