package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class LiveBillPaymentRequest {
    @NotBlank
    private String utilityCompanyCode;
    @NotBlank
    private String consumerNo;
    @NotBlank
    private String amount;
    private String transactionReference;
    private String beneficiaryName;
    private String notes;

    public String getUtilityCompanyCode() { return utilityCompanyCode; }
    public void setUtilityCompanyCode(String utilityCompanyCode) { this.utilityCompanyCode = utilityCompanyCode; }
    public String getConsumerNo() { return consumerNo; }
    public void setConsumerNo(String consumerNo) { this.consumerNo = consumerNo; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
