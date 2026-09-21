package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class LiveFtConfirmRequest {
    @NotBlank
    private String accountNo;
    @NotBlank
    private String amount;
    private String accountType = "W";
    /** Customer wallet MPIN — verified by DFS before money moves */
    @NotBlank
    private String mpin;
    /** Override payer APP_USER_ID if party.dfs_app_user_id is empty */
    private String appUserId;
    private String transPurposeId = "1";
    private String narration;
    private String beneficiaryName;

    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getMpin() { return mpin; }
    public void setMpin(String mpin) { this.mpin = mpin; }
    public String getAppUserId() { return appUserId; }
    public void setAppUserId(String appUserId) { this.appUserId = appUserId; }
    public String getTransPurposeId() { return transPurposeId; }
    public void setTransPurposeId(String transPurposeId) { this.transPurposeId = transPurposeId; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
}
