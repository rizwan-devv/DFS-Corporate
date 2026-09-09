package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.FranchiseAgentPortalService;
import com.dfs.corporate.web.dto.AgentAppPortalResponse;
import com.dfs.corporate.web.dto.FranchiseChildChangeMpinRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class FranchiseAgentPortalController {

    private final FranchiseAgentPortalService portalService;

    public FranchiseAgentPortalController(FranchiseAgentPortalService portalService) {
        this.portalService = portalService;
    }

    /** Parent views a franchise child's live AgentApp wallet balance. */
    @GetMapping("/api/franchises/children/{childPartyId}/agent-balance")
    public AgentAppPortalResponse balance(@AuthenticationPrincipal AccountPrincipal principal,
                                          @PathVariable Long childPartyId) {
        return portalService.getBalance(principal, childPartyId);
    }

    /**
     * Parent views a franchise child's mini-statement.
     * Omit fromDate/toDate for last nine records; supply both as yyyy-MM-dd HH:mm:ss for a range (max 180 days).
     */
    @GetMapping("/api/franchises/children/{childPartyId}/agent-mini-statement")
    public AgentAppPortalResponse miniStatement(@AuthenticationPrincipal AccountPrincipal principal,
                                                @PathVariable Long childPartyId,
                                                @RequestParam(required = false) String fromDate,
                                                @RequestParam(required = false) String toDate) {
        return portalService.miniStatement(principal, childPartyId, fromDate, toDate);
    }

    /** Parent/admin change of a franchise child's AgentApp MPIN. */
    @PostMapping("/api/franchises/children/{childPartyId}/agent-change-mpin")
    public AgentAppPortalResponse changeMpin(@AuthenticationPrincipal AccountPrincipal principal,
                                             @PathVariable Long childPartyId,
                                             @Valid @RequestBody FranchiseChildChangeMpinRequest req) {
        return portalService.changeMpin(principal, childPartyId, req);
    }
}
