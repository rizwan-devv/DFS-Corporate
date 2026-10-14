package com.dfs.corporate.web.dto;

import java.time.Instant;

/** Public view of a pre-approval acknowledgment link (no sensitive internals). */
public class AcknowledgmentPublicResponse {

    private String status;
    private String message;
    private String brandName;
    private String businessName;
    private String trackingId;
    private String emailMasked;
    private Instant expiresAt;
    private Instant acknowledgedAt;
    private boolean canConfirm;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }
    public String getEmailMasked() { return emailMasked; }
    public void setEmailMasked(String emailMasked) { this.emailMasked = emailMasked; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public boolean isCanConfirm() { return canConfirm; }
    public void setCanConfirm(boolean canConfirm) { this.canConfirm = canConfirm; }
}
