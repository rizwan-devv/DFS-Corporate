package com.dfs.corporate.web.dto;

import java.time.Instant;
import java.util.List;

public class ApprovalRequestResponse {
    private String publicId;
    private String requestType;
    private String referenceKey;
    private String title;
    private String payloadJson;
    private String status;
    private String currentStep;
    private Long createdByAccountId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private List<ApprovalActionResponse> actions;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getReferenceKey() { return referenceKey; }
    public void setReferenceKey(String referenceKey) { this.referenceKey = referenceKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
    public Long getCreatedByAccountId() { return createdByAccountId; }
    public void setCreatedByAccountId(Long createdByAccountId) { this.createdByAccountId = createdByAccountId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public List<ApprovalActionResponse> getActions() { return actions; }
    public void setActions(List<ApprovalActionResponse> actions) { this.actions = actions; }
}
