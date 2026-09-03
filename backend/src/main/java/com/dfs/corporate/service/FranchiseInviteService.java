package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.FranchiseCommissionPlanRepository;
import com.dfs.corporate.repository.FranchiseInviteRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.web.dto.*;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class FranchiseInviteService {

    private final FranchiseInviteRepository inviteRepository;
    private final PartyRepository partyRepository;
    private final MailService mailService;
    private final FranchiseCommissionPlanRepository commissionPlanRepository;
    private final String frontendBaseUrl;

    public FranchiseInviteService(FranchiseInviteRepository inviteRepository,
                                  PartyRepository partyRepository,
                                  MailService mailService,
                                  FranchiseCommissionPlanRepository commissionPlanRepository,
                                  @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.inviteRepository = inviteRepository;
        this.partyRepository = partyRepository;
        this.mailService = mailService;
        this.commissionPlanRepository = commissionPlanRepository;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    public List<FranchiseInviteResponse> listInvites(AccountPrincipal principal) {
        Party parent = requireActiveMaster(principal);
        return inviteRepository.findByParentPartyIdOrderByInvitedAtDesc(parent.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<FranchiseChildSummary> listChildren(AccountPrincipal principal) {
        Party parent = requireActiveMaster(principal);
        return partyRepository.findByParentPartyIdOrderByCreatedAtDesc(parent.getId()).stream()
                .map(this::toChildSummary)
                .toList();
    }

    @Transactional
    public FranchiseInviteResponse create(AccountPrincipal principal, FranchiseInviteCreateRequest req) {
        Party parent = requireActiveMaster(principal);
        String email = req.getEmail().trim().toLowerCase();
        String phone = IdentityFormats.phoneDigits(req.getPhone());
        if (phone == null || phone.length() < 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Valid franchise phone is required (app user ID)");
        }

        FranchiseInvite invite = new FranchiseInvite();
        invite.setPublicToken(UUID.randomUUID().toString().replace("-", ""));
        invite.setParentPartyId(parent.getId());
        invite.setEmail(email);
        invite.setPhone(phone);
        invite.setContactName(req.getContactName().trim());
        invite.setBusinessName(trim(req.getBusinessName()));
        invite.setEntityType(trim(req.getEntityType()));
        FranchiseCommissionService.validateRate(req.getCommissionRatePercent());
        invite.setCommissionRatePercent(req.getCommissionRatePercent());
        invite.setCommissionType(trim(req.getCommissionType()) != null
                ? trim(req.getCommissionType()) : (req.getCommissionRatePercent() != null ? "PERCENT_GROSS" : null));
        invite.setCommissionNotes(trim(req.getCommissionNotes()));
        invite.setStatus(FranchiseInviteStatus.PENDING);
        invite.setExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
        inviteRepository.save(invite);

        sendInviteMail(parent, invite);
        return toResponse(invite);
    }

    @Transactional
    public FranchiseInviteResponse resend(AccountPrincipal principal, Long inviteId) {
        Party parent = requireActiveMaster(principal);
        FranchiseInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Franchise invite not found"));
        if (!invite.getParentPartyId().equals(parent.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Invite does not belong to your corporate account");
        }
        if (invite.getStatus() == FranchiseInviteStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invite was cancelled");
        }
        if (invite.getStatus() == FranchiseInviteStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Franchise already onboarded via this invite");
        }
        if (invite.getExpiresAt().isBefore(Instant.now()) || invite.getStatus() == FranchiseInviteStatus.EXPIRED) {
            invite.setPublicToken(UUID.randomUUID().toString().replace("-", ""));
            invite.setExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
            invite.setStatus(FranchiseInviteStatus.PENDING);
        }
        inviteRepository.save(invite);
        sendInviteMail(parent, invite);
        return toResponse(invite);
    }

    @Transactional
    public void cancel(AccountPrincipal principal, Long inviteId) {
        Party parent = requireActiveMaster(principal);
        FranchiseInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Franchise invite not found"));
        if (!invite.getParentPartyId().equals(parent.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Invite does not belong to your corporate account");
        }
        if (invite.getStatus() == FranchiseInviteStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot cancel a completed invite");
        }
        invite.setStatus(FranchiseInviteStatus.CANCELLED);
        inviteRepository.save(invite);
    }

    @Transactional
    public FranchiseInvitePublicResponse getPublic(String token) {
        FranchiseInvite invite = inviteRepository.findByPublicToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invalid franchise invite link"));
        Party parent = partyRepository.findById(invite.getParentPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Parent corporate not found"));

        FranchiseInvitePublicResponse res = new FranchiseInvitePublicResponse();
        res.setToken(invite.getPublicToken());
        res.setStatus(invite.getStatus());
        res.setExpiresAt(invite.getExpiresAt());
        res.setContactName(invite.getContactName());
        res.setEmail(invite.getEmail());
        res.setPhone(invite.getPhone());
        res.setBusinessName(invite.getBusinessName());
        res.setEntityType(invite.getEntityType());
        res.setCommissionRatePercent(invite.getCommissionRatePercent());
        res.setCommissionType(invite.getCommissionType());
        res.setParentBusinessName(parent.getBusinessName() != null ? parent.getBusinessName() : parent.getFullName());
        res.setParentTrackingId(parent.getTrackingId());

        if (invite.getStatus() == FranchiseInviteStatus.CANCELLED) {
            res.setUsable(false);
            res.setMessage("This invite was cancelled by the corporate parent.");
            return res;
        }
        if (invite.getStatus() == FranchiseInviteStatus.COMPLETED) {
            res.setUsable(false);
            res.setMessage("This invite was already used. Please login instead.");
            return res;
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(FranchiseInviteStatus.EXPIRED);
            inviteRepository.save(invite);
            res.setStatus(FranchiseInviteStatus.EXPIRED);
            res.setUsable(false);
            res.setMessage("This invite has expired. Ask the corporate parent to resend.");
            return res;
        }
        if (parent.getStatus() != PartyStatus.ACTIVE || parent.getPartyType() != PartyType.MERCHANT) {
            res.setUsable(false);
            res.setMessage("Parent corporate is not active. Franchise onboarding is unavailable.");
            return res;
        }
        res.setUsable(true);
        res.setMessage("You are joining under " + res.getParentBusinessName()
                + ". Complete signup — parent is already linked (no parent ID needed).");
        return res;
    }

    /**
     * Called from AuthService during franchise signup — validates token and returns parent party.
     * Marks invite IN_PROGRESS; caller must call {@link #markCompleted} after party save.
     */
    @Transactional
    public FranchiseInvite consumeForSignup(String token) {
        FranchiseInvite invite = inviteRepository.findByPublicToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid franchise invite token"));
        if (invite.getStatus() == FranchiseInviteStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This franchise invite was cancelled");
        }
        if (invite.getStatus() == FranchiseInviteStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This franchise invite was already used");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(FranchiseInviteStatus.EXPIRED);
            inviteRepository.save(invite);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Franchise invite expired — ask parent to resend");
        }
        Party parent = partyRepository.findById(invite.getParentPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Parent corporate not found"));
        if (parent.getPartyType() != PartyType.MERCHANT || parent.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Parent must be an active corporate (master) wallet");
        }
        invite.setStatus(FranchiseInviteStatus.IN_PROGRESS);
        inviteRepository.save(invite);
        return invite;
    }

    @Transactional
    public void markCompleted(FranchiseInvite invite, Long childPartyId) {
        invite.setChildPartyId(childPartyId);
        invite.setStatus(FranchiseInviteStatus.COMPLETED);
        invite.setCompletedAt(Instant.now());
        inviteRepository.save(invite);
    }

    private Party requireActiveMaster(AccountPrincipal principal) {
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getPartyType() != PartyType.MERCHANT) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only the corporate (master) account can manage franchise invites");
        }
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Corporate must be ACTIVE before inviting franchises");
        }
        return party;
    }

    private void sendInviteMail(Party parent, FranchiseInvite invite) {
        String url = inviteUrl(invite.getPublicToken());
        String parentName = parent.getBusinessName() != null ? parent.getBusinessName() : parent.getFullName();
        mailService.send(invite.getEmail(),
                "DFS Corporate — franchise / child wallet invite",
                "Hello " + invite.getContactName() + ",\n\n"
                        + "You are invited to join as a Franchise (child wallet) under:\n"
                        + "  " + parentName
                        + (parent.getTrackingId() != null ? " (" + parent.getTrackingId() + ")" : "")
                        + "\n\n"
                        + (invite.getCommissionRatePercent() != null
                        ? ("Proposed commission: " + invite.getCommissionRatePercent() + "% "
                        + (invite.getCommissionType() != null ? invite.getCommissionType() : "") + "\n"
                        + "(Finalized when your application is approved)\n\n")
                        : "")
                        + "Open this secure link to create your account (parent is already linked):\n"
                        + url + "\n\n"
                        + "Suggested phone (app user ID): " + invite.getPhone() + "\n"
                        + "Link expires: " + invite.getExpiresAt() + "\n\n"
                        + "— DFS Corporate / Business Wallet");
    }

    private String inviteUrl(String token) {
        return frontendBaseUrl + "/franchise-onboard?token=" + token;
    }

    private FranchiseInviteResponse toResponse(FranchiseInvite invite) {
        FranchiseInviteResponse r = new FranchiseInviteResponse();
        r.setId(invite.getId());
        r.setContactName(invite.getContactName());
        r.setEmail(invite.getEmail());
        r.setPhone(invite.getPhone());
        r.setBusinessName(invite.getBusinessName());
        r.setEntityType(invite.getEntityType());
        r.setStatus(invite.getStatus());
        r.setInviteUrl(inviteUrl(invite.getPublicToken()));
        r.setInvitedAt(invite.getInvitedAt());
        r.setExpiresAt(invite.getExpiresAt());
        r.setCompletedAt(invite.getCompletedAt());
        r.setChildPartyId(invite.getChildPartyId());
        r.setCommissionRatePercent(invite.getCommissionRatePercent());
        r.setCommissionType(invite.getCommissionType());
        r.setCommissionNotes(invite.getCommissionNotes());
        if (invite.getChildPartyId() != null) {
            partyRepository.findById(invite.getChildPartyId()).ifPresent(c -> {
                r.setChildTrackingId(c.getTrackingId());
                r.setChildStatus(c.getStatus() != null ? c.getStatus().name() : null);
            });
        }
        return r;
    }

    private FranchiseChildSummary toChildSummary(Party p) {
        FranchiseChildSummary s = new FranchiseChildSummary();
        s.setId(p.getId());
        s.setPublicId(p.getPublicId());
        s.setTrackingId(p.getTrackingId());
        s.setBusinessName(p.getBusinessName());
        s.setFullName(p.getFullName());
        s.setEmail(p.getEmail());
        s.setPhone(p.getPhone());
        s.setStatus(p.getStatus() != null ? p.getStatus().name() : null);
        s.setPartyType(p.getPartyType() != null ? p.getPartyType().name() : null);
        s.setEntityType(p.getEntityType() != null ? p.getEntityType().name() : null);
        commissionPlanRepository.findByChildPartyId(p.getId()).ifPresent(plan -> {
            s.setCommissionRatePercent(plan.getCommissionRatePercent());
            s.setCommissionType(plan.getCommissionType());
            s.setCommissionStatus(plan.getStatus() != null ? plan.getStatus().name() : null);
        });
        return s;
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
