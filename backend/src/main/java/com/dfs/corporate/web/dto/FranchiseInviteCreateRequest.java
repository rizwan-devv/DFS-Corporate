package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class FranchiseInviteCreateRequest {
    @NotBlank
    private String contactName;
    @NotBlank @Email
    private String email;
    @NotBlank
    private String phone;
    private String businessName;
    /** Optional: SOLE_PROPRIETORSHIP, PARTNERSHIP, etc. */
    private String entityType;
    /** Proposed franchise commission % (locked on approve). */
    private java.math.BigDecimal commissionRatePercent;
    /** Default PERCENT_GROSS */
    private String commissionType;
    private String commissionNotes;

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
    public String getCommissionNotes() { return commissionNotes; }
    public void setCommissionNotes(String commissionNotes) { this.commissionNotes = commissionNotes; }
}
