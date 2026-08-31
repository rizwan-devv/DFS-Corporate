package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class ProfileUpdateRequest {
    @NotBlank private String fullName;
    @NotBlank private String businessName;
    @NotNull private CorporateEntityType entityType;
    private String incorporationNumber;
    private LocalDate incorporationDate;
    private String incorporationCountry;
    private String incorporationAuthority;
    private String ntnNumber;
    private String taxCountry;
    private Boolean fatcaCrsDeclared;
    private String fatcaCrsDetails;
    @NotBlank private String registeredAddress;
    private String mailingAddress;
    private String placeOfBusiness;
    private String addressDifferenceReason;
    private String city;
    private String country;
    private String phone;
    @NotBlank private String natureOfBusiness;
    private String businessLicenseDetails;
    @NotBlank private String purposeOfAccount;
    private String intendedRelationship;
    @NotNull private Boolean termsAccepted;
    private Integer onboardingStep;
    private String geoLocation;
    private RiskRating riskRating;
    private Boolean eddRequired;
    private String eddNotes;
    private String videoKycRef;
    private Boolean partnershipUnregistered;
    private Boolean applicantIsPartner;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public CorporateEntityType getEntityType() { return entityType; }
    public void setEntityType(CorporateEntityType entityType) { this.entityType = entityType; }
    public String getIncorporationNumber() { return incorporationNumber; }
    public void setIncorporationNumber(String incorporationNumber) { this.incorporationNumber = incorporationNumber; }
    public LocalDate getIncorporationDate() { return incorporationDate; }
    public void setIncorporationDate(LocalDate incorporationDate) { this.incorporationDate = incorporationDate; }
    public String getIncorporationCountry() { return incorporationCountry; }
    public void setIncorporationCountry(String incorporationCountry) { this.incorporationCountry = incorporationCountry; }
    public String getIncorporationAuthority() { return incorporationAuthority; }
    public void setIncorporationAuthority(String incorporationAuthority) { this.incorporationAuthority = incorporationAuthority; }
    public String getNtnNumber() { return ntnNumber; }
    public void setNtnNumber(String ntnNumber) { this.ntnNumber = ntnNumber; }
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
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNatureOfBusiness() { return natureOfBusiness; }
    public void setNatureOfBusiness(String natureOfBusiness) { this.natureOfBusiness = natureOfBusiness; }
    public String getBusinessLicenseDetails() { return businessLicenseDetails; }
    public void setBusinessLicenseDetails(String businessLicenseDetails) { this.businessLicenseDetails = businessLicenseDetails; }
    public String getPurposeOfAccount() { return purposeOfAccount; }
    public void setPurposeOfAccount(String purposeOfAccount) { this.purposeOfAccount = purposeOfAccount; }
    public String getIntendedRelationship() { return intendedRelationship; }
    public void setIntendedRelationship(String intendedRelationship) { this.intendedRelationship = intendedRelationship; }
    public Boolean getTermsAccepted() { return termsAccepted; }
    public void setTermsAccepted(Boolean termsAccepted) { this.termsAccepted = termsAccepted; }
    public Integer getOnboardingStep() { return onboardingStep; }
    public void setOnboardingStep(Integer onboardingStep) { this.onboardingStep = onboardingStep; }
    public String getGeoLocation() { return geoLocation; }
    public void setGeoLocation(String geoLocation) { this.geoLocation = geoLocation; }
    public RiskRating getRiskRating() { return riskRating; }
    public void setRiskRating(RiskRating riskRating) { this.riskRating = riskRating; }
    public Boolean getEddRequired() { return eddRequired; }
    public void setEddRequired(Boolean eddRequired) { this.eddRequired = eddRequired; }
    public String getEddNotes() { return eddNotes; }
    public void setEddNotes(String eddNotes) { this.eddNotes = eddNotes; }
    public String getVideoKycRef() { return videoKycRef; }
    public void setVideoKycRef(String videoKycRef) { this.videoKycRef = videoKycRef; }
    public Boolean getPartnershipUnregistered() { return partnershipUnregistered; }
    public void setPartnershipUnregistered(Boolean partnershipUnregistered) { this.partnershipUnregistered = partnershipUnregistered; }
    public Boolean getApplicantIsPartner() { return applicantIsPartner; }
    public void setApplicantIsPartner(Boolean applicantIsPartner) { this.applicantIsPartner = applicantIsPartner; }
}
