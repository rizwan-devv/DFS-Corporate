package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class LiveIbftTitleRequest {
    @NotBlank
    private String beneficiaryAccountNo;
    @NotBlank
    private String beneficiaryBankImd;
    @NotBlank
    private String amount;

    public String getBeneficiaryAccountNo() { return beneficiaryAccountNo; }
    public void setBeneficiaryAccountNo(String beneficiaryAccountNo) { this.beneficiaryAccountNo = beneficiaryAccountNo; }
    public String getBeneficiaryBankImd() { return beneficiaryBankImd; }
    public void setBeneficiaryBankImd(String beneficiaryBankImd) { this.beneficiaryBankImd = beneficiaryBankImd; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
}
