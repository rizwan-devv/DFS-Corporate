package com.dfs.corporate.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * OTP verify success: session + DFS getAllLovs payload (city, occupation, …).
 */
public class AppKycOtpVerifyResponse {
    private AppKycSessionResponse session;
    /** Full DFS getAllLovs JSON: responsecode, data, messages */
    private JsonNode lovs;

    public AppKycOtpVerifyResponse() {}

    public AppKycOtpVerifyResponse(AppKycSessionResponse session, JsonNode lovs) {
        this.session = session;
        this.lovs = lovs;
    }

    public AppKycSessionResponse getSession() { return session; }
    public void setSession(AppKycSessionResponse session) { this.session = session; }
    public JsonNode getLovs() { return lovs; }
    public void setLovs(JsonNode lovs) { this.lovs = lovs; }
}
