package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "employee_bulk_batches")
public class EmployeeBulkBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EmployeeBulkBatchStatus status = EmployeeBulkBatchStatus.DRAFT;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    @Column(name = "parked_rows", nullable = false)
    private Integer parkedRows = 0;

    @Column(name = "open_rows", nullable = false)
    private Integer openRows = 0;

    @Column(name = "failed_rows", nullable = false)
    private Integer failedRows = 0;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "parked_at")
    private Instant parkedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public EmployeeBulkBatchStatus getStatus() { return status; }
    public void setStatus(EmployeeBulkBatchStatus status) { this.status = status; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }
    public Integer getParkedRows() { return parkedRows; }
    public void setParkedRows(Integer parkedRows) { this.parkedRows = parkedRows; }
    public Integer getOpenRows() { return openRows; }
    public void setOpenRows(Integer openRows) { this.openRows = openRows; }
    public Integer getFailedRows() { return failedRows; }
    public void setFailedRows(Integer failedRows) { this.failedRows = failedRows; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getParkedAt() { return parkedAt; }
    public void setParkedAt(Instant parkedAt) { this.parkedAt = parkedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
