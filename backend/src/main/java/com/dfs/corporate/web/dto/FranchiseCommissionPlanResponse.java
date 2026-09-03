package com.dfs.corporate.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class FranchiseCommissionPlanResponse {
    private Long id;
    private Long childPartyId;
    private Long inviteId;
    private BigDecimal commissionRatePercent;
    private String commissionType;
    private String notes;
    private String status;
    private Instant proposedAt;
    private Instant lockedAt;
    private String lockedBySource;
    private Integer versionNo;
    private String childTrackingId;
    private String childBusinessName;
    private String childStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getProposedAt() { return proposedAt; }
    public void setProposedAt(Instant proposedAt) { this.proposedAt = proposedAt; }
    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public String getLockedBySource() { return lockedBySource; }
    public void setLockedBySource(String lockedBySource) { this.lockedBySource = lockedBySource; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getChildTrackingId() { return childTrackingId; }
    public void setChildTrackingId(String childTrackingId) { this.childTrackingId = childTrackingId; }
    public String getChildBusinessName() { return childBusinessName; }
    public void setChildBusinessName(String childBusinessName) { this.childBusinessName = childBusinessName; }
    public String getChildStatus() { return childStatus; }
    public void setChildStatus(String childStatus) { this.childStatus = childStatus; }
}
