package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.MockTransfer;
import com.dfs.corporate.domain.MockTransferMode;
import com.dfs.corporate.domain.MockTransferProduct;

import java.math.BigDecimal;
import java.time.Instant;

public class MockTransferResponse {
    private Long id;
    private String publicId;
    private MockTransferProduct productType;
    private MockTransferMode mode;
    private String status;
    private String mockTxnRef;
    private String accountNumber;
    private String ipin;
    private String bankName;
    private BigDecimal amount;
    private String cnic;
    private String mobile;
    private String beneficiaryName;
    private String notes;
    private String bulkFileName;
    private Integer bulkRowCount;
    private String bulkSummary;
    private String raastQrPayload;
    private String createdBy;
    private Instant createdAt;
    /** Convenience: SVG data URL for Raast QR mock display */
    private String raastQrDataUrl;

    public static MockTransferResponse from(MockTransfer t) {
        MockTransferResponse r = new MockTransferResponse();
        r.id = t.getId();
        r.publicId = t.getPublicId();
        r.productType = t.getProductType();
        r.mode = t.getMode();
        r.status = t.getStatus();
        r.mockTxnRef = t.getMockTxnRef();
        r.accountNumber = t.getAccountNumber();
        r.ipin = t.getIpin();
        r.bankName = t.getBankName();
        r.amount = t.getAmount();
        r.cnic = t.getCnic();
        r.mobile = t.getMobile();
        r.beneficiaryName = t.getBeneficiaryName();
        r.notes = t.getNotes();
        r.bulkFileName = t.getBulkFileName();
        r.bulkRowCount = t.getBulkRowCount();
        r.bulkSummary = t.getBulkSummary();
        r.raastQrPayload = t.getRaastQrPayload();
        r.createdBy = t.getCreatedBy();
        r.createdAt = t.getCreatedAt();
        if (t.getRaastQrPayload() != null && !t.getRaastQrPayload().isBlank()) {
            r.raastQrDataUrl = mockQrDataUrl(t.getRaastQrPayload());
        }
        return r;
    }

    /** Tiny placeholder QR-looking SVG (not a real QR codec — portal mock only). */
    public static String mockQrDataUrl(String payload) {
        String safe = payload == null ? "RAAST" : payload.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200">
                  <rect width="200" height="200" fill="#fff"/>
                  <rect x="12" y="12" width="48" height="48" fill="#111"/>
                  <rect x="20" y="20" width="32" height="32" fill="#fff"/>
                  <rect x="28" y="28" width="16" height="16" fill="#111"/>
                  <rect x="140" y="12" width="48" height="48" fill="#111"/>
                  <rect x="148" y="20" width="32" height="32" fill="#fff"/>
                  <rect x="156" y="28" width="16" height="16" fill="#111"/>
                  <rect x="12" y="140" width="48" height="48" fill="#111"/>
                  <rect x="20" y="148" width="32" height="32" fill="#fff"/>
                  <rect x="28" y="156" width="16" height="16" fill="#111"/>
                  <rect x="80" y="80" width="16" height="16" fill="#111"/>
                  <rect x="100" y="80" width="16" height="16" fill="#111"/>
                  <rect x="80" y="100" width="16" height="16" fill="#111"/>
                  <rect x="120" y="100" width="16" height="16" fill="#111"/>
                  <rect x="100" y="120" width="16" height="16" fill="#111"/>
                  <rect x="140" y="140" width="16" height="16" fill="#111"/>
                  <rect x="160" y="160" width="16" height="16" fill="#111"/>
                  <text x="100" y="195" text-anchor="middle" font-size="8" fill="#666">MOCK RAAST</text>
                  <title>%s</title>
                </svg>
                """.formatted(safe);
        return "data:image/svg+xml;utf8," + java.net.URLEncoder.encode(svg, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public MockTransferProduct getProductType() { return productType; }
    public MockTransferMode getMode() { return mode; }
    public String getStatus() { return status; }
    public String getMockTxnRef() { return mockTxnRef; }
    public String getAccountNumber() { return accountNumber; }
    public String getIpin() { return ipin; }
    public String getBankName() { return bankName; }
    public BigDecimal getAmount() { return amount; }
    public String getCnic() { return cnic; }
    public String getMobile() { return mobile; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public String getNotes() { return notes; }
    public String getBulkFileName() { return bulkFileName; }
    public Integer getBulkRowCount() { return bulkRowCount; }
    public String getBulkSummary() { return bulkSummary; }
    public String getRaastQrPayload() { return raastQrPayload; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getRaastQrDataUrl() { return raastQrDataUrl; }
}
