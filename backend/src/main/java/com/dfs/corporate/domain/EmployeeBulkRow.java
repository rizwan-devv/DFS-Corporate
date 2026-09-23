package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "employee_bulk_rows")
public class EmployeeBulkRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EmployeeBulkRowStatus status = EmployeeBulkRowStatus.VALIDATED;

    @Column(name = "employee_code", length = 64)
    private String employeeCode;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "father_name", length = 200)
    private String fatherName;

    @Column(name = "mobile", length = 32)
    private String mobile;

    @Column(name = "cnic", length = 32)
    private String cnic;

    @Column(name = "date_of_birth", length = 32)
    private String dateOfBirth;

    @Column(name = "gender", length = 16)
    private String gender;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "department", length = 120)
    private String department;

    @Column(name = "park_ref", length = 120)
    private String parkRef;

    @Column(name = "dfs_account_no", length = 64)
    private String dfsAccountNo;

    @Column(name = "dfs_customer_id", length = 64)
    private String dfsCustomerId;

    @Column(name = "response_message", length = 1000)
    private String responseMessage;

    @Column(name = "raw_line", length = 1500)
    private String rawLine;

    @Column(name = "parked_at")
    private Instant parkedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public EmployeeBulkRowStatus getStatus() { return status; }
    public void setStatus(EmployeeBulkRowStatus status) { this.status = status; }
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
    public String getRawLine() { return rawLine; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine; }
    public Instant getParkedAt() { return parkedAt; }
    public void setParkedAt(Instant parkedAt) { this.parkedAt = parkedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
