package com.dfs.corporate.repository;

import com.dfs.corporate.domain.AccountPortalRole;
import com.dfs.corporate.domain.PortalRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountPortalRoleRepository extends JpaRepository<AccountPortalRole, Long> {
    List<AccountPortalRole> findByAccountId(Long accountId);
    boolean existsByAccountIdAndPortalRole(Long accountId, PortalRole portalRole);
    void deleteByAccountId(Long accountId);
}
