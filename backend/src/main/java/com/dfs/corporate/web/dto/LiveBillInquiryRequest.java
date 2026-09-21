package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class LiveBillInquiryRequest {
    @NotBlank
    private String utilityCompanyCode;
    @NotBlank
    private String consumerNo;

    public String getUtilityCompanyCode() { return utilityCompanyCode; }
    public void setUtilityCompanyCode(String utilityCompanyCode) { this.utilityCompanyCode = utilityCompanyCode; }
    public String getConsumerNo() { return consumerNo; }
    public void setConsumerNo(String consumerNo) { this.consumerNo = consumerNo; }
}
