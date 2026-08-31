package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "partner_invites")
public class PartnerInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_token", nullable = false, unique = true, length = 64)
    private String publicToken;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "associated_person_id", nullable = false)
    private Long associatedPersonId;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartnerInviteStatus status = PartnerInviteStatus.PENDING;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "kyc_payload", columnDefinition = "LONGTEXT")
    private String kycPayload;

    public Long getId() { return id; }
    public String getPublicToken() { return publicToken; }
    public void setPublicToken(String publicToken) { this.publicToken = publicToken; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public Long getAssociatedPersonId() { return associatedPersonId; }
    public void setAssociatedPersonId(Long associatedPersonId) { this.associatedPersonId = associatedPersonId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public PartnerInviteStatus getStatus() { return status; }
    public void setStatus(PartnerInviteStatus status) { this.status = status; }
    public Instant getInvitedAt() { return invitedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getKycPayload() { return kycPayload; }
    public void setKycPayload(String kycPayload) { this.kycPayload = kycPayload; }
}
