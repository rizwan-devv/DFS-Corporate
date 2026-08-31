package com.dfs.corporate.security;

import com.dfs.corporate.domain.Account;
import com.dfs.corporate.domain.AccountStatus;
import com.dfs.corporate.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AccountPrincipal implements UserDetails {

    private final Long accountId;
    private final Long partyId;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final AccountStatus status;

    public AccountPrincipal(Account account) {
        this.accountId = account.getId();
        this.partyId = account.getPartyId();
        this.email = account.getEmail();
        this.passwordHash = account.getPasswordHash() != null ? account.getPasswordHash() : "";
        this.role = account.getRole();
        this.status = account.getStatus();
    }

    public Long getAccountId() { return accountId; }
    public Long getPartyId() { return partyId; }
    public Role getRole() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        // PENDING_VERIFICATION accounts may complete onboarding with OTP JWT
        return status != AccountStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
