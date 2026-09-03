package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.FranchiseCommissionPlanRepository;
import com.dfs.corporate.repository.FranchiseInviteRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.FranchiseCommissionPlanResponse;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class FranchiseCommissionService {

    private final FranchiseCommissionPlanRepository planRepository;
    private final FranchiseInviteRepository inviteRepository;
    private final PartyRepository partyRepository;
    private final PortalUserService portalUserService;

    public FranchiseCommissionService(FranchiseCommissionPlanRepository planRepository,
                                      FranchiseInviteRepository inviteRepository,
                                      PartyRepository partyRepository,
                                      PortalUserService portalUserService) {
        this.planRepository = planRepository;
        this.inviteRepository = inviteRepository;
        this.partyRepository = partyRepository;
        this.portalUserService = portalUserService;
    }

    /** After franchise signup completes — create PROPOSED plan from invite rates. */
    @Transactional
    public void proposeFromInvite(FranchiseInvite invite, Long childPartyId) {
        if (invite.getCommissionRatePercent() == null) {
            return;
        }
        FranchiseCommissionPlan plan = planRepository.findByChildPartyId(childPartyId).orElseGet(FranchiseCommissionPlan::new);
        if (plan.getStatus() == CommissionPlanStatus.LOCKED) {
            return;
        }
        plan.setParentPartyId(invite.getParentPartyId());
        plan.setChildPartyId(childPartyId);
        plan.setInviteId(invite.getId());
        plan.setCommissionRatePercent(invite.getCommissionRatePercent());
        plan.setCommissionType(invite.getCommissionType() != null ? invite.getCommissionType() : "PERCENT_GROSS");
        plan.setNotes(invite.getCommissionNotes());
        plan.setStatus(CommissionPlanStatus.PROPOSED);
        planRepository.save(plan);
    }

    /**
     * Lock commission when franchise (child) is approved — backoffice or parent.
     */
    @Transactional
    public FranchiseCommissionPlan lockForChild(Long childPartyId, Long actorAccountId, String source) {
        Party child = partyRepository.findById(childPartyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Franchise party not found"));
        if (child.getPartyType() != PartyType.SUB_MERCHANT) {
            return null;
        }

        FranchiseCommissionPlan plan = planRepository.findByChildPartyId(childPartyId).orElse(null);
        if (plan == null) {
            FranchiseInvite invite = inviteRepository.findByChildPartyId(childPartyId).orElse(null);
            if (invite == null || invite.getCommissionRatePercent() == null) {
                return null;
            }
            proposeFromInvite(invite, childPartyId);
            plan = planRepository.findByChildPartyId(childPartyId).orElse(null);
        }
        if (plan == null) return null;
        if (plan.getStatus() == CommissionPlanStatus.LOCKED) {
            return plan;
        }
        plan.setStatus(CommissionPlanStatus.LOCKED);
        plan.setLockedAt(Instant.now());
        plan.setLockedByAccountId(actorAccountId);
        plan.setLockedBySource(source);
        plan.setVersionNo(plan.getVersionNo() == null ? 1 : plan.getVersionNo());
        return planRepository.save(plan);
    }

    @Transactional
    public FranchiseCommissionPlanResponse parentConfirm(AccountPrincipal principal, Long childPartyId) {
        Party parent = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (parent.getPartyType() != PartyType.MERCHANT || parent.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only ACTIVE master can confirm commission");
        }
        if (!portalUserService.hasAnyRole(principal.getAccountId(), PortalRole.PARTY_ADMIN, PortalRole.APPROVER, PortalRole.RELEASER)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PARTY_ADMIN / APPROVER / RELEASER required to lock commission");
        }
        Party child = partyRepository.findById(childPartyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Franchise not found"));
        if (!java.util.Objects.equals(child.getParentPartyId(), parent.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Franchise is not under your corporate");
        }
        if (child.getStatus() != PartyStatus.ACTIVE && child.getStatus() != PartyStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Franchise must be PENDING_APPROVAL or ACTIVE to lock commission");
        }
        FranchiseCommissionPlan plan = lockForChild(childPartyId, principal.getAccountId(), "PARENT");
        if (plan == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No commission was proposed on the franchise invite");
        }
        return toResponse(plan, child);
    }

    public List<FranchiseCommissionPlanResponse> listForParent(AccountPrincipal principal) {
        Party parent = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        return planRepository.findByParentPartyIdOrderByProposedAtDesc(parent.getId()).stream()
                .map(p -> {
                    Party child = partyRepository.findById(p.getChildPartyId()).orElse(null);
                    return toResponse(p, child);
                })
                .toList();
    }

    public FranchiseCommissionPlanResponse getForChild(Long childPartyId) {
        return planRepository.findByChildPartyId(childPartyId)
                .map(p -> toResponse(p, partyRepository.findById(childPartyId).orElse(null)))
                .orElse(null);
    }

    public static void validateRate(BigDecimal rate) {
        if (rate == null) return;
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Commission rate must be between 0 and 100");
        }
    }

    private FranchiseCommissionPlanResponse toResponse(FranchiseCommissionPlan plan, Party child) {
        FranchiseCommissionPlanResponse r = new FranchiseCommissionPlanResponse();
        r.setId(plan.getId());
        r.setChildPartyId(plan.getChildPartyId());
        r.setInviteId(plan.getInviteId());
        r.setCommissionRatePercent(plan.getCommissionRatePercent());
        r.setCommissionType(plan.getCommissionType());
        r.setNotes(plan.getNotes());
        r.setStatus(plan.getStatus().name());
        r.setProposedAt(plan.getProposedAt());
        r.setLockedAt(plan.getLockedAt());
        r.setLockedBySource(plan.getLockedBySource());
        r.setVersionNo(plan.getVersionNo());
        if (child != null) {
            r.setChildTrackingId(child.getTrackingId());
            r.setChildBusinessName(child.getBusinessName());
            r.setChildStatus(child.getStatus() != null ? child.getStatus().name() : null);
        }
        return r;
    }
}
