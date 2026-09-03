package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.FranchiseInviteStatus;

import java.time.Instant;

/** Public view of an open franchise invite (parent pre-bound). */
public class FranchiseInvitePublicResponse {
    private String token;
    private FranchiseInviteStatus status;
    private boolean usable;
    private String message;
    private String parentBusinessName;
    private String parentTrackingId;
    private String contactName;
    private String email;
    private String phone;
    private String businessName;
    private String entityType;
    private java.math.BigDecimal commissionRatePercent;
    private String commissionType;
    private Instant expiresAt;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public FranchiseInviteStatus getStatus() { return status; }
    public void setStatus(FranchiseInviteStatus status) { this.status = status; }
    public boolean isUsable() { return usable; }
    public void setUsable(boolean usable) { this.usable = usable; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getParentBusinessName() { return parentBusinessName; }
    public void setParentBusinessName(String parentBusinessName) { this.parentBusinessName = parentBusinessName; }
    public String getParentTrackingId() { return parentTrackingId; }
    public void setParentTrackingId(String parentTrackingId) { this.parentTrackingId = parentTrackingId; }
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
    public java.math.BigDecimal getCommissionRatePercent() { return commissionRatePercent; }
    public void setCommissionRatePercent(java.math.BigDecimal commissionRatePercent) {
        this.commissionRatePercent = commissionRatePercent;
    }
    public String getCommissionType() { return commissionType; }
    public void setCommissionType(String commissionType) { this.commissionType = commissionType; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
