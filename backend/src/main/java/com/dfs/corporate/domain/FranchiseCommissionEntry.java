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

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "parent_party_id", nullable = false)
    private Long parentPartyId;

    @Column(name = "child_party_id", nullable = false)
    private Long childPartyId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "external_txn_ref", nullable = false, unique = true, length = 120)
    private String externalTxnRef;

    @Column(name = "txn_type", nullable = false, length = 40)
    private String txnType = "INCOMING";

    @Column(nullable = false, length = 8)
    private String currency = "PKR";

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "rate_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal ratePercent = BigDecimal.ZERO;

    @Column(name = "commission_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "child_net_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal childNetAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CommissionEntryStatus status = CommissionEntryStatus.POSTED;

    @Column(nullable = false, length = 40)
    private String source = "API";

    @Column(length = 500)
    private String notes;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt = Instant.now();

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reverse_of_id")
    private Long reverseOfId;

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getParentPartyId() { return parentPartyId; }
    public void setParentPartyId(Long parentPartyId) { this.parentPartyId = parentPartyId; }
    public Long getChildPartyId() { return childPartyId; }
    public void setChildPartyId(Long childPartyId) { this.childPartyId = childPartyId; }
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
    public CommissionEntryStatus getStatus() { return status; }
    public void setStatus(CommissionEntryStatus status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public Instant getReversedAt() { return reversedAt; }
    public void setReversedAt(Instant reversedAt) { this.reversedAt = reversedAt; }
    public Long getReverseOfId() { return reverseOfId; }
    public void setReverseOfId(Long reverseOfId) { this.reverseOfId = reverseOfId; }
}
