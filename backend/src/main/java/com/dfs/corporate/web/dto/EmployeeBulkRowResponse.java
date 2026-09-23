package com.dfs.corporate.web.dto;

import java.time.Instant;

public class EmployeeBulkRowResponse {
    private String publicId;
    private int lineNo;
    private String status;
    private String employeeCode;
    private String fullName;
    private String fatherName;
    private String mobile;
    private String cnic;
    private String dateOfBirth;
    private String gender;
    private String email;
    private String department;
    private String parkRef;
    private String dfsAccountNo;
    private String dfsCustomerId;
    private String responseMessage;
    private Instant parkedAt;
    private Instant confirmedAt;

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getFatherName() { return fatherName; }
    public void setFatherName(String fatherName) { this.fatherName = fatherName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getCnic() { return cnic; }
    public void setCnic(String cnic) { this.cnic = cnic; }
    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getParkRef() { return parkRef; }
    public void setParkRef(String parkRef) { this.parkRef = parkRef; }
    public String getDfsAccountNo() { return dfsAccountNo; }
    public void setDfsAccountNo(String dfsAccountNo) { this.dfsAccountNo = dfsAccountNo; }
    public String getDfsCustomerId() { return dfsCustomerId; }
    public void setDfsCustomerId(String dfsCustomerId) { this.dfsCustomerId = dfsCustomerId; }
    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }
    public Instant getParkedAt() { return parkedAt; }
    public void setParkedAt(Instant parkedAt) { this.parkedAt = parkedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
