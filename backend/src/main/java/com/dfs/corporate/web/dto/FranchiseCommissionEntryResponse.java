package com.dfs.corporate.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class FranchiseCommissionEntryResponse {
    private Long id;
    private String publicId;
    private Long parentPartyId;
    private Long childPartyId;
    private String childTrackingId;
    private String childBusinessName;
    private Long planId;
    private String externalTxnRef;
    private String txnType;
    private String currency;
    private BigDecimal grossAmount;
    private BigDecimal ratePercent;
    private BigDecimal commissionAmount;
    private BigDecimal childNetAmount;
    private String status;
    private String source;
    private String notes;
    private Instant postedAt;
    private Instant reversedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getParentPartyId() { return parentPartyId; }
    public void setParentPartyId(Long parentPartyId) { this.parentPartyId = parentPartyId; }
    public Long getChildPartyId() { return childPartyId; }
    public void setChildPartyId(Long childPartyId) { this.childPartyId = childPartyId; }
    public String getChildTrackingId() { return childTrackingId; }
    public void setChildTrackingId(String childTrackingId) { this.childTrackingId = childTrackingId; }
    public String getChildBusinessName() { return childBusinessName; }
    public void setChildBusinessName(String childBusinessName) { this.childBusinessName = childBusinessName; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getExternalTxnRef() { return externalTxnRef; }
    public void setExternalTxnRef(String externalTxnRef) { this.externalTxnRef = externalTxnRef; }
    public String getTxnType() { return txnType; }
    public void setTxnType(String txnType) { this.txnType = txnType; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public void setRatePercent(BigDecimal ratePercent) { this.ratePercent = ratePercent; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
    public BigDecimal getChildNetAmount() { return childNetAmount; }
    public void setChildNetAmount(BigDecimal childNetAmount) { this.childNetAmount = childNetAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public Instant getReversedAt() { return reversedAt; }
    public void setReversedAt(Instant reversedAt) { this.reversedAt = reversedAt; }
}
