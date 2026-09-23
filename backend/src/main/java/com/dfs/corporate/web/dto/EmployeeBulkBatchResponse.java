package com.dfs.corporate.web.dto;

import java.time.Instant;
import java.util.List;

public class EmployeeBulkBatchResponse {
    private String publicId;
    private String status;
    private String fileName;
    private int totalRows;
    private int parkedRows;
    private int openRows;
    private int failedRows;
    private String errorMessage;
    private Instant createdAt;
    private Instant parkedAt;
    private Instant finishedAt;
    private List<EmployeeBulkRowResponse> rows;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getParkedRows() { return parkedRows; }
    public void setParkedRows(int parkedRows) { this.parkedRows = parkedRows; }
    public int getOpenRows() { return openRows; }
    public void setOpenRows(int openRows) { this.openRows = openRows; }
    public int getFailedRows() { return failedRows; }
    public void setFailedRows(int failedRows) { this.failedRows = failedRows; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getParkedAt() { return parkedAt; }
    public void setParkedAt(Instant parkedAt) { this.parkedAt = parkedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public List<EmployeeBulkRowResponse> getRows() { return rows; }
    public void setRows(List<EmployeeBulkRowResponse> rows) { this.rows = rows; }
}
