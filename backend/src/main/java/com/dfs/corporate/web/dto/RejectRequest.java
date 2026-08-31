package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class RejectRequest {
    @NotBlank
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
