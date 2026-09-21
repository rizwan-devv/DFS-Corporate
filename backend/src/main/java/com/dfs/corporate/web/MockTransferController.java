package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.MockTransferService;
import com.dfs.corporate.web.dto.MockTransferResponse;
import com.dfs.corporate.web.dto.MockTransferSingleRequest;
import com.dfs.corporate.web.dto.MockUbpFetchRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transfers/mock")
public class MockTransferController {

    private final MockTransferService mockTransferService;

    public MockTransferController(MockTransferService mockTransferService) {
        this.mockTransferService = mockTransferService;
    }

    @GetMapping
    public List<MockTransferResponse> list(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) String productType
    ) {
        return mockTransferService.list(principal, productType);
    }

    @GetMapping("/ubp/catalog")
    public Map<String, Object> ubpCatalog(@AuthenticationPrincipal AccountPrincipal principal) {
        return mockTransferService.ubpCatalog(principal);
    }

    @PostMapping("/ubp/fetch")
    public Map<String, Object> ubpFetch(@AuthenticationPrincipal AccountPrincipal principal,
                                        @Valid @RequestBody MockUbpFetchRequest req) {
        return mockTransferService.fetchUbpBill(principal, req);
    }

    @PostMapping("/single")
    public MockTransferResponse single(@AuthenticationPrincipal AccountPrincipal principal,
                                       @Valid @RequestBody MockTransferSingleRequest req) {
        return mockTransferService.createSingle(principal, req);
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MockTransferResponse bulk(@AuthenticationPrincipal AccountPrincipal principal,
                                     @RequestParam String productType,
                                     @RequestParam("file") MultipartFile file) {
        return mockTransferService.createBulk(principal, productType, file);
    }

    @GetMapping("/template.csv")
    public ResponseEntity<byte[]> template(@RequestParam(required = false) String productType) {
        String fileName = "UBP".equalsIgnoreCase(productType)
                ? "dfs-ubp-bulk-template.csv"
                : "dfs-bulk-transfer-template.csv";
        byte[] body = mockTransferService.csvTemplate(productType).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "mock", true,
                "message", "Portal mock transfers — not connected to live AgentApp rails",
                "products", List.of("FT", "IBFT", "UBP", "RAAST"),
                "bulkProducts", List.of("FT", "IBFT", "UBP"),
                "ubp", "Pakistan utility bill payment mock (electricity, gas, internet, tickets, …)",
                "raastBulk", false
        );
    }
}
