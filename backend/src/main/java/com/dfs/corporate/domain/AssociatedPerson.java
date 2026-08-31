package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "associated_persons")
public class AssociatedPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 40)
    private AssociatedPersonRole roleType;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "father_or_spouse_name", length = 200)
    private String fatherOrSpouseName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "mother_maiden_name", length = 200)
    private String motherMaidenName;

    @Column(name = "place_of_birth", length = 120)
    private String placeOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_document_type", length = 40)
    private IdDocumentType idDocumentType;

    @Column(name = "id_document_number", length = 40)
    private String idDocumentNumber;

    @Column(name = "id_issue_date")
    private LocalDate idIssueDate;

    @Column(name = "id_expiry_date")
    private LocalDate idExpiryDate;

    @Column(name = "passport_number", length = 40)
    private String passportNumber;

    @Column(name = "passport_country", length = 80)
    private String passportCountry;

    @Column(length = 300)
    private String nationalities;

    @Column(name = "tax_residencies", length = 300)
    private String taxResidencies;

    private String email;
    private String phone;

    @Column(name = "mailing_address", length = 500)
    private String mailingAddress;

    private String occupation;

    @Column(name = "ownership_percent", precision = 5, scale = 2)
    private BigDecimal ownershipPercent;

    @Column(name = "authorized_to_operate")
    private Boolean authorizedToOperate = false;

    @Column(name = "fatca_crs_declared")
    private Boolean fatcaCrsDeclared = false;

    @Column(name = "fatca_crs_details", length = 500)
    private String fatcaCrsDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "sanctions_status", length = 32)
    private ScreeningStatus sanctionsStatus = ScreeningStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_verification_status", length = 32)
    private IdentityVerificationStatus identityVerificationStatus = IdentityVerificationStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public AssociatedPersonRole getRoleType() { return roleType; }
    public void setRoleType(AssociatedPersonRole roleType) { this.roleType = roleType; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getFatherOrSpouseName() { return fatherOrSpouseName; }
    public void setFatherOrSpouseName(String fatherOrSpouseName) { this.fatherOrSpouseName = fatherOrSpouseName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getMotherMaidenName() { return motherMaidenName; }
    public void setMotherMaidenName(String motherMaidenName) { this.motherMaidenName = motherMaidenName; }
    public String getPlaceOfBirth() { return placeOfBirth; }
    public void setPlaceOfBirth(String placeOfBirth) { this.placeOfBirth = placeOfBirth; }
    public IdDocumentType getIdDocumentType() { return idDocumentType; }
    public void setIdDocumentType(IdDocumentType idDocumentType) { this.idDocumentType = idDocumentType; }
    public String getIdDocumentNumber() { return idDocumentNumber; }
    public void setIdDocumentNumber(String idDocumentNumber) { this.idDocumentNumber = idDocumentNumber; }
    public LocalDate getIdIssueDate() { return idIssueDate; }
    public void setIdIssueDate(LocalDate idIssueDate) { this.idIssueDate = idIssueDate; }
    public LocalDate getIdExpiryDate() { return idExpiryDate; }
    public void setIdExpiryDate(LocalDate idExpiryDate) { this.idExpiryDate = idExpiryDate; }
    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }
    public String getPassportCountry() { return passportCountry; }
    public void setPassportCountry(String passportCountry) { this.passportCountry = passportCountry; }
    public String getNationalities() { return nationalities; }
    public void setNationalities(String nationalities) { this.nationalities = nationalities; }
    public String getTaxResidencies() { return taxResidencies; }
    public void setTaxResidencies(String taxResidencies) { this.taxResidencies = taxResidencies; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getMailingAddress() { return mailingAddress; }
    public void setMailingAddress(String mailingAddress) { this.mailingAddress = mailingAddress; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
    public BigDecimal getOwnershipPercent() { return ownershipPercent; }
    public void setOwnershipPercent(BigDecimal ownershipPercent) { this.ownershipPercent = ownershipPercent; }
    public Boolean getAuthorizedToOperate() { return authorizedToOperate; }
    public void setAuthorizedToOperate(Boolean authorizedToOperate) { this.authorizedToOperate = authorizedToOperate; }
    public Boolean getFatcaCrsDeclared() { return fatcaCrsDeclared; }
    public void setFatcaCrsDeclared(Boolean fatcaCrsDeclared) { this.fatcaCrsDeclared = fatcaCrsDeclared; }
    public String getFatcaCrsDetails() { return fatcaCrsDetails; }
    public void setFatcaCrsDetails(String fatcaCrsDetails) { this.fatcaCrsDetails = fatcaCrsDetails; }
    public ScreeningStatus getSanctionsStatus() { return sanctionsStatus; }
    public void setSanctionsStatus(ScreeningStatus sanctionsStatus) { this.sanctionsStatus = sanctionsStatus; }
    public IdentityVerificationStatus getIdentityVerificationStatus() { return identityVerificationStatus; }
    public void setIdentityVerificationStatus(IdentityVerificationStatus identityVerificationStatus) { this.identityVerificationStatus = identityVerificationStatus; }
}
