package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "account_portal_roles")
public class AccountPortalRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "portal_role", nullable = false, length = 32)
    private PortalRole portalRole;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public PortalRole getPortalRole() { return portalRole; }
    public void setPortalRole(PortalRole portalRole) { this.portalRole = portalRole; }
    public Instant getCreatedAt() { return createdAt; }
}
