package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.ApprovalWorkflowService;
import com.dfs.corporate.service.FranchiseCommissionService;
import com.dfs.corporate.service.PortalUserService;
import com.dfs.corporate.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CorporatePortalController {

    private final PortalUserService portalUserService;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final FranchiseCommissionService franchiseCommissionService;

    public CorporatePortalController(PortalUserService portalUserService,
                                     ApprovalWorkflowService approvalWorkflowService,
                                     FranchiseCommissionService franchiseCommissionService) {
        this.portalUserService = portalUserService;
        this.approvalWorkflowService = approvalWorkflowService;
        this.franchiseCommissionService = franchiseCommissionService;
    }

    // —— Portal users & roles ——

    @GetMapping("/api/portal/users")
    public List<PortalUserResponse> listUsers(@AuthenticationPrincipal AccountPrincipal principal) {
        return portalUserService.listUsers(principal);
    }

    @PostMapping("/api/portal/users")
    public PortalUserResponse createUser(@AuthenticationPrincipal AccountPrincipal principal,
                                         @Valid @RequestBody PortalUserCreateRequest req) {
        return portalUserService.createUser(principal, req);
    }

    @PutMapping("/api/portal/users/{accountId}/roles")
    public PortalUserResponse updateRoles(@AuthenticationPrincipal AccountPrincipal principal,
                                          @PathVariable Long accountId,
                                          @Valid @RequestBody PortalUserRolesUpdateRequest req) {
        return portalUserService.updateRoles(principal, accountId, req);
    }

    @GetMapping("/api/portal/me/roles")
    public Map<String, Object> myRoles(@AuthenticationPrincipal AccountPrincipal principal) {
        return Map.of(
                "accountId", principal.getAccountId(),
                "roles", portalUserService.rolesOf(principal.getAccountId()).stream().map(Enum::name).toList()
        );
    }

    // —— Approval workflow ——

    @GetMapping("/api/approvals")
    public List<ApprovalRequestResponse> listApprovals(@AuthenticationPrincipal AccountPrincipal principal) {
        return approvalWorkflowService.list(principal);
    }

    @GetMapping("/api/approvals/inbox")
    public List<ApprovalRequestResponse> inbox(@AuthenticationPrincipal AccountPrincipal principal) {
        return approvalWorkflowService.inboxAll(principal);
    }

    @GetMapping("/api/approvals/{publicId}")
    public ApprovalRequestResponse getApproval(@AuthenticationPrincipal AccountPrincipal principal,
                                               @PathVariable String publicId) {
        return approvalWorkflowService.get(principal, publicId);
    }

    @PostMapping("/api/approvals")
    public ApprovalRequestResponse createApproval(@AuthenticationPrincipal AccountPrincipal principal,
                                                  @Valid @RequestBody ApprovalCreateRequest req) {
        return approvalWorkflowService.create(principal, req);
    }

    @PostMapping("/api/approvals/{publicId}/decide")
    public ApprovalRequestResponse decide(@AuthenticationPrincipal AccountPrincipal principal,
                                          @PathVariable String publicId,
                                          @Valid @RequestBody ApprovalDecisionRequest req) {
        return approvalWorkflowService.decide(principal, publicId, req);
    }

    // —— Franchise commission ——

    @GetMapping("/api/franchises/commission-plans")
    public List<FranchiseCommissionPlanResponse> listCommissionPlans(
            @AuthenticationPrincipal AccountPrincipal principal) {
        return franchiseCommissionService.listForParent(principal);
    }

    @PostMapping("/api/franchises/children/{childPartyId}/confirm-commission")
    public FranchiseCommissionPlanResponse confirmCommission(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable Long childPartyId) {
        return franchiseCommissionService.parentConfirm(principal, childPartyId);
    }
}
