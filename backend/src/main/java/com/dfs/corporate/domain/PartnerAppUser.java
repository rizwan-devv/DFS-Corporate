package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "partner_app_users")
public class PartnerAppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "associated_person_id")
    private Long associatedPersonId;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(length = 200)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "temp_pin", nullable = false, length = 20)
    private String tempPin;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = true;

    @Column(name = "mobile_verified", nullable = false)
    private Boolean mobileVerified = false;

    @Column(name = "father_name", length = 200)
    private String fatherName;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "permanent_address", columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(name = "present_address", columnDefinition = "TEXT")
    private String presentAddress;

    @Column(name = "nid_issuance_date")
    private LocalDate nidIssuanceDate;

    @Column(name = "wallet_pin", length = 20)
    private String walletPin;

    @Column(name = "imei_no", length = 64)
    private String imeiNo;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /** Selected province LOV id (KYC app). */
    @Column(name = "province_id", length = 32)
    private String provinceId;

    /** Selected city LOV id (KYC app) — also used as DFS cityId when set. */
    @Column(name = "city_id", length = 32)
    private String cityId;

    /**
     * Plain password from force-change — sent to DFS Account API on admin approve, then cleared.
     * Not used for app login (password_hash is).
     */
    @Column(name = "password_plain", length = 128)
    private String passwordPlain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartnerAppKycStatus status = PartnerAppKycStatus.INVITED;

    @Column(name = "app_invite_token", nullable = false, unique = true, length = 64)
    private String appInviteToken;

    @Column(name = "session_token", length = 64)
    private String sessionToken;

    @Column(name = "cnic_number", length = 40)
    private String cnicNumber;

    @Column(name = "cnic_full_name", length = 200)
    private String cnicFullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "video_kyc_ref", length = 500)
    private String videoKycRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_verification_status", nullable = false, length = 32)
    private VideoVerificationStatus videoVerificationStatus = VideoVerificationStatus.NONE;

    @Column(name = "biometric_ref", length = 500)
    private String biometricRef;

    @Column(name = "selfie_uploaded")
    private Boolean selfieUploaded = false;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getId() { return id; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public Long getAssociatedPersonId() { return associatedPersonId; }
    public void setAssociatedPersonId(Long associatedPersonId) { this.associatedPersonId = associatedPersonId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getTempPin() { return tempPin; }
    public void setTempPin(String tempPin) { this.tempPin = tempPin; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
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
    public String getWalletPin() { return walletPin; }
    public void setWalletPin(String walletPin) { this.walletPin = walletPin; }
    public String getImeiNo() { return imeiNo; }
    public void setImeiNo(String imeiNo) { this.imeiNo = imeiNo; }
    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public void setPasswordChangedAt(Instant passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }
    public String getProvinceId() { return provinceId; }
    public void setProvinceId(String provinceId) { this.provinceId = provinceId; }
    public String getCityId() { return cityId; }
    public void setCityId(String cityId) { this.cityId = cityId; }
    public String getPasswordPlain() { return passwordPlain; }
    public void setPasswordPlain(String passwordPlain) { this.passwordPlain = passwordPlain; }
    public PartnerAppKycStatus getStatus() { return status; }
    public void setStatus(PartnerAppKycStatus status) { this.status = status; }
    public String getAppInviteToken() { return appInviteToken; }
    public void setAppInviteToken(String appInviteToken) { this.appInviteToken = appInviteToken; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    public String getCnicNumber() { return cnicNumber; }
    public void setCnicNumber(String cnicNumber) { this.cnicNumber = cnicNumber; }
    public String getCnicFullName() { return cnicFullName; }
    public void setCnicFullName(String cnicFullName) { this.cnicFullName = cnicFullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getVideoKycRef() { return videoKycRef; }
    public void setVideoKycRef(String videoKycRef) { this.videoKycRef = videoKycRef; }
    public VideoVerificationStatus getVideoVerificationStatus() { return videoVerificationStatus; }
    public void setVideoVerificationStatus(VideoVerificationStatus videoVerificationStatus) {
        this.videoVerificationStatus = videoVerificationStatus;
    }
    public String getBiometricRef() { return biometricRef; }
    public void setBiometricRef(String biometricRef) { this.biometricRef = biometricRef; }
    public Boolean getSelfieUploaded() { return selfieUploaded; }
    public void setSelfieUploaded(Boolean selfieUploaded) { this.selfieUploaded = selfieUploaded; }
    public Instant getInvitedAt() { return invitedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
