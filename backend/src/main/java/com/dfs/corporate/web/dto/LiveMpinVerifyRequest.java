package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class LiveMpinVerifyRequest {
    @NotBlank
    private String mpin;

    public String getMpin() { return mpin; }
    public void setMpin(String mpin) { this.mpin = mpin; }
}
