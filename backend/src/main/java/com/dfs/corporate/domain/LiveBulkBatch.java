package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "live_bulk_batches")
public class LiveBulkBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 16)
    private MockTransferProduct productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LiveBulkBatchStatus status = LiveBulkBatchStatus.DRAFT;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    @Column(name = "success_rows", nullable = false)
    private Integer successRows = 0;

    @Column(name = "failed_rows", nullable = false)
    private Integer failedRows = 0;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public MockTransferProduct getProductType() { return productType; }
    public void setProductType(MockTransferProduct productType) { this.productType = productType; }
    public LiveBulkBatchStatus getStatus() { return status; }
    public void setStatus(LiveBulkBatchStatus status) { this.status = status; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }
    public Integer getSuccessRows() { return successRows; }
    public void setSuccessRows(Integer successRows) { this.successRows = successRows; }
    public Integer getFailedRows() { return failedRows; }
    public void setFailedRows(Integer failedRows) { this.failedRows = failedRows; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
