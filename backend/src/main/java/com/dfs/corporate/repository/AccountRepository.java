package com.dfs.corporate.repository;

import com.dfs.corporate.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByEmailIgnoreCase(String email);
    Optional<Account> findByPartyId(Long partyId);
    boolean existsByEmailIgnoreCase(String email);
}
