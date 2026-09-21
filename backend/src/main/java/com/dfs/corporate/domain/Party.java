package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
// LocalDate used for DOB / CNIC issue date (DFS backend account payload)

@Entity
@Table(name = "parties")
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "tracking_id", length = 40)
    private String trackingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 32)
    private PartyType partyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartyStatus status;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "business_name", length = 200)
    private String businessName;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(name = "address_line", length = 500)
    private String addressLine;

    private String city;
    private String country;

    @Column(name = "ntn_number", length = 50)
    private String ntnNumber;

    @Column(name = "father_or_spouse_name", length = 200)
    private String fatherOrSpouseName;

    @Column(name = "cnic_number", length = 30)
    private String cnicNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_document_type", length = 40)
    private IdDocumentType idDocumentType;

    @Column(name = "mother_name", length = 200)
    private String motherName;

    @Column(name = "place_of_birth", length = 120)
    private String placeOfBirth;

    @Column(name = "business_address", length = 500)
    private String businessAddress;

    @Column(name = "secp_registration_no", length = 80)
    private String secpRegistrationNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 40)
    private CorporateEntityType entityType;

    @Column(name = "terms_accepted")
    private Boolean termsAccepted = false;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "kyc_tier", length = 32)
    private String kycTier = "ENTITY_CONSOLIDATED";

    @Column(name = "incorporation_number", length = 80)
    private String incorporationNumber;

    @Column(name = "incorporation_date")
    private LocalDate incorporationDate;

    @Column(name = "incorporation_country", length = 100)
    private String incorporationCountry;

    @Column(name = "incorporation_authority", length = 200)
    private String incorporationAuthority;

    @Column(name = "tax_country", length = 100)
    private String taxCountry;

    @Column(name = "fatca_crs_declared")
    private Boolean fatcaCrsDeclared = false;

    @Column(name = "fatca_crs_details", length = 1000)
    private String fatcaCrsDetails;

    @Column(name = "registered_address", length = 500)
    private String registeredAddress;

    @Column(name = "mailing_address", length = 500)
    private String mailingAddress;

    @Column(name = "place_of_business", length = 500)
    private String placeOfBusiness;

    @Column(name = "address_difference_reason", length = 500)
    private String addressDifferenceReason;

    @Column(name = "nature_of_business", length = 1000)
    private String natureOfBusiness;

    @Column(name = "business_license_details", length = 500)
    private String businessLicenseDetails;

    @Column(name = "purpose_of_account", length = 500)
    private String purposeOfAccount;

    @Column(name = "intended_relationship", length = 500)
    private String intendedRelationship;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "geo_location", length = 200)
    private String geoLocation;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "sanctions_status", length = 32)
    private ScreeningStatus sanctionsStatus = ScreeningStatus.PENDING;

    @Column(name = "sanctions_screened_at")
    private Instant sanctionsScreenedAt;

    @Column(name = "sanctions_notes", length = 1000)
    private String sanctionsNotes;

    /** Backoffice explicitly CLEARed a HIT/review. Auto-screen will not overwrite until re-screen. */
    @Column(name = "sanctions_manual_clear", nullable = false)
    private boolean sanctionsManualClear = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_verification_status", length = 32)
    private IdentityVerificationStatus identityVerificationStatus = IdentityVerificationStatus.PENDING;

    @Column(name = "identity_verification_method", length = 64)
    private String identityVerificationMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_rating", length = 16)
    private RiskRating riskRating = RiskRating.MEDIUM;

    @Column(name = "edd_required")
    private Boolean eddRequired = false;

    @Column(name = "edd_notes", length = 1000)
    private String eddNotes;

    @Column(name = "video_kyc_ref", length = 500)
    private String videoKycRef;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decision_due_at")
    private Instant decisionDueAt;

    @Column(name = "discrepancy_note", length = 1000)
    private String discrepancyNote;

    @Column(name = "draft_expires_at")
    private Instant draftExpiresAt;

    @Column(name = "onboarding_step")
    private Integer onboardingStep = 1;

    @Column(name = "partnership_unregistered")
    private Boolean partnershipUnregistered = false;

    @Column(name = "applicant_is_partner")
    private Boolean applicantIsPartner = false;

    @Column(name = "brand_id")
    private Long brandId;

    @Column(name = "parent_party_id")
    private Long parentPartyId;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by", length = 200)
    private String approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_provision_status", nullable = false, length = 32)
    private AccountProvisionStatus accountProvisionStatus = AccountProvisionStatus.NOT_STARTED;

    @Column(name = "dfs_account_id", length = 100)
    private String dfsAccountId;

    /**
     * DFS App APP_USER_ID for the corporate wallet payer.
     * Required by fundsTransferLocal; not the same as local partner_app_users.id.
     */
    @Column(name = "dfs_app_user_id", length = 32)
    private String dfsAppUserId;

    @Column(name = "account_provision_error", length = 1000)
    private String accountProvisionError;

    @Column(name = "account_provisioned_at")
    private Instant accountProvisionedAt;

    @Column(name = "account_provision_attempts", nullable = false)
    private Integer accountProvisionAttempts = 0;

    @Column(name = "account_provision_last_attempt_at")
    private Instant accountProvisionLastAttemptAt;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "nid_issuance_date")
    private LocalDate nidIssuanceDate;

    @Column(name = "permanent_address", columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(name = "present_address", columnDefinition = "TEXT")
    private String presentAddress;

    @Column(name = "city_id", length = 32)
    private String cityId;

    @Column(name = "business_type_id", length = 32)
    private String businessTypeId;

    @Column(name = "expected_monthly_volume_id", length = 32)
    private String expectedMonthlyVolumeId;

    @Column(name = "parent_agent_id", length = 64)
    private String parentAgentId;

    @Column(name = "level_code", length = 16)
    private String levelCode = "L4";

    @Column(name = "wallet_pin", length = 20)
    private String walletPin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }
    public PartyType getPartyType() { return partyType; }
    public void setPartyType(PartyType partyType) { this.partyType = partyType; }
    public PartyStatus getStatus() { return status; }
    public void setStatus(PartyStatus status) { this.status = status; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getNtnNumber() { return ntnNumber; }
    public void setNtnNumber(String ntnNumber) { this.ntnNumber = ntnNumber; }
    public String getFatherOrSpouseName() { return fatherOrSpouseName; }
    public void setFatherOrSpouseName(String fatherOrSpouseName) { this.fatherOrSpouseName = fatherOrSpouseName; }
    public String getCnicNumber() { return cnicNumber; }
    public void setCnicNumber(String cnicNumber) { this.cnicNumber = cnicNumber; }
    public IdDocumentType getIdDocumentType() { return idDocumentType; }
    public void setIdDocumentType(IdDocumentType idDocumentType) { this.idDocumentType = idDocumentType; }
    public String getMotherName() { return motherName; }
    public void setMotherName(String motherName) { this.motherName = motherName; }
    public String getPlaceOfBirth() { return placeOfBirth; }
    public void setPlaceOfBirth(String placeOfBirth) { this.placeOfBirth = placeOfBirth; }
    public String getBusinessAddress() { return businessAddress; }
    public void setBusinessAddress(String businessAddress) { this.businessAddress = businessAddress; }
    public String getSecpRegistrationNo() { return secpRegistrationNo; }
    public void setSecpRegistrationNo(String secpRegistrationNo) { this.secpRegistrationNo = secpRegistrationNo; }
    public CorporateEntityType getEntityType() { return entityType; }
    public void setEntityType(CorporateEntityType entityType) { this.entityType = entityType; }
    public Boolean getTermsAccepted() { return termsAccepted; }
    public void setTermsAccepted(Boolean termsAccepted) { this.termsAccepted = termsAccepted; }
    public Instant getTermsAcceptedAt() { return termsAcceptedAt; }
    public void setTermsAcceptedAt(Instant termsAcceptedAt) { this.termsAcceptedAt = termsAcceptedAt; }
    public String getKycTier() { return kycTier; }
    public void setKycTier(String kycTier) { this.kycTier = kycTier; }
    public String getIncorporationNumber() { return incorporationNumber; }
    public void setIncorporationNumber(String incorporationNumber) { this.incorporationNumber = incorporationNumber; }
    public LocalDate getIncorporationDate() { return incorporationDate; }
    public void setIncorporationDate(LocalDate incorporationDate) { this.incorporationDate = incorporationDate; }
    public String getIncorporationCountry() { return incorporationCountry; }
    public void setIncorporationCountry(String incorporationCountry) { this.incorporationCountry = incorporationCountry; }
    public String getIncorporationAuthority() { return incorporationAuthority; }
    public void setIncorporationAuthority(String incorporationAuthority) { this.incorporationAuthority = incorporationAuthority; }
    public String getTaxCountry() { return taxCountry; }
    public void setTaxCountry(String taxCountry) { this.taxCountry = taxCountry; }
    public Boolean getFatcaCrsDeclared() { return fatcaCrsDeclared; }
    public void setFatcaCrsDeclared(Boolean fatcaCrsDeclared) { this.fatcaCrsDeclared = fatcaCrsDeclared; }
    public String getFatcaCrsDetails() { return fatcaCrsDetails; }
    public void setFatcaCrsDetails(String fatcaCrsDetails) { this.fatcaCrsDetails = fatcaCrsDetails; }
    public String getRegisteredAddress() { return registeredAddress; }
    public void setRegisteredAddress(String registeredAddress) { this.registeredAddress = registeredAddress; }
    public String getMailingAddress() { return mailingAddress; }
    public void setMailingAddress(String mailingAddress) { this.mailingAddress = mailingAddress; }
    public String getPlaceOfBusiness() { return placeOfBusiness; }
    public void setPlaceOfBusiness(String placeOfBusiness) { this.placeOfBusiness = placeOfBusiness; }
    public String getAddressDifferenceReason() { return addressDifferenceReason; }
    public void setAddressDifferenceReason(String addressDifferenceReason) { this.addressDifferenceReason = addressDifferenceReason; }
    public String getNatureOfBusiness() { return natureOfBusiness; }
    public void setNatureOfBusiness(String natureOfBusiness) { this.natureOfBusiness = natureOfBusiness; }
    public String getBusinessLicenseDetails() { return businessLicenseDetails; }
    public void setBusinessLicenseDetails(String businessLicenseDetails) { this.businessLicenseDetails = businessLicenseDetails; }
    public String getPurposeOfAccount() { return purposeOfAccount; }
    public void setPurposeOfAccount(String purposeOfAccount) { this.purposeOfAccount = purposeOfAccount; }
    public String getIntendedRelationship() { return intendedRelationship; }
    public void setIntendedRelationship(String intendedRelationship) { this.intendedRelationship = intendedRelationship; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public String getGeoLocation() { return geoLocation; }
    public void setGeoLocation(String geoLocation) { this.geoLocation = geoLocation; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public ScreeningStatus getSanctionsStatus() { return sanctionsStatus; }
    public void setSanctionsStatus(ScreeningStatus sanctionsStatus) { this.sanctionsStatus = sanctionsStatus; }
    public Instant getSanctionsScreenedAt() { return sanctionsScreenedAt; }
    public void setSanctionsScreenedAt(Instant sanctionsScreenedAt) { this.sanctionsScreenedAt = sanctionsScreenedAt; }
    public String getSanctionsNotes() { return sanctionsNotes; }
    public void setSanctionsNotes(String sanctionsNotes) { this.sanctionsNotes = sanctionsNotes; }
    public boolean isSanctionsManualClear() { return sanctionsManualClear; }
    public void setSanctionsManualClear(boolean sanctionsManualClear) { this.sanctionsManualClear = sanctionsManualClear; }
    public IdentityVerificationStatus getIdentityVerificationStatus() { return identityVerificationStatus; }
    public void setIdentityVerificationStatus(IdentityVerificationStatus identityVerificationStatus) { this.identityVerificationStatus = identityVerificationStatus; }
    public String getIdentityVerificationMethod() { return identityVerificationMethod; }
    public void setIdentityVerificationMethod(String identityVerificationMethod) { this.identityVerificationMethod = identityVerificationMethod; }
    public RiskRating getRiskRating() { return riskRating; }
    public void setRiskRating(RiskRating riskRating) { this.riskRating = riskRating; }
    public Boolean getEddRequired() { return eddRequired; }
    public void setEddRequired(Boolean eddRequired) { this.eddRequired = eddRequired; }
    public String getEddNotes() { return eddNotes; }
    public void setEddNotes(String eddNotes) { this.eddNotes = eddNotes; }
    public String getVideoKycRef() { return videoKycRef; }
    public void setVideoKycRef(String videoKycRef) { this.videoKycRef = videoKycRef; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getDecisionDueAt() { return decisionDueAt; }
    public void setDecisionDueAt(Instant decisionDueAt) { this.decisionDueAt = decisionDueAt; }
    public String getDiscrepancyNote() { return discrepancyNote; }
    public void setDiscrepancyNote(String discrepancyNote) { this.discrepancyNote = discrepancyNote; }
    public Instant getDraftExpiresAt() { return draftExpiresAt; }
    public void setDraftExpiresAt(Instant draftExpiresAt) { this.draftExpiresAt = draftExpiresAt; }
    public Integer getOnboardingStep() { return onboardingStep; }
    public void setOnboardingStep(Integer onboardingStep) { this.onboardingStep = onboardingStep; }
    public Boolean getPartnershipUnregistered() { return partnershipUnregistered; }
    public void setPartnershipUnregistered(Boolean partnershipUnregistered) { this.partnershipUnregistered = partnershipUnregistered; }
    public Boolean getApplicantIsPartner() { return applicantIsPartner; }
    public void setApplicantIsPartner(Boolean applicantIsPartner) { this.applicantIsPartner = applicantIsPartner; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }
    public Long getParentPartyId() { return parentPartyId; }
    public void setParentPartyId(Long parentPartyId) { this.parentPartyId = parentPartyId; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public AccountProvisionStatus getAccountProvisionStatus() { return accountProvisionStatus; }
    public void setAccountProvisionStatus(AccountProvisionStatus accountProvisionStatus) {
        this.accountProvisionStatus = accountProvisionStatus;
    }
    public String getDfsAccountId() { return dfsAccountId; }
    public void setDfsAccountId(String dfsAccountId) { this.dfsAccountId = dfsAccountId; }
    public String getDfsAppUserId() { return dfsAppUserId; }
    public void setDfsAppUserId(String dfsAppUserId) { this.dfsAppUserId = dfsAppUserId; }
    public String getAccountProvisionError() { return accountProvisionError; }
    public void setAccountProvisionError(String accountProvisionError) {
        this.accountProvisionError = accountProvisionError;
    }
    public Instant getAccountProvisionedAt() { return accountProvisionedAt; }
    public void setAccountProvisionedAt(Instant accountProvisionedAt) {
        this.accountProvisionedAt = accountProvisionedAt;
    }
    public Integer getAccountProvisionAttempts() { return accountProvisionAttempts; }
    public void setAccountProvisionAttempts(Integer accountProvisionAttempts) {
        this.accountProvisionAttempts = accountProvisionAttempts;
    }
    public Instant getAccountProvisionLastAttemptAt() { return accountProvisionLastAttemptAt; }
    public void setAccountProvisionLastAttemptAt(Instant accountProvisionLastAttemptAt) {
        this.accountProvisionLastAttemptAt = accountProvisionLastAttemptAt;
    }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public LocalDate getNidIssuanceDate() { return nidIssuanceDate; }
    public void setNidIssuanceDate(LocalDate nidIssuanceDate) { this.nidIssuanceDate = nidIssuanceDate; }
    public String getPermanentAddress() { return permanentAddress; }
    public void setPermanentAddress(String permanentAddress) { this.permanentAddress = permanentAddress; }
    public String getPresentAddress() { return presentAddress; }
    public void setPresentAddress(String presentAddress) { this.presentAddress = presentAddress; }
    public String getCityId() { return cityId; }
    public void setCityId(String cityId) { this.cityId = cityId; }
    public String getBusinessTypeId() { return businessTypeId; }
    public void setBusinessTypeId(String businessTypeId) { this.businessTypeId = businessTypeId; }
    public String getExpectedMonthlyVolumeId() { return expectedMonthlyVolumeId; }
    public void setExpectedMonthlyVolumeId(String expectedMonthlyVolumeId) {
        this.expectedMonthlyVolumeId = expectedMonthlyVolumeId;
    }
    public String getParentAgentId() { return parentAgentId; }
    public void setParentAgentId(String parentAgentId) { this.parentAgentId = parentAgentId; }
    public String getLevelCode() { return levelCode; }
    public void setLevelCode(String levelCode) { this.levelCode = levelCode; }
    public String getWalletPin() { return walletPin; }
    public void setWalletPin(String walletPin) { this.walletPin = walletPin; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
