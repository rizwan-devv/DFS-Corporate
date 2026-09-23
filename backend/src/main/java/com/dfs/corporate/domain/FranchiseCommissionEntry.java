package com.dfs.corporate.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "franchise_commission_entries")
public class FranchiseCommissionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36)
    private String publicId;

    @Column(name = "parent_party_id", nullable = false)
    private Long parentPartyId;

    @Column(name = "child_party_id", nullable = false)
    private Long childPartyId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "source_ref", nullable = false, length = 190)
    private String sourceRef;

    @Column(name = "inbound_ref", length = 120)
    private String inboundRef;

    @Column(name = "inbound_at", length = 64)
    private String inboundAt;

    @Column(nullable = false, length = 8)
    private String currency = "PKR";

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "rate_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal ratePercent;

    @Column(name = "commission_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal commissionAmount;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "dfs_auth_id", length = 80)
    private String dfsAuthId;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt = Instant.now();

    @Column(name = "settled_at")
    private Instant settledAt;

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getParentPartyId() { return parentPartyId; }
    public void setParentPartyId(Long parentPartyId) { this.parentPartyId = parentPartyId; }
    public Long getChildPartyId() { return childPartyId; }
    public void setChildPartyId(Long childPartyId) { this.childPartyId = childPartyId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getInboundRef() { return inboundRef; }
    public void setInboundRef(String inboundRef) { this.inboundRef = inboundRef; }
    public String getInboundAt() { return inboundAt; }
    public void setInboundAt(String inboundAt) { this.inboundAt = inboundAt; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
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
