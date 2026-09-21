package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "aml_watchlist")
public class AmlWatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_source", nullable = false, length = 16)
    private AmlListSource listSource;

    @Column(name = "list_name", length = 120)
    private String listName;

    @Column(name = "full_name", nullable = false, length = 300)
    private String fullName;

    @Column(length = 20)
    private String cnic;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    @Column(name = "source_version", length = 80)
    private String sourceVersion;

    public Long getId() { return id; }
    public AmlListSource getListSource() { return listSource; }
    public void setListSource(AmlListSource listSource) { this.listSource = listSource; }
    public String getListName() { return listName; }
    public void setListName(String listName) { this.listName = listName; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getCnic() { return cnic; }
    public void setCnic(String cnic) { this.cnic = cnic; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
}
