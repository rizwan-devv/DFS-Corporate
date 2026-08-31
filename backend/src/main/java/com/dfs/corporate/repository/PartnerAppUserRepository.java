package com.dfs.corporate.repository;

import com.dfs.corporate.domain.PartnerAppKycStatus;
import com.dfs.corporate.domain.PartnerAppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerAppUserRepository extends JpaRepository<PartnerAppUser, Long> {
    List<PartnerAppUser> findByPartyIdOrderByIdAsc(Long partyId);
    Optional<PartnerAppUser> findByPartyIdAndPhone(Long partyId, String phone);
    Optional<PartnerAppUser> findByAppInviteToken(String token);
    Optional<PartnerAppUser> findBySessionToken(String sessionToken);
    Optional<PartnerAppUser> findByPhoneAndTempPin(String phone, String tempPin);
    Optional<PartnerAppUser> findByEmailIgnoreCase(String email);
    long countByPartyIdAndStatus(Long partyId, PartnerAppKycStatus status);
}
