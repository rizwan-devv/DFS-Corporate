package com.dfs.corporate.web.dto;

/** Summary of a child franchise under a master corporate. */
public class FranchiseChildSummary {
    private Long id;
    private String publicId;
    private String trackingId;
    private String businessName;
    private String fullName;
    private String email;
    private String phone;
    private String status;
    private String partyType;
    private String entityType;
    private java.math.BigDecimal commissionRatePercent;
    private String commissionStatus;
    private String commissionType;
    private String dfsAccountId;
    private String levelCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPartyType() { return partyType; }
    public void setPartyType(String partyType) { this.partyType = partyType; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public java.math.BigDecimal getCommissionRatePercent() { return commissionRatePercent; }
    public void setCommissionRatePercent(java.math.BigDecimal commissionRatePercent) {
        this.commissionRatePercent = commissionRatePercent;
    }
    public String getCommissionStatus() { return commissionStatus; }
    public void setCommissionStatus(String commissionStatus) { this.commissionStatus = commissionStatus; }
    public String getCommissionType() { return commissionType; }
    public void setCommissionType(String commissionType) { this.commissionType = commissionType; }
    public String getDfsAccountId() { return dfsAccountId; }
    public void setDfsAccountId(String dfsAccountId) { this.dfsAccountId = dfsAccountId; }
    public String getLevelCode() { return levelCode; }
    public void setLevelCode(String levelCode) { this.levelCode = levelCode; }
}
