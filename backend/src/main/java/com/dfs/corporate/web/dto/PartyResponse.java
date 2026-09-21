package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class PartyResponse {
    private Long id;
    private String publicId;
    private String trackingId;
    private PartyType partyType;
    private PartyStatus status;
    private String kycTier;
    private Integer onboardingStep;
    private String fullName;
    private String businessName;
    private CorporateEntityType entityType;
    private Boolean partnershipUnregistered;
    private Boolean applicantIsPartner;
    private Long brandId;
    private String brandCode;
    private String brandName;
    private String email;
    private String phone;
    private String incorporationNumber;
    private LocalDate incorporationDate;
    private String incorporationCountry;
    private String incorporationAuthority;
    private String ntnNumber;
    private String taxCountry;
    private Boolean fatcaCrsDeclared;
    private String fatcaCrsDetails;
    private String registeredAddress;
    private String mailingAddress;
    private String placeOfBusiness;
    private String addressDifferenceReason;
    private String city;
    private String country;
    private String natureOfBusiness;
    private String businessLicenseDetails;
    private String purposeOfAccount;
    private String intendedRelationship;
    private Boolean termsAccepted;
    private String clientIp;
    private String geoLocation;
    private ScreeningStatus sanctionsStatus;
    private IdentityVerificationStatus identityVerificationStatus;
    private String identityVerificationMethod;
    private Boolean eddRequired;
    private String eddNotes;
    private String videoKycRef;
    private Instant submittedAt;
    private Instant decisionDueAt;
    private String discrepancyNote;
    private Instant draftExpiresAt;
    private Long parentPartyId;
    private String rejectionReason;
    private Instant approvedAt;
    private Instant createdAt;
    private AccountProvisionStatus accountProvisionStatus;
    private String dfsAccountId;
    private String accountProvisionError;
    private Instant accountProvisionedAt;
    private Integer accountProvisionAttempts;
    private Instant accountProvisionLastAttemptAt;
    private int docsPending;
    private int docsRejected;
    private boolean docsReadyForApprove;
    private List<DocumentItem> documents;
    private List<RequiredItem> requiredDocuments;
    private List<AssociatedPersonItem> associatedPersons;
    private List<PartnerInviteResponse> partnerInvites;
    private List<PartnerAppUserResponse> partnerAppUsers;
    private int partnerKycTotal;
    private int partnerKycCompleted;
    private boolean canSubmit;
    private long tatWorkingDays = 5;
    private Boolean tatOverdue;

    public static PartyResponse from(Party p) {
        PartyResponse r = new PartyResponse();
        r.id = p.getId();
        r.publicId = p.getPublicId();
        r.trackingId = p.getTrackingId();
        r.partyType = p.getPartyType();
        r.status = p.getStatus();
        r.kycTier = p.getKycTier();
        r.onboardingStep = p.getOnboardingStep();
        r.fullName = p.getFullName();
        r.businessName = p.getBusinessName();
        r.entityType = p.getEntityType();
        r.partnershipUnregistered = Boolean.TRUE.equals(p.getPartnershipUnregistered());
        r.applicantIsPartner = Boolean.TRUE.equals(p.getApplicantIsPartner());
        r.brandId = p.getBrandId();
        r.email = p.getEmail();
        r.phone = p.getPhone();
        r.incorporationNumber = p.getIncorporationNumber();
        r.incorporationDate = p.getIncorporationDate();
        r.incorporationCountry = p.getIncorporationCountry();
        r.incorporationAuthority = p.getIncorporationAuthority();
        r.ntnNumber = p.getNtnNumber();
        r.taxCountry = p.getTaxCountry();
        r.fatcaCrsDeclared = p.getFatcaCrsDeclared();
        r.fatcaCrsDetails = p.getFatcaCrsDetails();
        r.registeredAddress = p.getRegisteredAddress();
        r.mailingAddress = p.getMailingAddress();
        r.placeOfBusiness = p.getPlaceOfBusiness();
        r.addressDifferenceReason = p.getAddressDifferenceReason();
        r.city = p.getCity();
        r.country = p.getCountry();
        r.natureOfBusiness = p.getNatureOfBusiness();
        r.businessLicenseDetails = p.getBusinessLicenseDetails();
        r.purposeOfAccount = p.getPurposeOfAccount();
        r.intendedRelationship = p.getIntendedRelationship();
        r.termsAccepted = p.getTermsAccepted();
        r.clientIp = p.getClientIp();
        r.geoLocation = p.getGeoLocation();
        r.sanctionsStatus = p.getSanctionsStatus();
        r.identityVerificationStatus = p.getIdentityVerificationStatus();
        r.identityVerificationMethod = p.getIdentityVerificationMethod();
        r.eddRequired = p.getEddRequired();
        r.eddNotes = p.getEddNotes();
        r.videoKycRef = p.getVideoKycRef();
        r.submittedAt = p.getSubmittedAt();
        r.decisionDueAt = p.getDecisionDueAt();
        r.discrepancyNote = p.getDiscrepancyNote();
        r.draftExpiresAt = p.getDraftExpiresAt();
        r.parentPartyId = p.getParentPartyId();
        r.rejectionReason = p.getRejectionReason();
        r.approvedAt = p.getApprovedAt();
        r.createdAt = p.getCreatedAt();
        r.accountProvisionStatus = p.getAccountProvisionStatus() != null
                ? p.getAccountProvisionStatus() : AccountProvisionStatus.NOT_STARTED;
        r.dfsAccountId = p.getDfsAccountId();
        r.accountProvisionError = p.getAccountProvisionError();
        r.accountProvisionedAt = p.getAccountProvisionedAt();
        r.accountProvisionAttempts = p.getAccountProvisionAttempts();
        r.accountProvisionLastAttemptAt = p.getAccountProvisionLastAttemptAt();
        return r;
    }

    public static class DocumentItem {
        private Long id;
        private String documentCode;
        private String originalName;
        private DocumentStatus status;
        private Instant uploadedAt;
        private String contentType;
        private String reviewNote;

        public static DocumentItem from(PartyDocument d) {
            DocumentItem i = new DocumentItem();
            i.id = d.getId();
            i.documentCode = d.getDocumentCode();
            i.originalName = d.getOriginalName();
            i.status = d.getStatus();
            i.uploadedAt = d.getUploadedAt();
            i.contentType = d.getContentType();
            i.reviewNote = d.getReviewNote();
            return i;
        }
        public Long getId() { return id; }
        public String getDocumentCode() { return documentCode; }
        public String getOriginalName() { return originalName; }
        public DocumentStatus getStatus() { return status; }
        public Instant getUploadedAt() { return uploadedAt; }
        public String getContentType() { return contentType; }
        public String getReviewNote() { return reviewNote; }
    }

    public static class RequiredItem {
        private String documentCode;
        private String documentLabel;
        private boolean mandatory;
        private boolean uploaded;
        public RequiredItem(String code, String label, boolean mandatory, boolean uploaded) {
            this.documentCode = code; this.documentLabel = label; this.mandatory = mandatory; this.uploaded = uploaded;
        }
        public String getDocumentCode() { return documentCode; }
        public String getDocumentLabel() { return documentLabel; }
        public boolean isMandatory() { return mandatory; }
        public boolean isUploaded() { return uploaded; }
    }

    public static class AssociatedPersonItem {
        private Long id;
        private AssociatedPersonRole roleType;
        private String fullName;
        private String fatherOrSpouseName;
        private LocalDate dateOfBirth;
        private String motherMaidenName;
        private String placeOfBirth;
        private IdDocumentType idDocumentType;
        private String idDocumentNumber;
        private BigDecimal ownershipPercent;
        private Boolean authorizedToOperate;
        private String email;
        private String phone;
        private ScreeningStatus sanctionsStatus;
        private IdentityVerificationStatus identityVerificationStatus;

        public static AssociatedPersonItem from(AssociatedPerson p) {
            AssociatedPersonItem i = new AssociatedPersonItem();
            i.id = p.getId();
            i.roleType = p.getRoleType();
            i.fullName = p.getFullName();
            i.fatherOrSpouseName = p.getFatherOrSpouseName();
            i.dateOfBirth = p.getDateOfBirth();
            i.motherMaidenName = p.getMotherMaidenName();
            i.placeOfBirth = p.getPlaceOfBirth();
            i.idDocumentType = p.getIdDocumentType();
            i.idDocumentNumber = p.getIdDocumentNumber();
            i.ownershipPercent = p.getOwnershipPercent();
            i.authorizedToOperate = p.getAuthorizedToOperate();
            i.email = p.getEmail();
            i.phone = p.getPhone();
            i.sanctionsStatus = p.getSanctionsStatus();
            i.identityVerificationStatus = p.getIdentityVerificationStatus();
            return i;
        }
        public Long getId() { return id; }
        public AssociatedPersonRole getRoleType() { return roleType; }
        public String getFullName() { return fullName; }
        public String getFatherOrSpouseName() { return fatherOrSpouseName; }
        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public String getMotherMaidenName() { return motherMaidenName; }
        public String getPlaceOfBirth() { return placeOfBirth; }
        public IdDocumentType getIdDocumentType() { return idDocumentType; }
        public String getIdDocumentNumber() { return idDocumentNumber; }
        public BigDecimal getOwnershipPercent() { return ownershipPercent; }
        public Boolean getAuthorizedToOperate() { return authorizedToOperate; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
        public ScreeningStatus getSanctionsStatus() { return sanctionsStatus; }
        public IdentityVerificationStatus getIdentityVerificationStatus() { return identityVerificationStatus; }
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getTrackingId() { return trackingId; }
    public PartyType getPartyType() { return partyType; }
    public PartyStatus getStatus() { return status; }
    public String getKycTier() { return kycTier; }
    public Integer getOnboardingStep() { return onboardingStep; }
    public String getFullName() { return fullName; }
    public String getBusinessName() { return businessName; }
    public CorporateEntityType getEntityType() { return entityType; }
    public Boolean getPartnershipUnregistered() { return partnershipUnregistered; }
    public Boolean getApplicantIsPartner() { return applicantIsPartner; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }
    public String getBrandCode() { return brandCode; }
    public void setBrandCode(String brandCode) { this.brandCode = brandCode; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getIncorporationNumber() { return incorporationNumber; }
    public LocalDate getIncorporationDate() { return incorporationDate; }
    public String getIncorporationCountry() { return incorporationCountry; }
    public String getIncorporationAuthority() { return incorporationAuthority; }
    public String getNtnNumber() { return ntnNumber; }
    public String getTaxCountry() { return taxCountry; }
    public Boolean getFatcaCrsDeclared() { return fatcaCrsDeclared; }
    public String getFatcaCrsDetails() { return fatcaCrsDetails; }
    public String getRegisteredAddress() { return registeredAddress; }
    public String getMailingAddress() { return mailingAddress; }
    public String getPlaceOfBusiness() { return placeOfBusiness; }
    public String getAddressDifferenceReason() { return addressDifferenceReason; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
    public String getNatureOfBusiness() { return natureOfBusiness; }
    public String getBusinessLicenseDetails() { return businessLicenseDetails; }
    public String getPurposeOfAccount() { return purposeOfAccount; }
    public String getIntendedRelationship() { return intendedRelationship; }
    public Boolean getTermsAccepted() { return termsAccepted; }
    public String getClientIp() { return clientIp; }
    public String getGeoLocation() { return geoLocation; }
    public ScreeningStatus getSanctionsStatus() { return sanctionsStatus; }
    public IdentityVerificationStatus getIdentityVerificationStatus() { return identityVerificationStatus; }
    public String getIdentityVerificationMethod() { return identityVerificationMethod; }
    public Boolean getEddRequired() { return eddRequired; }
    public String getEddNotes() { return eddNotes; }
    public String getVideoKycRef() { return videoKycRef; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecisionDueAt() { return decisionDueAt; }
    public String getDiscrepancyNote() { return discrepancyNote; }
    public Instant getDraftExpiresAt() { return draftExpiresAt; }
    public Long getParentPartyId() { return parentPartyId; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public AccountProvisionStatus getAccountProvisionStatus() { return accountProvisionStatus; }
    public String getDfsAccountId() { return dfsAccountId; }
    public String getAccountProvisionError() { return accountProvisionError; }
    public Instant getAccountProvisionedAt() { return accountProvisionedAt; }
    public Integer getAccountProvisionAttempts() { return accountProvisionAttempts; }
    public Instant getAccountProvisionLastAttemptAt() { return accountProvisionLastAttemptAt; }
    public int getDocsPending() { return docsPending; }
    public void setDocsPending(int docsPending) { this.docsPending = docsPending; }
    public int getDocsRejected() { return docsRejected; }
    public void setDocsRejected(int docsRejected) { this.docsRejected = docsRejected; }
    public boolean isDocsReadyForApprove() { return docsReadyForApprove; }
    public void setDocsReadyForApprove(boolean docsReadyForApprove) { this.docsReadyForApprove = docsReadyForApprove; }
    public List<DocumentItem> getDocuments() { return documents; }
    public void setDocuments(List<DocumentItem> documents) { this.documents = documents; }
    public List<RequiredItem> getRequiredDocuments() { return requiredDocuments; }
    public void setRequiredDocuments(List<RequiredItem> requiredDocuments) { this.requiredDocuments = requiredDocuments; }
    public List<AssociatedPersonItem> getAssociatedPersons() { return associatedPersons; }
    public void setAssociatedPersons(List<AssociatedPersonItem> associatedPersons) { this.associatedPersons = associatedPersons; }
    public List<PartnerInviteResponse> getPartnerInvites() { return partnerInvites; }
    public void setPartnerInvites(List<PartnerInviteResponse> partnerInvites) { this.partnerInvites = partnerInvites; }
    public List<PartnerAppUserResponse> getPartnerAppUsers() { return partnerAppUsers; }
    public void setPartnerAppUsers(List<PartnerAppUserResponse> partnerAppUsers) { this.partnerAppUsers = partnerAppUsers; }
    public int getPartnerKycTotal() { return partnerKycTotal; }
    public void setPartnerKycTotal(int partnerKycTotal) { this.partnerKycTotal = partnerKycTotal; }
    public int getPartnerKycCompleted() { return partnerKycCompleted; }
    public void setPartnerKycCompleted(int partnerKycCompleted) { this.partnerKycCompleted = partnerKycCompleted; }
    public boolean isCanSubmit() { return canSubmit; }
    public void setCanSubmit(boolean canSubmit) { this.canSubmit = canSubmit; }
    public long getTatWorkingDays() { return tatWorkingDays; }
    public Boolean getTatOverdue() { return tatOverdue; }
    public void setTatOverdue(Boolean tatOverdue) { this.tatOverdue = tatOverdue; }
}
