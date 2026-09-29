package com.dfs.corporate.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "required_documents")
public class RequiredDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 32)
    private PartyType partyType;

    @Column(name = "document_code", nullable = false, length = 64)
    private String documentCode;

    @Column(name = "document_label", nullable = false, length = 200)
    private String documentLabel;

    @Column(nullable = false)
    private boolean mandatory = true;

    public Long getId() { return id; }
    public PartyType getPartyType() { return partyType; }
    public void setPartyType(PartyType partyType) { this.partyType = partyType; }
    public String getDocumentCode() { return documentCode; }
    public void setDocumentCode(String documentCode) { this.documentCode = documentCode; }
    public String getDocumentLabel() { return documentLabel; }
    public void setDocumentLabel(String documentLabel) { this.documentLabel = documentLabel; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
}
