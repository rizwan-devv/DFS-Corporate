package com.dfs.corporate.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "entity_onboarding_documents")
public class EntityOnboardingDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "document_code", nullable = false, length = 64)
    private String documentCode;

    @Column(name = "document_label", nullable = false, length = 200)
    private String documentLabel;

    @Column(nullable = false)
    private boolean mandatory = true;

    @Column(nullable = false, length = 20)
    private String requirement = "REQUIRED";

    @Column(name = "one_of_group", nullable = false)
    private int oneOfGroup;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getDocumentCode() { return documentCode; }
    public void setDocumentCode(String documentCode) { this.documentCode = documentCode; }
    public String getDocumentLabel() { return documentLabel; }
    public void setDocumentLabel(String documentLabel) { this.documentLabel = documentLabel; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public int getOneOfGroup() { return oneOfGroup; }
    public void setOneOfGroup(int oneOfGroup) { this.oneOfGroup = oneOfGroup; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
