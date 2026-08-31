package com.dfs.corporate.repository;

import com.dfs.corporate.domain.AssociatedPerson;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssociatedPersonRepository extends JpaRepository<AssociatedPerson, Long> {
    List<AssociatedPerson> findByPartyIdOrderByIdAsc(Long partyId);
    void deleteByPartyIdAndId(Long partyId, Long id);
}
