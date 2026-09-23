package com.dfs.corporate.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class FranchiseCommissionEntryResponse {
    private String publicId;
    private Long childPartyId;
    private Long parentPartyId;
    private String childBusinessName;
    private String childTrackingId;
    private String inboundRef;
    private String inboundAt;
    private BigDecimal grossAmount;
    private BigDecimal ratePercent;
    private BigDecimal commissionAmount;
    private String status;
    private String dfsAuthId;
    private String errorMessage;
    private Instant postedAt;
    private Instant settledAt;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getChildPartyId() { return childPartyId; }
    public void setChildPartyId(Long childPartyId) { this.childPartyId = childPartyId; }
    public Long getParentPartyId() { return parentPartyId; }
    public void setParentPartyId(Long parentPartyId) { this.parentPartyId = parentPartyId; }
    public String getChildBusinessName() { return childBusinessName; }
    public void setChildBusinessName(String childBusinessName) { this.childBusinessName = childBusinessName; }
    public String getChildTrackingId() { return childTrackingId; }
    public void setChildTrackingId(String childTrackingId) { this.childTrackingId = childTrackingId; }
    public String getInboundRef() { return inboundRef; }
    public void setInboundRef(String inboundRef) { this.inboundRef = inboundRef; }
    public String getInboundAt() { return inboundAt; }
    public void setInboundAt(String inboundAt) { this.inboundAt = inboundAt; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public void setRatePercent(BigDecimal ratePercent) { this.ratePercent = ratePercent; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDfsAuthId() { return dfsAuthId; }
    public void setDfsAuthId(String dfsAuthId) { this.dfsAuthId = dfsAuthId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
}
