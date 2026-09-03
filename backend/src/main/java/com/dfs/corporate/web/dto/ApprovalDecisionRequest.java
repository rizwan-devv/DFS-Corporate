package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class ApprovalDecisionRequest {
    @NotBlank
    private String decision;
    private String comment;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
