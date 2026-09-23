package com.dfs.corporate.web.dto;

import java.time.Instant;
import java.util.List;

public class LiveBulkBatchResponse {
    private String publicId;
    private String productType;
    private String status;
    private String fileName;
    private int totalRows;
    private int successRows;
    private int failedRows;
    private String errorMessage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private List<LiveBulkRowResponse> rows;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getSuccessRows() { return successRows; }
    public void setSuccessRows(int successRows) { this.successRows = successRows; }
    public int getFailedRows() { return failedRows; }
    public void setFailedRows(int failedRows) { this.failedRows = failedRows; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public List<LiveBulkRowResponse> getRows() { return rows; }
    public void setRows(List<LiveBulkRowResponse> rows) { this.rows = rows; }
}
