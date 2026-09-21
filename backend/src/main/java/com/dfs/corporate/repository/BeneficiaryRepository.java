package com.dfs.corporate.repository;

import com.dfs.corporate.domain.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByPartyIdOrderByAliasNameAsc(Long partyId);
    List<Beneficiary> findByPartyIdAndActiveTrueOrderByAliasNameAsc(Long partyId);
    Optional<Beneficiary> findByPublicIdAndPartyId(String publicId, Long partyId);
}
