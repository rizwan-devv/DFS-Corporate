package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class ApprovalDecisionRequest {
    @NotBlank
    private String decision;
    private String comment;
    /** Customer MPIN for live FT on Releaser approve. Not stored. */
    private String mpin;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getMpin() { return mpin; }
    public void setMpin(String mpin) { this.mpin = mpin; }
}
