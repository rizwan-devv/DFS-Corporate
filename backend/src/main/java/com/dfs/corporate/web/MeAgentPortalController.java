package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.FranchiseAgentPortalService;
import com.dfs.corporate.web.dto.AgentAppPortalResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Logged-in corporate party's own AgentApp balance / mini-statement.
 */
@RestController
public class MeAgentPortalController {

    private final FranchiseAgentPortalService portalService;

    public MeAgentPortalController(FranchiseAgentPortalService portalService) {
        this.portalService = portalService;
    }

    @GetMapping("/api/me/agent-balance")
    public AgentAppPortalResponse balance(@AuthenticationPrincipal AccountPrincipal principal) {
        return portalService.getOwnBalance(principal);
    }

    /**
     * Omit fromDate/toDate for last nine records; supply both as yyyy-MM-dd HH:mm:ss for a range.
     */
    @GetMapping("/api/me/agent-mini-statement")
    public AgentAppPortalResponse miniStatement(@AuthenticationPrincipal AccountPrincipal principal,
                                                @RequestParam(required = false) String fromDate,
                                                @RequestParam(required = false) String toDate) {
        return portalService.getOwnMiniStatement(principal, fromDate, toDate);
    }
}
