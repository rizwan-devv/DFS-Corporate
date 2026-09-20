package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.MockTransferRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.MockTransferResponse;
import com.dfs.corporate.web.dto.MockTransferSingleRequest;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Portal-only mock FT / IBFT / UBP / Raast — no live AgentApp rails.
 * Bulk = CSV (Excel-compatible); Raast = single + mock QR only.
 */
@Service
public class MockTransferService {

    private final MockTransferRepository transferRepository;
    private final PartyRepository partyRepository;

    public MockTransferService(MockTransferRepository transferRepository, PartyRepository partyRepository) {
        this.transferRepository = transferRepository;
        this.partyRepository = partyRepository;
    }

    public List<MockTransferResponse> list(AccountPrincipal principal) {
        requireActiveParty(principal);
        return transferRepository.findByPartyIdOrderByCreatedAtDesc(principal.getPartyId()).stream()
                .map(MockTransferResponse::from)
                .toList();
    }

    @Transactional
    public MockTransferResponse createSingle(AccountPrincipal principal, MockTransferSingleRequest req) {
        Party party = requireActiveParty(principal);
        MockTransferProduct product = parseProduct(req.getProductType());
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
        if (product != MockTransferProduct.RAAST) {
            if (isBlank(req.getAccountNumber())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Account number is required");
            }
        }

        MockTransfer t = base(party, principal, product, MockTransferMode.SINGLE);
        t.setAccountNumber(trim(req.getAccountNumber()));
        t.setIpin(trim(req.getIpin()));
        t.setBankName(trim(req.getBankName()));
        t.setAmount(req.getAmount());
        t.setCnic(trim(req.getCnic()));
        t.setMobile(trim(req.getMobile()));
        t.setBeneficiaryName(trim(req.getBeneficiaryName()));
        t.setNotes(trim(req.getNotes()));
        if (product == MockTransferProduct.RAAST) {
            String payload = "RAAST|MOCK|" + t.getMockTxnRef() + "|AMT:" + req.getAmount()
                    + (party.getDfsAccountId() != null ? "|AID:" + party.getDfsAccountId() : "")
                    + (party.getPhone() != null ? "|MOB:" + party.getPhone() : "");
            t.setRaastQrPayload(payload);
            t.setNotes(t.getNotes() != null ? t.getNotes() : "Mock Raast payment request with QR");
        }
        t.setStatus("MOCK_SUCCESS");
        return MockTransferResponse.from(transferRepository.save(t));
    }

    @Transactional
    public MockTransferResponse createBulk(AccountPrincipal principal, String productType, MultipartFile file) {
        Party party = requireActiveParty(principal);
        MockTransferProduct product = parseProduct(productType);
        if (product == MockTransferProduct.RAAST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Raast bulk is not supported — single + QR only");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV/Excel file is required");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.csv";
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".txt")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Upload a CSV template (.csv). Excel: Save As → CSV, then upload.");
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
            }
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read file: " + e.getMessage());
        }
        if (lines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        int dataRows = lines.size();
        // Skip header if it looks like one
        String first = lines.get(0).toLowerCase(Locale.ROOT);
        if (first.contains("account") || first.contains("amount") || first.contains("cnic")) {
            dataRows = Math.max(0, lines.size() - 1);
        }
        if (dataRows == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No data rows found (only header?)");
        }

        BigDecimal total = BigDecimal.ZERO;
        int preview = 0;
        StringBuilder summary = new StringBuilder();
        summary.append("Mock bulk ").append(product).append(" — ").append(dataRows).append(" row(s).\n");
        int start = lines.size() - dataRows;
        for (int i = start; i < lines.size() && preview < 5; i++) {
            String[] cols = lines.get(i).split("[,;\\t]", -1);
            summary.append("• ").append(lines.get(i)).append('\n');
            if (cols.length >= 4) {
                try {
                    total = total.add(new BigDecimal(cols[3].trim().replace(",", "")));
                } catch (Exception ignored) {
                    // mock — ignore parse errors on amount column
                }
            }
            preview++;
        }
        if (dataRows > 5) {
            summary.append("… and ").append(dataRows - 5).append(" more row(s).\n");
        }

        MockTransfer t = base(party, principal, product, MockTransferMode.BULK);
        t.setBulkFileName(name);
        t.setBulkRowCount(dataRows);
        t.setBulkSummary(summary.toString());
        t.setAmount(total.compareTo(BigDecimal.ZERO) > 0 ? total : null);
        t.setNotes("Portal mock bulk — not sent to AgentApp");
        t.setStatus("MOCK_SUCCESS");
        return MockTransferResponse.from(transferRepository.save(t));
    }

    public String csvTemplate() {
        return "account_number,ipin,bank_name,amount,cnic,mobile\n"
                + "1234567890123,1234,Mock Bank,1000.00,3520212345671,03001234567\n"
                + "9876543210987,5678,Demo Bank,2500.50,4220112345678,03009876543\n";
    }

    private MockTransfer base(Party party, AccountPrincipal principal,
                              MockTransferProduct product, MockTransferMode mode) {
        MockTransfer t = new MockTransfer();
        t.setPublicId(UUID.randomUUID().toString());
        t.setPartyId(party.getId());
        t.setProductType(product);
        t.setMode(mode);
        t.setMockTxnRef(mockRef(product));
        t.setCreatedBy(principal.getUsername());
        return t;
    }

    private String mockRef(MockTransferProduct product) {
        int n = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "MOCK-" + product.name() + "-" + n;
    }

    private MockTransferProduct parseProduct(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "productType is required (FT, IBFT, UBP, RAAST)");
        }
        try {
            return MockTransferProduct.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid productType: " + raw);
        }
    }

    private Party requireActiveParty(AccountPrincipal principal) {
        if (principal.getPartyId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Merchant party required");
        }
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Transfers available after entity is ACTIVE");
        }
        return party;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
