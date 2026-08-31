package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AssociatedPersonRequest {
    @NotNull private AssociatedPersonRole roleType;
    @NotBlank private String fullName;
    private String fatherOrSpouseName;
    private LocalDate dateOfBirth;
    private String motherMaidenName;
    private String placeOfBirth;
    private IdDocumentType idDocumentType;
    private String idDocumentNumber;
    private LocalDate idIssueDate;
    private LocalDate idExpiryDate;
    private String passportNumber;
    private String passportCountry;
    private String nationalities;
    private String taxResidencies;
    private String email;
    private String phone;
    private String mailingAddress;
    private String occupation;
    private BigDecimal ownershipPercent;
    private Boolean authorizedToOperate;
    private Boolean fatcaCrsDeclared;
    private String fatcaCrsDetails;

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
}
