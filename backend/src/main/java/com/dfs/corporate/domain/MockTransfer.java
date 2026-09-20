package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "mock_transfers")
public class MockTransfer {

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
    @Column(nullable = false, length = 16)
    private MockTransferMode mode;

    @Column(nullable = false, length = 32)
    private String status = "MOCK_SUCCESS";

    @Column(name = "mock_txn_ref", nullable = false, length = 64)
    private String mockTxnRef;

    @Column(name = "account_number", length = 64)
    private String accountNumber;

    @Column(length = 64)
    private String ipin;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 40)
    private String cnic;

    @Column(length = 40)
    private String mobile;

    @Column(name = "beneficiary_name", length = 200)
    private String beneficiaryName;

    @Column(length = 1000)
    private String notes;

    @Column(name = "bulk_file_name", length = 255)
    private String bulkFileName;

    @Column(name = "bulk_row_count")
    private Integer bulkRowCount;

    @Column(name = "bulk_summary", columnDefinition = "TEXT")
    private String bulkSummary;

    @Column(name = "raast_qr_payload", length = 1000)
    private String raastQrPayload;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public MockTransferProduct getProductType() { return productType; }
    public void setProductType(MockTransferProduct productType) { this.productType = productType; }
    public MockTransferMode getMode() { return mode; }
    public void setMode(MockTransferMode mode) { this.mode = mode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMockTxnRef() { return mockTxnRef; }
    public void setMockTxnRef(String mockTxnRef) { this.mockTxnRef = mockTxnRef; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getIpin() { return ipin; }
    public void setIpin(String ipin) { this.ipin = ipin; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCnic() { return cnic; }
    public void setCnic(String cnic) { this.cnic = cnic; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getBulkFileName() { return bulkFileName; }
    public void setBulkFileName(String bulkFileName) { this.bulkFileName = bulkFileName; }
    public Integer getBulkRowCount() { return bulkRowCount; }
    public void setBulkRowCount(Integer bulkRowCount) { this.bulkRowCount = bulkRowCount; }
    public String getBulkSummary() { return bulkSummary; }
    public void setBulkSummary(String bulkSummary) { this.bulkSummary = bulkSummary; }
    public String getRaastQrPayload() { return raastQrPayload; }
    public void setRaastQrPayload(String raastQrPayload) { this.raastQrPayload = raastQrPayload; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
