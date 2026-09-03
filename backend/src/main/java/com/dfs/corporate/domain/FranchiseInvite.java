package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "franchise_invites")
public class FranchiseInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_token", nullable = false, unique = true, length = 64)
    private String publicToken;

    @Column(name = "parent_party_id", nullable = false)
    private Long parentPartyId;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(name = "contact_name", nullable = false, length = 200)
    private String contactName;

    @Column(name = "business_name", length = 200)
    private String businessName;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FranchiseInviteStatus status = FranchiseInviteStatus.PENDING;

    @Column(name = "child_party_id")
    private Long childPartyId;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public Long getId() { return id; }
    public String getPublicToken() { return publicToken; }
    public void setPublicToken(String publicToken) { this.publicToken = publicToken; }
    public Long getParentPartyId() { return parentPartyId; }
    public void setParentPartyId(Long parentPartyId) { this.parentPartyId = parentPartyId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public FranchiseInviteStatus getStatus() { return status; }
    public void setStatus(FranchiseInviteStatus status) { this.status = status; }
    public Long getChildPartyId() { return childPartyId; }
    public void setChildPartyId(Long childPartyId) { this.childPartyId = childPartyId; }
    public Instant getInvitedAt() { return invitedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
