package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class FranchiseTxnPostRequest {

    @NotNull
    private Long childPartyId;

    @NotBlank
    private String externalTxnRef;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal grossAmount;

    private String currency = "PKR";
    private String txnType = "INCOMING";
    private String source = "API";
    private String notes;

    public Long getChildPartyId() { return childPartyId; }
    public void setChildPartyId(Long childPartyId) { this.childPartyId = childPartyId; }
    public String getExternalTxnRef() { return externalTxnRef; }
    public void setExternalTxnRef(String externalTxnRef) { this.externalTxnRef = externalTxnRef; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getTxnType() { return txnType; }
    public void setTxnType(String txnType) { this.txnType = txnType; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
