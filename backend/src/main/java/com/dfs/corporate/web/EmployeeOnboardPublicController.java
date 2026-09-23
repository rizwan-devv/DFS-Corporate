package com.dfs.corporate.web;

import com.dfs.corporate.service.EmployeeBulkOnboardService;
import com.dfs.corporate.web.dto.EmployeeBulkRowResponse;
import com.dfs.corporate.web.dto.EmployeeOnboardConfirmRequest;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * DFS callback: confirm employee account opened (or failed) after park.
 */
@RestController
@RequestMapping("/api/public/employee-onboard")
public class EmployeeOnboardPublicController {

    private final EmployeeBulkOnboardService service;
    private final String confirmKey;

    public EmployeeOnboardPublicController(
            EmployeeBulkOnboardService service,
            @Value("${dfs.employee-onboard.confirm-key:}") String confirmKey) {
        this.service = service;
        this.confirmKey = confirmKey != null ? confirmKey.trim() : "";
    }

    @PostMapping("/confirm")
    public EmployeeBulkRowResponse confirm(
            @RequestHeader(value = "X-Employee-Onboard-Key", required = false) String key,
            @RequestBody EmployeeOnboardConfirmRequest req) {
        if (confirmKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Employee onboard confirm key not configured (dfs.employee-onboard.confirm-key)");
        }
        if (key == null || !confirmKey.equals(key.trim())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid confirm key");
        }
        return service.confirm(req);
    }
}
