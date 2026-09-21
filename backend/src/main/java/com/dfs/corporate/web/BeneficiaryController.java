package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.BeneficiaryService;
import com.dfs.corporate.web.dto.BeneficiaryRequest;
import com.dfs.corporate.web.dto.BeneficiaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @GetMapping
    public List<BeneficiaryResponse> list(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) String forProduct,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly
    ) {
        return beneficiaryService.list(principal, forProduct, activeOnly);
    }

    @GetMapping("/{publicId}")
    public BeneficiaryResponse get(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable String publicId
    ) {
        return beneficiaryService.get(principal, publicId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiaryResponse create(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody BeneficiaryRequest req
    ) {
        return beneficiaryService.create(principal, req);
    }

    @PutMapping("/{publicId}")
    public BeneficiaryResponse update(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable String publicId,
            @Valid @RequestBody BeneficiaryRequest req
    ) {
        return beneficiaryService.update(principal, publicId, req);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable String publicId
    ) {
        beneficiaryService.deactivate(principal, publicId);
    }
}
