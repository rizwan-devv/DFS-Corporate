package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class ApprovalCreateRequest {
    private String requestType;
    @NotBlank
    private String title;
    private String referenceKey;
    private String payloadJson;
    private String comment;

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getReferenceKey() { return referenceKey; }
    public void setReferenceKey(String referenceKey) { this.referenceKey = referenceKey; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
