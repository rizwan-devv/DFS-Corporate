package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class AppKycOtpVerifyRequest {
    @NotBlank
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
