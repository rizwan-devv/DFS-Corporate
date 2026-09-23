package com.dfs.corporate.web.dto;

import java.time.Instant;

public class LiveBulkRowResponse {
    private int lineNo;
    private String status;
    private String beneficiaryAccount;
    private String bankImd;
    private String utilityCompanyCode;
    private String consumerNo;
    private String amount;
    private String narration;
    private String responseCode;
    private String responseMessage;
    private String txnRef;
    private Instant processedAt;

    public int getLineNo() { return lineNo; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBeneficiaryAccount() { return beneficiaryAccount; }
    public void setBeneficiaryAccount(String beneficiaryAccount) { this.beneficiaryAccount = beneficiaryAccount; }
    public String getBankImd() { return bankImd; }
    public void setBankImd(String bankImd) { this.bankImd = bankImd; }
    public String getUtilityCompanyCode() { return utilityCompanyCode; }
    public void setUtilityCompanyCode(String utilityCompanyCode) { this.utilityCompanyCode = utilityCompanyCode; }
    public String getConsumerNo() { return consumerNo; }
    public void setConsumerNo(String consumerNo) { this.consumerNo = consumerNo; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }
    public String getTxnRef() { return txnRef; }
    public void setTxnRef(String txnRef) { this.txnRef = txnRef; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
