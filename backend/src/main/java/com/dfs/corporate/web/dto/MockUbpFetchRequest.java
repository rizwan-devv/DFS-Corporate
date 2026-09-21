package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class MockUbpFetchRequest {
    @NotBlank
    private String ubpCategory;
    @NotBlank
    private String ubpCompany;
    @NotBlank
    private String consumerNumber;

    public String getUbpCategory() { return ubpCategory; }
    public void setUbpCategory(String ubpCategory) { this.ubpCategory = ubpCategory; }
    public String getUbpCompany() { return ubpCompany; }
    public void setUbpCompany(String ubpCompany) { this.ubpCompany = ubpCompany; }
    public String getConsumerNumber() { return consumerNumber; }
    public void setConsumerNumber(String consumerNumber) { this.consumerNumber = consumerNumber; }
}
