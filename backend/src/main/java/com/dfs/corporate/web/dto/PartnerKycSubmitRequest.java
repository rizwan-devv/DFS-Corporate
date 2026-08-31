package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.IdDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class PartnerKycSubmitRequest {
    @NotBlank
    private String fullName;
    private String fatherOrSpouseName;
    @NotNull
    private LocalDate dateOfBirth;
    @NotBlank
    private String motherMaidenName;
    @NotBlank
    private String placeOfBirth;
    @NotNull
    private IdDocumentType idDocumentType;
    @NotBlank
    private String idDocumentNumber;
    private LocalDate idIssueDate;
    private LocalDate idExpiryDate;
    private String phone;
    private String mailingAddress;
    private String nationalities;
    private Boolean fatcaCrsDeclared;
    private String fatcaCrsDetails;

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
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getMailingAddress() { return mailingAddress; }
    public void setMailingAddress(String mailingAddress) { this.mailingAddress = mailingAddress; }
    public String getNationalities() { return nationalities; }
    public void setNationalities(String nationalities) { this.nationalities = nationalities; }
    public Boolean getFatcaCrsDeclared() { return fatcaCrsDeclared; }
    public void setFatcaCrsDeclared(Boolean fatcaCrsDeclared) { this.fatcaCrsDeclared = fatcaCrsDeclared; }
    public String getFatcaCrsDetails() { return fatcaCrsDetails; }
    public void setFatcaCrsDetails(String fatcaCrsDetails) { this.fatcaCrsDetails = fatcaCrsDetails; }
}
