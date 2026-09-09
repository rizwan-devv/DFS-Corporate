package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.FranchiseCommissionLedgerService;
import com.dfs.corporate.web.dto.FranchiseCommissionEntryResponse;
import com.dfs.corporate.web.dto.FranchiseTxnPostRequest;
import com.dfs.corporate.web.dto.FranchiseWalletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class FranchiseCommissionLedgerController {

    private final FranchiseCommissionLedgerService ledgerService;

    public FranchiseCommissionLedgerController(FranchiseCommissionLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    /** Post a successful child transaction → real-time parent/child split. */
    @PostMapping("/api/franchises/transactions")
    public FranchiseCommissionEntryResponse post(@AuthenticationPrincipal AccountPrincipal principal,
                                                 @Valid @RequestBody FranchiseTxnPostRequest req) {
        return ledgerService.post(principal, req);
    }

    @PostMapping("/api/franchises/transactions/{publicId}/reverse")
    public FranchiseCommissionEntryResponse reverse(@AuthenticationPrincipal AccountPrincipal principal,
                                                    @PathVariable String publicId) {
        return ledgerService.reverse(principal, publicId);
    }

    @GetMapping("/api/franchises/transactions")
    public List<FranchiseCommissionEntryResponse> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return ledgerService.list(principal);
    }

    @GetMapping("/api/franchises/wallet")
    public FranchiseWalletResponse wallet(@AuthenticationPrincipal AccountPrincipal principal,
                                          @RequestParam(defaultValue = "PKR") String currency) {
        return ledgerService.wallet(principal, currency);
    }
}
