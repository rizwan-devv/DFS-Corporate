package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class CmsCardStatusUpdateRequest {
    @NotBlank
    private String cardStatusCode;

    public String getCardStatusCode() { return cardStatusCode; }
    public void setCardStatusCode(String cardStatusCode) { this.cardStatusCode = cardStatusCode; }
}
