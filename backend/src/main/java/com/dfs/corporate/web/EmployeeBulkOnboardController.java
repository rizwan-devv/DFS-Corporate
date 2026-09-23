package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.EmployeeBulkOnboardService;
import com.dfs.corporate.web.dto.EmployeeBulkBatchResponse;
import com.dfs.corporate.web.dto.EmployeeBulkRowResponse;
import com.dfs.corporate.web.dto.EmployeeOnboardConfirmRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Corporate employee bulk onboard — upload CSV, park on DFS, track confirmations.
 */
@RestController
@RequestMapping("/api/employees/bulk")
public class EmployeeBulkOnboardController {

    private final EmployeeBulkOnboardService service;

    public EmployeeBulkOnboardController(EmployeeBulkOnboardService service) {
        this.service = service;
    }

    @GetMapping("/template.csv")
    public ResponseEntity<byte[]> template() {
        String csv = service.templateCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"employee-bulk-template.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmployeeBulkBatchResponse upload(@AuthenticationPrincipal AccountPrincipal principal,
                                            @RequestParam("file") MultipartFile file) {
        return service.upload(principal, file);
    }

    @PostMapping("/{publicId}/park")
    public EmployeeBulkBatchResponse park(@AuthenticationPrincipal AccountPrincipal principal,
                                          @PathVariable String publicId) {
        return service.park(principal, publicId);
    }

    @GetMapping("/{publicId}")
    public EmployeeBulkBatchResponse get(@AuthenticationPrincipal AccountPrincipal principal,
                                         @PathVariable String publicId) {
        return service.get(principal, publicId);
    }

    @GetMapping
    public List<EmployeeBulkBatchResponse> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.list(principal);
    }

    @GetMapping("/{publicId}/result.csv")
    public ResponseEntity<byte[]> resultCsv(@AuthenticationPrincipal AccountPrincipal principal,
                                            @PathVariable String publicId) {
        String csv = service.resultCsv(principal, publicId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"employee-bulk-result-" + publicId + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /** Portal test helper: simulate DFS confirmation for a parked row. */
    @PostMapping("/confirm")
    public EmployeeBulkRowResponse confirm(@AuthenticationPrincipal AccountPrincipal principal,
                                           @RequestBody EmployeeOnboardConfirmRequest req) {
        return service.confirmForParty(principal, req);
    }
}
