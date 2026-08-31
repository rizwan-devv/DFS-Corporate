package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.PartnerAppKycStatus;

import java.time.Instant;

public class PartnerAppUserResponse {
    private Long id;
    private Long associatedPersonId;
    private String phone;
    private String email;
    private String fullName;
    private PartnerAppKycStatus status;
    private String appInviteUrl;
    private Instant invitedAt;
    private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssociatedPersonId() { return associatedPersonId; }
    public void setAssociatedPersonId(Long associatedPersonId) { this.associatedPersonId = associatedPersonId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public PartnerAppKycStatus getStatus() { return status; }
    public void setStatus(PartnerAppKycStatus status) { this.status = status; }
    public String getAppInviteUrl() { return appInviteUrl; }
    public void setAppInviteUrl(String appInviteUrl) { this.appInviteUrl = appInviteUrl; }
    public Instant getInvitedAt() { return invitedAt; }
    public void setInvitedAt(Instant invitedAt) { this.invitedAt = invitedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
