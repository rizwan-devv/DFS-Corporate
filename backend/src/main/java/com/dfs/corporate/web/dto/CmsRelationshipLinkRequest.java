package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class CmsRelationshipLinkRequest {

    @NotBlank
    private String relationshipNum;

    public String getRelationshipNum() { return relationshipNum; }
    public void setRelationshipNum(String relationshipNum) { this.relationshipNum = relationshipNum; }
}
