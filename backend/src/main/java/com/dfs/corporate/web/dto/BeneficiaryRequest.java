package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BeneficiaryRequest {

    @NotBlank
    @Size(max = 120)
    private String aliasName;

    @NotBlank
    @Size(max = 200)
    private String fullName;

    @Size(max = 64)
    private String accountNumber;

    @Size(max = 200)
    private String bankName;

    @Size(max = 64)
    private String raastId;

    @Size(max = 40)
    private String mobile;

    @Size(max = 40)
    private String cnic;

    /** FT | IBFT | FT_IBFT | RAAST */
    @NotBlank
    @Size(max = 16)
    private String railScope;

    private Boolean active;

    @Size(max = 500)
    private String notes;

    public String getAliasName() { return aliasName; }
    public void setAliasName(String aliasName) { this.aliasName = aliasName; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getRaastId() { return raastId; }
    public void setRaastId(String raastId) { this.raastId = raastId; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getCnic() { return cnic; }
    public void setCnic(String cnic) { this.cnic = cnic; }
    public String getRailScope() { return railScope; }
    public void setRailScope(String railScope) { this.railScope = railScope; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
