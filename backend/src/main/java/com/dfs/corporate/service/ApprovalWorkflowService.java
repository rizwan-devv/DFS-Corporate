package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.ApprovalActionRepository;
import com.dfs.corporate.repository.ApprovalRequestRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.*;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Maker → Checker → Approver → Releaser workflow.
 * If the Checker also has APPROVER role, Approver step is skipped → Releaser.
 */
@Service
public class ApprovalWorkflowService {

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalActionRepository actionRepository;
    private final PartyRepository partyRepository;
    private final PortalUserService portalUserService;

    public ApprovalWorkflowService(ApprovalRequestRepository requestRepository,
                                   ApprovalActionRepository actionRepository,
                                   PartyRepository partyRepository,
                                   PortalUserService portalUserService) {
        this.requestRepository = requestRepository;
        this.actionRepository = actionRepository;
        this.partyRepository = partyRepository;
        this.portalUserService = portalUserService;
    }

    public List<ApprovalRequestResponse> list(AccountPrincipal principal) {
        Party party = requireParty(principal);
        return requestRepository.findByPartyIdOrderByCreatedAtDesc(party.getId()).stream()
                .map(r -> toResponse(r, true))
                .toList();
    }

    public List<ApprovalRequestResponse> inbox(AccountPrincipal principal) {
        Party party = requireParty(principal);
        ApprovalStep step = stepForActor(principal);
        if (step == null) {
            return List.of();
        }
        return requestRepository
                .findByPartyIdAndStatusAndCurrentStepOrderByCreatedAtAsc(
                        party.getId(), ApprovalRequestStatus.IN_PROGRESS, step)
                .stream()
                .map(r -> toResponse(r, true))
                .toList();
    }

    public ApprovalRequestResponse get(AccountPrincipal principal, String publicId) {
        ApprovalRequest req = loadOwned(principal, publicId);
        return toResponse(req, true);
    }

    @Transactional
    public ApprovalRequestResponse create(AccountPrincipal principal, ApprovalCreateRequest body) {
        Party party = requireParty(principal);
        requireActive(party);
        if (!portalUserService.hasAnyRole(principal.getAccountId(), PortalRole.MAKER, PortalRole.PARTY_ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MAKER (or PARTY_ADMIN) role required to create requests");
        }
        ApprovalRequestType type;
        try {
            type = body.getRequestType() != null
                    ? ApprovalRequestType.valueOf(body.getRequestType().trim().toUpperCase())
                    : ApprovalRequestType.GENERIC;
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid requestType");
        }

        ApprovalRequest req = new ApprovalRequest();
        req.setPublicId(UUID.randomUUID().toString());
        req.setPartyId(party.getId());
        req.setRequestType(type);
        req.setReferenceKey(trim(body.getReferenceKey()));
        req.setTitle(body.getTitle().trim());
        req.setPayloadJson(body.getPayloadJson());
        req.setStatus(ApprovalRequestStatus.IN_PROGRESS);
        req.setCurrentStep(ApprovalStep.CHECKER);
        req.setCreatedByAccountId(principal.getAccountId());
        requestRepository.save(req);

        recordAction(req, ApprovalStep.MAKER, ApprovalDecision.SUBMIT, principal, body.getComment());
        return toResponse(req, true);
    }

    @Transactional
    public ApprovalRequestResponse decide(AccountPrincipal principal, String publicId, ApprovalDecisionRequest body) {
        ApprovalRequest req = loadOwned(principal, publicId);
        if (req.getStatus() != ApprovalRequestStatus.IN_PROGRESS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request is not awaiting action");
        }
        ApprovalDecision decision;
        try {
            decision = ApprovalDecision.valueOf(body.getDecision().trim().toUpperCase());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "decision must be APPROVE or REJECT");
        }
        if (decision != ApprovalDecision.APPROVE && decision != ApprovalDecision.REJECT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "decision must be APPROVE or REJECT");
        }

        ApprovalStep step = req.getCurrentStep();
        requireStepRole(principal, step);

        if (decision == ApprovalDecision.REJECT) {
            recordAction(req, step, ApprovalDecision.REJECT, principal, body.getComment());
            req.setStatus(ApprovalRequestStatus.REJECTED);
            req.setCompletedAt(Instant.now());
            req.setUpdatedAt(Instant.now());
            requestRepository.save(req);
            return toResponse(req, true);
        }

        // APPROVE
        if (step == ApprovalStep.CHECKER
                && ObjectsEqualsMaker(principal, req)
                && !portalUserService.hasRole(principal.getAccountId(), PortalRole.PARTY_ADMIN)) {
            // soft segregation: maker should not check own item unless admin
            throw new ApiException(HttpStatus.FORBIDDEN, "Maker cannot check their own request");
        }

        recordAction(req, step, ApprovalDecision.APPROVE, principal, body.getComment());
        ApprovalStep next = nextStepAfterApprove(step, principal);
        if (next == ApprovalStep.DONE) {
            req.setCurrentStep(ApprovalStep.DONE);
            req.setStatus(ApprovalRequestStatus.APPROVED);
            req.setCompletedAt(Instant.now());
        } else {
            req.setCurrentStep(next);
            req.setStatus(ApprovalRequestStatus.IN_PROGRESS);
        }
        req.setUpdatedAt(Instant.now());
        requestRepository.save(req);
        return toResponse(req, true);
    }

    /**
     * After Checker approve: if actor also has APPROVER → skip to RELEASER.
     * After Approver → RELEASER. After Releaser → DONE.
     */
    private ApprovalStep nextStepAfterApprove(ApprovalStep current, AccountPrincipal actor) {
        return switch (current) {
            case CHECKER -> {
                if (portalUserService.hasRole(actor.getAccountId(), PortalRole.APPROVER)) {
                    yield ApprovalStep.RELEASER;
                }
                yield ApprovalStep.APPROVER;
            }
            case APPROVER -> ApprovalStep.RELEASER;
            case RELEASER -> ApprovalStep.DONE;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot approve at step " + current);
        };
    }

    private void requireStepRole(AccountPrincipal principal, ApprovalStep step) {
        PortalRole needed = switch (step) {
            case CHECKER -> PortalRole.CHECKER;
            case APPROVER -> PortalRole.APPROVER;
            case RELEASER -> PortalRole.RELEASER;
            default -> null;
        };
        if (needed == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No action at step " + step);
        }
        if (!portalUserService.hasAnyRole(principal.getAccountId(), needed, PortalRole.PARTY_ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, needed.name() + " role required for this step");
        }
    }

    private ApprovalStep stepForActor(AccountPrincipal principal) {
        if (portalUserService.hasAnyRole(principal.getAccountId(), PortalRole.CHECKER, PortalRole.PARTY_ADMIN)) {
            // inbox prefers earliest pending for any role they hold — return null and filter multi
        }
        // Collect inbox by scanning steps they can act on — handled in inbox() one step at a time
        if (portalUserService.hasRole(principal.getAccountId(), PortalRole.CHECKER)
                || portalUserService.hasRole(principal.getAccountId(), PortalRole.PARTY_ADMIN)) {
            return ApprovalStep.CHECKER;
        }
        if (portalUserService.hasRole(principal.getAccountId(), PortalRole.APPROVER)) {
            return ApprovalStep.APPROVER;
        }
        if (portalUserService.hasRole(principal.getAccountId(), PortalRole.RELEASER)) {
            return ApprovalStep.RELEASER;
        }
        return null;
    }

    /** Inbox for all steps the user can act on. */
    public List<ApprovalRequestResponse> inboxAll(AccountPrincipal principal) {
        Party party = requireParty(principal);
        java.util.ArrayList<ApprovalRequestResponse> out = new java.util.ArrayList<>();
        if (portalUserService.hasAnyRole(principal.getAccountId(), PortalRole.CHECKER, PortalRole.PARTY_ADMIN)) {
            requestRepository.findByPartyIdAndStatusAndCurrentStepOrderByCreatedAtAsc(
                    party.getId(), ApprovalRequestStatus.IN_PROGRESS, ApprovalStep.CHECKER)
                    .forEach(r -> out.add(toResponse(r, false)));
        }
        if (portalUserService.hasAnyRole(principal.getAccountId(), PortalRole.APPROVER, PortalRole.PARTY_ADMIN)) {
            requestRepository.findByPartyIdAndStatusAndCurrentStepOrderByCreatedAtAsc(
                    party.getId(), ApprovalRequestStatus.IN_PROGRESS, ApprovalStep.APPROVER)
                    .forEach(r -> out.add(toResponse(r, false)));
        }
        if (portalUserService.hasAnyRole(principal.getAccountId(), PortalRole.RELEASER, PortalRole.PARTY_ADMIN)) {
            requestRepository.findByPartyIdAndStatusAndCurrentStepOrderByCreatedAtAsc(
                    party.getId(), ApprovalRequestStatus.IN_PROGRESS, ApprovalStep.RELEASER)
                    .forEach(r -> out.add(toResponse(r, false)));
        }
        return out;
    }

    private boolean ObjectsEqualsMaker(AccountPrincipal principal, ApprovalRequest req) {
        return java.util.Objects.equals(principal.getAccountId(), req.getCreatedByAccountId());
    }

    private void recordAction(ApprovalRequest req, ApprovalStep step, ApprovalDecision decision,
                              AccountPrincipal actor, String comment) {
        ApprovalAction a = new ApprovalAction();
        a.setRequestId(req.getId());
        a.setStep(step);
        a.setDecision(decision);
        a.setActorAccountId(actor.getAccountId());
        a.setActorEmail(actor.getUsername());
        a.setCommentText(trim(comment));
        actionRepository.save(a);
    }

    private ApprovalRequest loadOwned(AccountPrincipal principal, String publicId) {
        Party party = requireParty(principal);
        ApprovalRequest req = requestRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Approval request not found"));
        if (!java.util.Objects.equals(req.getPartyId(), party.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Request does not belong to your corporate");
        }
        return req;
    }

    private Party requireParty(AccountPrincipal principal) {
        return partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
    }

    private void requireActive(Party party) {
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Corporate must be ACTIVE to use approvals");
        }
    }

    private ApprovalRequestResponse toResponse(ApprovalRequest req, boolean withActions) {
        ApprovalRequestResponse r = new ApprovalRequestResponse();
        r.setPublicId(req.getPublicId());
        r.setRequestType(req.getRequestType().name());
        r.setReferenceKey(req.getReferenceKey());
        r.setTitle(req.getTitle());
        r.setPayloadJson(req.getPayloadJson());
        r.setStatus(req.getStatus().name());
        r.setCurrentStep(req.getCurrentStep().name());
        r.setCreatedByAccountId(req.getCreatedByAccountId());
        r.setCreatedAt(req.getCreatedAt());
        r.setUpdatedAt(req.getUpdatedAt());
        r.setCompletedAt(req.getCompletedAt());
        if (withActions) {
            r.setActions(actionRepository.findByRequestIdOrderByCreatedAtAsc(req.getId()).stream().map(a -> {
                ApprovalActionResponse ar = new ApprovalActionResponse();
                ar.setStep(a.getStep().name());
                ar.setDecision(a.getDecision().name());
                ar.setActorEmail(a.getActorEmail());
                ar.setComment(a.getCommentText());
                ar.setCreatedAt(a.getCreatedAt());
                return ar;
            }).toList());
        }
        return r;
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
