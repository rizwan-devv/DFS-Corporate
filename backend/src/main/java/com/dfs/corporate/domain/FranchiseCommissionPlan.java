package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "franchise_commission_plans")
public class FranchiseCommissionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_party_id", nullable = false)
    private Long parentPartyId;

    @Column(name = "child_party_id", nullable = false, unique = true)
    private Long childPartyId;

    @Column(name = "invite_id")
    private Long inviteId;

    @Column(name = "commission_rate_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal commissionRatePercent;

    @Column(name = "commission_type", nullable = false, length = 40)
    private String commissionType = "PERCENT_GROSS";

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CommissionPlanStatus status = CommissionPlanStatus.PROPOSED;

    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt = Instant.now();

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by_account_id")
    private Long lockedByAccountId;

    @Column(name = "locked_by_source", length = 40)
    private String lockedBySource;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    public Long getId() { return id; }
    public Long getParentPartyId() { return parentPartyId; }
    public void setParentPartyId(Long parentPartyId) { this.parentPartyId = parentPartyId; }
    public Long getChildPartyId() { return childPartyId; }
    public void setChildPartyId(Long childPartyId) { this.childPartyId = childPartyId; }
    public Long getInviteId() { return inviteId; }
    public void setInviteId(Long inviteId) { this.inviteId = inviteId; }
    public BigDecimal getCommissionRatePercent() { return commissionRatePercent; }
    public void setCommissionRatePercent(BigDecimal commissionRatePercent) {
        this.commissionRatePercent = commissionRatePercent;
    }
    public String getCommissionType() { return commissionType; }
    public void setCommissionType(String commissionType) { this.commissionType = commissionType; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public CommissionPlanStatus getStatus() { return status; }
    public void setStatus(CommissionPlanStatus status) { this.status = status; }
    public Instant getProposedAt() { return proposedAt; }
    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public Long getLockedByAccountId() { return lockedByAccountId; }
    public void setLockedByAccountId(Long lockedByAccountId) { this.lockedByAccountId = lockedByAccountId; }
    public String getLockedBySource() { return lockedBySource; }
    public void setLockedBySource(String lockedBySource) { this.lockedBySource = lockedBySource; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
}
