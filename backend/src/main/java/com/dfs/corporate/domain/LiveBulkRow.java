package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "live_bulk_rows")
public class LiveBulkRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LiveBulkRowStatus status = LiveBulkRowStatus.PENDING;

    @Column(name = "beneficiary_account", length = 64)
    private String beneficiaryAccount;

    @Column(name = "bank_imd", length = 32)
    private String bankImd;

    @Column(name = "utility_company_code", length = 64)
    private String utilityCompanyCode;

    @Column(name = "consumer_no", length = 64)
    private String consumerNo;

    @Column(name = "amount", length = 32)
    private String amount;

    @Column(name = "narration", length = 500)
    private String narration;

    @Column(name = "response_code", length = 16)
    private String responseCode;

    @Column(name = "response_message", length = 1000)
    private String responseMessage;

    @Column(name = "txn_ref", length = 120)
    private String txnRef;

    @Column(name = "raw_line", length = 1000)
    private String rawLine;

    @Column(name = "processed_at")
    private Instant processedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public LiveBulkRowStatus getStatus() { return status; }
    public void setStatus(LiveBulkRowStatus status) { this.status = status; }
    public String getBeneficiaryAccount() { return beneficiaryAccount; }
    public void setBeneficiaryAccount(String beneficiaryAccount) { this.beneficiaryAccount = beneficiaryAccount; }
    public String getBankImd() { return bankImd; }
    public void setBankImd(String bankImd) { this.bankImd = bankImd; }
    public String getUtilityCompanyCode() { return utilityCompanyCode; }
    public void setUtilityCompanyCode(String utilityCompanyCode) { this.utilityCompanyCode = utilityCompanyCode; }
    public String getConsumerNo() { return consumerNo; }
    public void setConsumerNo(String consumerNo) { this.consumerNo = consumerNo; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }
    public String getTxnRef() { return txnRef; }
    public void setTxnRef(String txnRef) { this.txnRef = txnRef; }
    public String getRawLine() { return rawLine; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
