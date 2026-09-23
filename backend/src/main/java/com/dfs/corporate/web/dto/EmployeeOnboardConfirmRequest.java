package com.dfs.corporate.web.dto;

public class EmployeeOnboardConfirmRequest {
    /** Our row publicId (preferred). */
    private String rowPublicId;
    /** Optional alternate: park_ref returned when parked. */
    private String parkRef;
    /** OPEN | FAILED | REJECTED */
    private String status;
    private String dfsAccountNo;
    private String dfsCustomerId;
    private String message;

    public String getRowPublicId() { return rowPublicId; }
    public void setRowPublicId(String rowPublicId) { this.rowPublicId = rowPublicId; }
    public String getParkRef() { return parkRef; }
    public void setParkRef(String parkRef) { this.parkRef = parkRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDfsAccountNo() { return dfsAccountNo; }
    public void setDfsAccountNo(String dfsAccountNo) { this.dfsAccountNo = dfsAccountNo; }
    public String getDfsCustomerId() { return dfsCustomerId; }
    public void setDfsCustomerId(String dfsCustomerId) { this.dfsCustomerId = dfsCustomerId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
