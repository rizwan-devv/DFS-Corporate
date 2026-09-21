package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.LiveTransferService;
import com.dfs.corporate.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Live DFS transfer rails (FT / IBFT / UBP). Requires JWT + ACTIVE party.
 * Downstream auth uses server-side X-Portal-Key — never sent to the browser.
 */
@RestController
@RequestMapping("/api/transfers/live")
public class LiveTransferController {

    private final LiveTransferService liveTransferService;

    public LiveTransferController(LiveTransferService liveTransferService) {
        this.liveTransferService = liveTransferService;
    }

    @GetMapping("/status")
    public Map<String, Object> status(@AuthenticationPrincipal AccountPrincipal principal) {
        return liveTransferService.status(principal);
    }

    /**
     * Portal convenience GET. Downstream DFS bankList is POST (empty payload) per Postman.
     */
    @GetMapping("/ibft/banks")
    public DfsPortalTxnResponse ibftBanks(@AuthenticationPrincipal AccountPrincipal principal) {
        return liveTransferService.ibftBanks(principal);
    }

    @PostMapping("/ibft/title-fetch")
    public DfsPortalTxnResponse ibftTitle(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody LiveIbftTitleRequest req) {
        return liveTransferService.ibftTitleFetch(principal, req);
    }

    @PostMapping("/ibft/advice")
    public DfsPortalTxnResponse ibftAdvice(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody LiveIbftAdviceRequest req) {
        return liveTransferService.ibftAdvice(principal, req);
    }

    @GetMapping("/ubp/billers")
    public DfsPortalTxnResponse ubpBillers(@AuthenticationPrincipal AccountPrincipal principal) {
        return liveTransferService.ubpBillers(principal);
    }

    @PostMapping("/ubp/inquiry")
    public DfsPortalTxnResponse ubpInquiry(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody LiveBillInquiryRequest req) {
        return liveTransferService.ubpInquiry(principal, req);
    }

    @PostMapping("/ubp/pay")
    public DfsPortalTxnResponse ubpPay(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody LiveBillPaymentRequest req) {
        return liveTransferService.ubpPay(principal, req);
    }

    @PostMapping("/ft/initiate")
    public DfsPortalTxnResponse ftInitiate(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody LiveFtInitiateRequest req) {
        return liveTransferService.ftInitiate(principal, req);
    }

    @PostMapping("/ft/confirm")
    public DfsPortalTxnResponse ftConfirm(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody LiveFtConfirmRequest req) {
        return liveTransferService.ftConfirm(principal, req);
    }

    @PostMapping("/mpin/verify")
    public DfsPortalTxnResponse verifyMpin(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody LiveMpinVerifyRequest req) {
        return liveTransferService.verifyMpin(principal, req);
    }
}
