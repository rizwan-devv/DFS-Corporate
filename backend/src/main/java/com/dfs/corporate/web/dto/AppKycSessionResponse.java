package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.PartnerAppKycStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class AppKycSessionResponse {
    private String sessionToken;
    private Long appUserId;
    private String phone;
    private String fullName;
    private String email;
    private PartnerAppKycStatus status;
    private String businessName;
    private String trackingId;
    private String cnicNumber;
    private String cnicFullName;
    private LocalDate dateOfBirth;
    private String videoKycRef;
    private String videoVerificationStatus;
    private Boolean videoUploaded;
    private String biometricRef;
    private Boolean selfieUploaded;
    private String failureReason;
    private List<Map<String, Object>> requiredDocuments;
    private Boolean canSubmit;
    private Instant completedAt;
    private Boolean mustChangePassword;
    private Boolean mobileVerified;
    private String fatherName;
    private String gender;
    private String permanentAddress;
    private String presentAddress;
    private LocalDate nidIssuanceDate;
    private String accountProvisionStatus;
    private String dfsAccountId;
    private String provinceId;
    private String cityId;

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    public Long getAppUserId() { return appUserId; }
    public void setAppUserId(Long appUserId) { this.appUserId = appUserId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public PartnerAppKycStatus getStatus() { return status; }
    public void setStatus(PartnerAppKycStatus status) { this.status = status; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }
    public String getCnicNumber() { return cnicNumber; }
    public void setCnicNumber(String cnicNumber) { this.cnicNumber = cnicNumber; }
    public String getCnicFullName() { return cnicFullName; }
    public void setCnicFullName(String cnicFullName) { this.cnicFullName = cnicFullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getVideoKycRef() { return videoKycRef; }
    public void setVideoKycRef(String videoKycRef) { this.videoKycRef = videoKycRef; }
    public String getVideoVerificationStatus() { return videoVerificationStatus; }
    public void setVideoVerificationStatus(String videoVerificationStatus) {
        this.videoVerificationStatus = videoVerificationStatus;
    }
    public Boolean getVideoUploaded() { return videoUploaded; }
    public void setVideoUploaded(Boolean videoUploaded) { this.videoUploaded = videoUploaded; }
    public String getBiometricRef() { return biometricRef; }
    public void setBiometricRef(String biometricRef) { this.biometricRef = biometricRef; }
    public Boolean getSelfieUploaded() { return selfieUploaded; }
    public void setSelfieUploaded(Boolean selfieUploaded) { this.selfieUploaded = selfieUploaded; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public List<Map<String, Object>> getRequiredDocuments() { return requiredDocuments; }
    public void setRequiredDocuments(List<Map<String, Object>> requiredDocuments) { this.requiredDocuments = requiredDocuments; }
    public Boolean getCanSubmit() { return canSubmit; }
    public void setCanSubmit(Boolean canSubmit) { this.canSubmit = canSubmit; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public Boolean getMobileVerified() { return mobileVerified; }
    public void setMobileVerified(Boolean mobileVerified) { this.mobileVerified = mobileVerified; }
    public String getFatherName() { return fatherName; }
    public void setFatherName(String fatherName) { this.fatherName = fatherName; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getPermanentAddress() { return permanentAddress; }
    public void setPermanentAddress(String permanentAddress) { this.permanentAddress = permanentAddress; }
    public String getPresentAddress() { return presentAddress; }
    public void setPresentAddress(String presentAddress) { this.presentAddress = presentAddress; }
    public LocalDate getNidIssuanceDate() { return nidIssuanceDate; }
    public void setNidIssuanceDate(LocalDate nidIssuanceDate) { this.nidIssuanceDate = nidIssuanceDate; }
    public String getAccountProvisionStatus() { return accountProvisionStatus; }
    public void setAccountProvisionStatus(String accountProvisionStatus) {
        this.accountProvisionStatus = accountProvisionStatus;
    }
    public String getDfsAccountId() { return dfsAccountId; }
    public void setDfsAccountId(String dfsAccountId) { this.dfsAccountId = dfsAccountId; }
    public String getProvinceId() { return provinceId; }
    public void setProvinceId(String provinceId) { this.provinceId = provinceId; }
    public String getCityId() { return cityId; }
    public void setCityId(String cityId) { this.cityId = cityId; }
}
