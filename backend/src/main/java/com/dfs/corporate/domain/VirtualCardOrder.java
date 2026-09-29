package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "virtual_card_orders")
public class VirtualCardOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "relationship_num", nullable = false, length = 32)
    private String relationshipNum;

    @Column(name = "emboss_name", nullable = false, length = 40)
    private String embossName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "masked_pan", length = 32)
    private String maskedPan;

    @Column(length = 19)
    private String pan;

    @Column(length = 3)
    private String cvv;

    @Column(length = 4)
    private String last4;

    @Column(length = 8)
    private String expiry;

    @Column(name = "requested_by", length = 200)
    private String requestedBy;

    @Column(name = "decided_by", length = 200)
    private String decidedBy;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public String getRelationshipNum() { return relationshipNum; }
    public void setRelationshipNum(String relationshipNum) { this.relationshipNum = relationshipNum; }
    public String getEmbossName() { return embossName; }
    public void setEmbossName(String embossName) { this.embossName = embossName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMaskedPan() { return maskedPan; }
    public void setMaskedPan(String maskedPan) { this.maskedPan = maskedPan; }
    public String getPan() { return pan; }
    public void setPan(String pan) { this.pan = pan; }
    public String getCvv() { return cvv; }
    public void setCvv(String cvv) { this.cvv = cvv; }
    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
