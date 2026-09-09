package com.dfs.corporate.repository;

import com.dfs.corporate.domain.PartyWalletBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartyWalletBalanceRepository extends JpaRepository<PartyWalletBalance, Long> {
    Optional<PartyWalletBalance> findByPartyIdAndCurrency(Long partyId, String currency);
}
