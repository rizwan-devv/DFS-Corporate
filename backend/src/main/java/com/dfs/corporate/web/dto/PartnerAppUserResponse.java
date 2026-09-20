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
    private String failureReason;
    private int kycFailCount;
    private int kycAttemptsRemaining;
    private boolean bankVisitRequired;
    private boolean signatureUploaded;
    private String manualKycApproveReason;
    private String manualKycApprovedBy;
    private Instant manualKycApprovedAt;

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
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public int getKycFailCount() { return kycFailCount; }
    public void setKycFailCount(int kycFailCount) { this.kycFailCount = kycFailCount; }
    public int getKycAttemptsRemaining() { return kycAttemptsRemaining; }
    public void setKycAttemptsRemaining(int kycAttemptsRemaining) { this.kycAttemptsRemaining = kycAttemptsRemaining; }
    public boolean isBankVisitRequired() { return bankVisitRequired; }
    public void setBankVisitRequired(boolean bankVisitRequired) { this.bankVisitRequired = bankVisitRequired; }
    public boolean isSignatureUploaded() { return signatureUploaded; }
    public void setSignatureUploaded(boolean signatureUploaded) { this.signatureUploaded = signatureUploaded; }
    public String getManualKycApproveReason() { return manualKycApproveReason; }
    public void setManualKycApproveReason(String manualKycApproveReason) { this.manualKycApproveReason = manualKycApproveReason; }
    public String getManualKycApprovedBy() { return manualKycApprovedBy; }
    public void setManualKycApprovedBy(String manualKycApprovedBy) { this.manualKycApprovedBy = manualKycApprovedBy; }
    public Instant getManualKycApprovedAt() { return manualKycApprovedAt; }
    public void setManualKycApprovedAt(Instant manualKycApprovedAt) { this.manualKycApprovedAt = manualKycApprovedAt; }
}
