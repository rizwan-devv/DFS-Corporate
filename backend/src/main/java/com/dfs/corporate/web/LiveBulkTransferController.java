package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.LiveBulkTransferService;
import com.dfs.corporate.web.dto.LiveBulkBatchResponse;
import com.dfs.corporate.web.dto.LiveBulkStartRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Live bulk FT / IBFT / UBP — separate from single {@code /api/transfers/live/ft|ibft|ubp} endpoints.
 */
@RestController
@RequestMapping("/api/transfers/live/bulk")
public class LiveBulkTransferController {

    private final LiveBulkTransferService bulkTransferService;

    public LiveBulkTransferController(LiveBulkTransferService bulkTransferService) {
        this.bulkTransferService = bulkTransferService;
    }

    @GetMapping("/template.csv")
    public ResponseEntity<byte[]> template(@RequestParam String productType) {
        String csv = bulkTransferService.templateCsv(productType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"live-bulk-" + productType.toLowerCase() + "-template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LiveBulkBatchResponse upload(@AuthenticationPrincipal AccountPrincipal principal,
                                        @RequestParam String productType,
                                        @RequestParam("file") MultipartFile file) {
        return bulkTransferService.upload(principal, productType, file);
    }

    @PostMapping("/{publicId}/start")
    public LiveBulkBatchResponse start(@AuthenticationPrincipal AccountPrincipal principal,
                                       @PathVariable String publicId,
                                       @RequestBody(required = false) LiveBulkStartRequest req) {
        return bulkTransferService.start(principal, publicId, req != null ? req : new LiveBulkStartRequest());
    }

    @GetMapping("/{publicId}")
    public LiveBulkBatchResponse get(@AuthenticationPrincipal AccountPrincipal principal,
                                     @PathVariable String publicId) {
        return bulkTransferService.get(principal, publicId);
    }

    @GetMapping
    public List<LiveBulkBatchResponse> list(@AuthenticationPrincipal AccountPrincipal principal,
                                            @RequestParam(required = false) String productType) {
        return bulkTransferService.list(principal, productType);
    }

    @GetMapping("/{publicId}/result.csv")
    public ResponseEntity<byte[]> resultCsv(@AuthenticationPrincipal AccountPrincipal principal,
                                            @PathVariable String publicId) {
        String csv = bulkTransferService.resultCsv(principal, publicId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"live-bulk-result-" + publicId + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
