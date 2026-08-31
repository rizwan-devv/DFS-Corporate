package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.AssociatedPersonRole;
import com.dfs.corporate.domain.PartnerInviteStatus;

import java.time.Instant;

public class PartnerInviteResponse {
    private Long id;
    private String email;
    private String fullName;
    private AssociatedPersonRole roleType;
    private Boolean authorizedToOperate;
    private PartnerInviteStatus status;
    private Instant invitedAt;
    private Instant completedAt;
    private Instant expiresAt;
    private String inviteUrl;
    private Long associatedPersonId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public AssociatedPersonRole getRoleType() { return roleType; }
    public void setRoleType(AssociatedPersonRole roleType) { this.roleType = roleType; }
    public Boolean getAuthorizedToOperate() { return authorizedToOperate; }
    public void setAuthorizedToOperate(Boolean authorizedToOperate) { this.authorizedToOperate = authorizedToOperate; }
    public PartnerInviteStatus getStatus() { return status; }
    public void setStatus(PartnerInviteStatus status) { this.status = status; }
    public Instant getInvitedAt() { return invitedAt; }
    public void setInvitedAt(Instant invitedAt) { this.invitedAt = invitedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getInviteUrl() { return inviteUrl; }
    public void setInviteUrl(String inviteUrl) { this.inviteUrl = inviteUrl; }
    public Long getAssociatedPersonId() { return associatedPersonId; }
    public void setAssociatedPersonId(Long associatedPersonId) { this.associatedPersonId = associatedPersonId; }
}
