package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class LiveFtInitiateRequest {
    /** Beneficiary DFS wallet mobile / account */
    @NotBlank
    private String accountNo;
    @NotBlank
    private String amount;
    /** W = wallet (default) */
    private String accountType = "W";

    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
}
