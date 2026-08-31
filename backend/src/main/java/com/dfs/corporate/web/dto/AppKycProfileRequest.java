package com.dfs.corporate.web.dto;

import java.time.LocalDate;

public class AppKycProfileRequest {
    private String cnicNumber;
    private String cnicFullName;
    private LocalDate dateOfBirth;
    private String videoKycRef;
    private String biometricRef;
    private String fatherName;
    private String gender;
    private String permanentAddress;
    private String presentAddress;
    private LocalDate nidIssuanceDate;
    private String walletPin;
    private String imeiNo;
    private String deviceModel;
    private String appVersion;
    private String provinceId;
    private String cityId;

    public String getCnicNumber() { return cnicNumber; }
    public void setCnicNumber(String cnicNumber) { this.cnicNumber = cnicNumber; }
    public String getCnicFullName() { return cnicFullName; }
    public void setCnicFullName(String cnicFullName) { this.cnicFullName = cnicFullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getVideoKycRef() { return videoKycRef; }
    public void setVideoKycRef(String videoKycRef) { this.videoKycRef = videoKycRef; }
    public String getBiometricRef() { return biometricRef; }
    public void setBiometricRef(String biometricRef) { this.biometricRef = biometricRef; }
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
    public String getProvinceId() { return provinceId; }
    public void setProvinceId(String provinceId) { this.provinceId = provinceId; }
    public String getCityId() { return cityId; }
    public void setCityId(String cityId) { this.cityId = cityId; }
}
