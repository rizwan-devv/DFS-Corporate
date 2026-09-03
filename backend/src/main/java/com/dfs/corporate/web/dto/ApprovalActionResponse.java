package com.dfs.corporate.web.dto;

import java.time.Instant;

public class ApprovalActionResponse {
    private String step;
    private String decision;
    private String actorEmail;
    private String comment;
    private Instant createdAt;

    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
