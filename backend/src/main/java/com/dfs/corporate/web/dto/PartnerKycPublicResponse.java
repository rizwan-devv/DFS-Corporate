package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.IdDocumentType;
import com.dfs.corporate.domain.PartnerInviteStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class PartnerKycPublicResponse {
    private String businessName;
    private String entityType;
    private String partnerFullName;
    private String email;
    private PartnerInviteStatus status;
    private boolean expired;
    private IdDocumentType idDocumentType;
    private String idDocumentNumber;
    private LocalDate dateOfBirth;
    private String motherMaidenName;
    private String placeOfBirth;
    private String fatherOrSpouseName;
    private String phone;
    private String mailingAddress;
    private String nationalities;
    private Boolean fatcaCrsDeclared;
    private String fatcaCrsDetails;
    private List<Map<String, Object>> requiredDocs;
    private boolean canComplete;

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getPartnerFullName() { return partnerFullName; }
    public void setPartnerFullName(String partnerFullName) { this.partnerFullName = partnerFullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public PartnerInviteStatus getStatus() { return status; }
    public void setStatus(PartnerInviteStatus status) { this.status = status; }
    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }
    public IdDocumentType getIdDocumentType() { return idDocumentType; }
    public void setIdDocumentType(IdDocumentType idDocumentType) { this.idDocumentType = idDocumentType; }
    public String getIdDocumentNumber() { return idDocumentNumber; }
    public void setIdDocumentNumber(String idDocumentNumber) { this.idDocumentNumber = idDocumentNumber; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getMotherMaidenName() { return motherMaidenName; }
    public void setMotherMaidenName(String motherMaidenName) { this.motherMaidenName = motherMaidenName; }
    public String getPlaceOfBirth() { return placeOfBirth; }
    public void setPlaceOfBirth(String placeOfBirth) { this.placeOfBirth = placeOfBirth; }
    public String getFatherOrSpouseName() { return fatherOrSpouseName; }
    public void setFatherOrSpouseName(String fatherOrSpouseName) { this.fatherOrSpouseName = fatherOrSpouseName; }
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
    public List<Map<String, Object>> getRequiredDocs() { return requiredDocs; }
    public void setRequiredDocs(List<Map<String, Object>> requiredDocs) { this.requiredDocs = requiredDocs; }
    public boolean isCanComplete() { return canComplete; }
    public void setCanComplete(boolean canComplete) { this.canComplete = canComplete; }
}
