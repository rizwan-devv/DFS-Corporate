package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class LiveIbftAdviceRequest {
    @NotBlank
    private String beneficiaryAccountNo;
    @NotBlank
    private String beneficiaryBankImd;
    @NotBlank
    private String amount;
    @NotBlank
    private String purposeOfPayment;
    private String transactionReference;
    private String beneficiaryName;
    private String notes;

    public String getBeneficiaryAccountNo() { return beneficiaryAccountNo; }
    public void setBeneficiaryAccountNo(String beneficiaryAccountNo) { this.beneficiaryAccountNo = beneficiaryAccountNo; }
    public String getBeneficiaryBankImd() { return beneficiaryBankImd; }
    public void setBeneficiaryBankImd(String beneficiaryBankImd) { this.beneficiaryBankImd = beneficiaryBankImd; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getPurposeOfPayment() { return purposeOfPayment; }
    public void setPurposeOfPayment(String purposeOfPayment) { this.purposeOfPayment = purposeOfPayment; }
    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
