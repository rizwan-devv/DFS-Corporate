package com.dfs.corporate.web.dto;

public class AppKycVideoVerificationResponse {
    private AppKycSessionResponse session;
    private String message;

    public AppKycVideoVerificationResponse() {}

    public AppKycVideoVerificationResponse(AppKycSessionResponse session, String message) {
        this.session = session;
        this.message = message;
    }

    public AppKycSessionResponse getSession() { return session; }
    public void setSession(AppKycSessionResponse session) { this.session = session; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
