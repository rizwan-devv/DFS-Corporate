package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.FranchiseInviteStatus;

import java.time.Instant;

public class FranchiseInviteResponse {
    private Long id;
    private String contactName;
    private String email;
    private String phone;
    private String businessName;
    private String entityType;
    private FranchiseInviteStatus status;
    private String inviteUrl;
    private Instant invitedAt;
    private Instant expiresAt;
    private Instant completedAt;
    private Long childPartyId;
    private String childTrackingId;
    private String childStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public FranchiseInviteStatus getStatus() { return status; }
    public void setStatus(FranchiseInviteStatus status) { this.status = status; }
    public String getInviteUrl() { return inviteUrl; }
    public void setInviteUrl(String inviteUrl) { this.inviteUrl = inviteUrl; }
    public Instant getInvitedAt() { return invitedAt; }
    public void setInvitedAt(Instant invitedAt) { this.invitedAt = invitedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Long getChildPartyId() { return childPartyId; }
    public void setChildPartyId(Long childPartyId) { this.childPartyId = childPartyId; }
    public String getChildTrackingId() { return childTrackingId; }
    public void setChildTrackingId(String childTrackingId) { this.childTrackingId = childTrackingId; }
    public String getChildStatus() { return childStatus; }
    public void setChildStatus(String childStatus) { this.childStatus = childStatus; }
}
