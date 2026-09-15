package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class CmsCardInquiryRequest {
    @NotBlank
    private String relationshipNum;
    /** When present, CMS returns unmasked PAN/CVV — audited on corporate side. */
    private String pin;

    public String getRelationshipNum() { return relationshipNum; }
    public void setRelationshipNum(String relationshipNum) { this.relationshipNum = relationshipNum; }
    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
}
