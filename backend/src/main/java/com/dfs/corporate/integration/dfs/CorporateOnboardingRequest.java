package com.dfs.corporate.integration.dfs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CorporateOnboardingRequest {

    private String channel;
    /** Parent company name — same value for children of that company. */
    private String segment;
    @JsonProperty("imieNo")
    private String imieNo;
    private Payload payload;

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getSegment() { return segment; }
    public void setSegment(String segment) { this.segment = segment; }
    public String getImieNo() { return imieNo; }
    public void setImieNo(String imieNo) { this.imieNo = imieNo; }
    public Payload getPayload() { return payload; }
    public void setPayload(Payload payload) { this.payload = payload; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payload {
        private String appVersion;
        private String deviceModel;
        private String imeiNo;
        private String levelCode;
        private String fullName;
        private String fatherName;
        private String mobileNumber;
        @JsonProperty("permenantAddress")
        private String permanentAddress;
        private String presentAddress;
        private String gender;
        private String nidNo;
        private String dob;
        private String nidIssuanceDate;
        private String cityId;
        private String pin;
        private String confirmMpin;
        private String parentAgentId;
        /** Percentage the parent earns on this sub-agent (AgentApp TBL_AGENT_COMMISSION_DISTRIBUTION). */
        private String parentCommission;
        private String businessName;
        private String businessTypeId;
        private String businessAddress;
        private String expectedMonthlyVolumeId;
        private List<PartnerCredential> partners = new ArrayList<>();

        public String getAppVersion() { return appVersion; }
        public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
        public String getDeviceModel() { return deviceModel; }
        public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
        public String getImeiNo() { return imeiNo; }
        public void setImeiNo(String imeiNo) { this.imeiNo = imeiNo; }
        public String getLevelCode() { return levelCode; }
        public void setLevelCode(String levelCode) { this.levelCode = levelCode; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getFatherName() { return fatherName; }
        public void setFatherName(String fatherName) { this.fatherName = fatherName; }
        public String getMobileNumber() { return mobileNumber; }
        public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
        public String getPermanentAddress() { return permanentAddress; }
        public void setPermanentAddress(String permanentAddress) { this.permanentAddress = permanentAddress; }
        public String getPresentAddress() { return presentAddress; }
        public void setPresentAddress(String presentAddress) { this.presentAddress = presentAddress; }
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
        public String getNidNo() { return nidNo; }
        public void setNidNo(String nidNo) { this.nidNo = nidNo; }
        public String getDob() { return dob; }
        public void setDob(String dob) { this.dob = dob; }
        public String getNidIssuanceDate() { return nidIssuanceDate; }
        public void setNidIssuanceDate(String nidIssuanceDate) { this.nidIssuanceDate = nidIssuanceDate; }
        public String getCityId() { return cityId; }
        public void setCityId(String cityId) { this.cityId = cityId; }
        public String getPin() { return pin; }
        public void setPin(String pin) { this.pin = pin; }
        public String getConfirmMpin() { return confirmMpin; }
        public void setConfirmMpin(String confirmMpin) { this.confirmMpin = confirmMpin; }
        public String getParentAgentId() { return parentAgentId; }
        public void setParentAgentId(String parentAgentId) { this.parentAgentId = parentAgentId; }
        public String getParentCommission() { return parentCommission; }
        public void setParentCommission(String parentCommission) { this.parentCommission = parentCommission; }
        public String getBusinessName() { return businessName; }
        public void setBusinessName(String businessName) { this.businessName = businessName; }
        public String getBusinessTypeId() { return businessTypeId; }
        public void setBusinessTypeId(String businessTypeId) { this.businessTypeId = businessTypeId; }
        public String getBusinessAddress() { return businessAddress; }
        public void setBusinessAddress(String businessAddress) { this.businessAddress = businessAddress; }
        public String getExpectedMonthlyVolumeId() { return expectedMonthlyVolumeId; }
        public void setExpectedMonthlyVolumeId(String expectedMonthlyVolumeId) {
            this.expectedMonthlyVolumeId = expectedMonthlyVolumeId;
        }
        public List<PartnerCredential> getPartners() { return partners; }
        public void setPartners(List<PartnerCredential> partners) { this.partners = partners; }
    }

    public static class PartnerCredential {
        private String email;
        private String password;

        public PartnerCredential() {}
        public PartnerCredential(String email, String password) {
            this.email = email;
            this.password = password;
        }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
