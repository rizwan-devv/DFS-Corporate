package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class MockTransferSingleRequest {
    @NotBlank
    private String productType; // FT | IBFT | UBP | RAAST

    private String accountNumber;
    private String ipin;
    private String bankName;
    @NotNull
    private BigDecimal amount;
    private String cnic;
    private String mobile;
    private String beneficiaryName;
    private String notes;

    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getIpin() { return ipin; }
    public void setIpin(String ipin) { this.ipin = ipin; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCnic() { return cnic; }
    public void setCnic(String cnic) { this.cnic = cnic; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
