package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AccountRepository;
import com.dfs.corporate.repository.AssociatedPersonRepository;
import com.dfs.corporate.repository.BrandRepository;
import com.dfs.corporate.repository.PartyDocumentRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.PartnerAppUserResponse;
import com.dfs.corporate.web.dto.PartnerInviteResponse;
import com.dfs.corporate.web.dto.PartyResponse;
import com.dfs.corporate.web.dto.RejectRequest;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminOnboardingService {

    private final PartyRepository partyRepository;
    private final AccountRepository accountRepository;
    private final PartyDocumentRepository documentRepository;
    private final AssociatedPersonRepository associatedPersonRepository;
    private final BrandRepository brandRepository;
    private final PartnerInviteService partnerInviteService;
    private final PartnerAppUserService partnerAppUserService;
    private final AccountProvisioningService accountProvisioningService;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();

    public AdminOnboardingService(PartyRepository partyRepository,
                                  AccountRepository accountRepository,
                                  PartyDocumentRepository documentRepository,
                                  AssociatedPersonRepository associatedPersonRepository,
                                  BrandRepository brandRepository,
                                  PartnerInviteService partnerInviteService,
                                  PartnerAppUserService partnerAppUserService,
                                  AccountProvisioningService accountProvisioningService,
                                  FileStorageService fileStorageService,
                                  PasswordEncoder passwordEncoder,
                                  MailService mailService) {
        this.partyRepository = partyRepository;
        this.accountRepository = accountRepository;
        this.documentRepository = documentRepository;
        this.associatedPersonRepository = associatedPersonRepository;
        this.brandRepository = brandRepository;
        this.partnerInviteService = partnerInviteService;
        this.partnerAppUserService = partnerAppUserService;
        this.accountProvisioningService = accountProvisioningService;
        this.fileStorageService = fileStorageService;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
    }

    public List<Map<String, Object>> brands() {
        return brandRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", b.getId());
                    m.put("code", b.getCode());
                    m.put("name", b.getName());
                    m.put("merchantCount", partyRepository.findByBrandIdOrderByCreatedAtDesc(b.getId()).size());
                    return m;
                })
                .toList();
    }

    public List<PartyResponse> pending() {
        List<Party> submitted = partyRepository.findByStatusOrderByCreatedAtDesc(PartyStatus.SUBMITTED);
        List<Party> pending = partyRepository.findByStatusOrderByCreatedAtDesc(PartyStatus.PENDING_APPROVAL);
        List<Party> all = new java.util.ArrayList<>();
        all.addAll(submitted);
        all.addAll(pending);
        return all.stream().map(this::enrich).toList();
    }

    public List<PartyResponse> all(Long brandId, String status) {
        List<Party> parties;
        PartyStatus st = parseStatus(status);
        if (brandId != null && st != null) {
            parties = partyRepository.findByBrandIdAndStatusOrderByCreatedAtDesc(brandId, st);
        } else if (brandId != null) {
            parties = partyRepository.findByBrandIdOrderByCreatedAtDesc(brandId);
        } else if (st != null) {
            parties = partyRepository.findByStatusOrderByCreatedAtDesc(st);
        } else {
            parties = partyRepository.findAllByOrderByCreatedAtDesc();
        }
        return parties.stream().map(this::enrich).toList();
    }

    public PartyResponse get(Long id) {
        return enrich(partyRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found")));
    }

    @Transactional
    public Map<String, Object> approve(Long id, AccountPrincipal admin) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        partnerAppUserService.tryAdvanceParty(party.getId());
        party = partyRepository.findById(id).orElseThrow();
        if (party.getStatus() == PartyStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Waiting for all partners to complete mobile app KYC before final approve");
        }
        if (party.getStatus() != PartyStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Party must be PENDING_APPROVAL");
        }
        if (!partnerAppUserService.allKycCompleted(party.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Cannot approve: partner mobile KYC still incomplete");
        }
        List<PartyDocument> docs = documentRepository.findByPartyIdOrderByUploadedAtDesc(party.getId());
        long pendingDocs = docs.stream().filter(d -> d.getStatus() == DocumentStatus.PENDING).count();
        long rejectedDocs = docs.stream().filter(d -> d.getStatus() == DocumentStatus.REJECTED).count();
        if (rejectedDocs > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Cannot approve: " + rejectedDocs + " document(s) rejected — ask applicant to re-upload");
        }
        if (pendingDocs > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Cannot approve: " + pendingDocs + " document(s) still PENDING — review each document first");
        }
        if (docs.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot approve: no documents uploaded");
        }
        if (party.getSanctionsStatus() == ScreeningStatus.HIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot approve: sanctions HIT");
        }
        if (party.getSanctionsStatus() != ScreeningStatus.CLEAR) {
            party.setSanctionsStatus(ScreeningStatus.CLEAR);
            party.setSanctionsNotes("Cleared by admin at approval (Framework §F.4)");
            party.setSanctionsScreenedAt(Instant.now());
        }
        if (party.getIdentityVerificationStatus() != IdentityVerificationStatus.BV_DONE
                && party.getIdentityVerificationStatus() != IdentityVerificationStatus.VERISYS_DONE) {
            party.setIdentityVerificationStatus(IdentityVerificationStatus.WAIVED_MANUAL);
            party.setIdentityVerificationMethod("ADMIN_MANUAL_PENDING_NADRA");
        }
        Account account = accountRepository.findByPartyId(party.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Account missing"));

        String rawPassword = generatePassword();
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setStatus(AccountStatus.ACTIVE);
        account.setFirstLogin(true);
        accountRepository.save(account);

        party.setStatus(PartyStatus.ACTIVE);
        party.setApprovedAt(Instant.now());
        party.setApprovedBy(admin.getUsername());
        party.setRejectionReason(null);
        partyRepository.save(party);

        // Kick off DFS Account API (stub leaves PENDING until real client is wired)
        party = accountProvisioningService.provisionAfterApprove(party.getId());

        mailService.send(party.getEmail(), "DFS Corporate — Account Approved",
                """
                Hello %s,

                Your DFS Corporate application (%s) has been approved.

                Login email: %s
                Temporary password: %s

                Please login and change your password on first use.

                DFS account status: %s%s

                — DFS Corporate
                """.formatted(
                        party.getFullName(),
                        party.getPartyType(),
                        party.getEmail(),
                        rawPassword,
                        party.getAccountProvisionStatus(),
                        party.getDfsAccountId() != null ? (" / ID: " + party.getDfsAccountId()) : ""));

        Map<String, Object> res = new HashMap<>();
        res.put("party", enrich(party));
        res.put("temporaryPassword", rawPassword);
        res.put("accountProvisionStatus", party.getAccountProvisionStatus());
        res.put("dfsAccountId", party.getDfsAccountId());
        res.put("message", "Approved. Credentials emailed. DFS account provision: "
                + party.getAccountProvisionStatus());
        return res;
    }

    @Transactional
    public PartyResponse retryAccountProvision(Long id) {
        return enrich(accountProvisioningService.retry(id));
    }

    @Transactional
    public PartyResponse reject(Long id, RejectRequest req, AccountPrincipal admin) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.PENDING_APPROVAL && party.getStatus() != PartyStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Party must be SUBMITTED or PENDING_APPROVAL");
        }
        Account account = accountRepository.findByPartyId(party.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Account missing"));

        party.setStatus(PartyStatus.REJECTED);
        party.setRejectionReason(req.getReason());
        party.setApprovedBy(admin.getUsername());
        partyRepository.save(party);

        account.setStatus(AccountStatus.LOCKED);
        accountRepository.save(account);

        mailService.send(party.getEmail(), "DFS Corporate — Application Rejected",
                """
                Hello %s,

                Your DFS Corporate application was not approved.

                Reason: %s

                You may correct your profile/documents and resubmit.

                — DFS Corporate
                """.formatted(party.getFullName(), req.getReason()));

        return enrich(party);
    }

    @Transactional
    public PartyResponse reviewDocument(Long docId, boolean approve, String note) {
        PartyDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        doc.setStatus(approve ? DocumentStatus.APPROVED : DocumentStatus.REJECTED);
        doc.setReviewNote(note);
        documentRepository.save(doc);
        Party party = partyRepository.findById(doc.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        return enrich(party);
    }

    @Transactional
    public PartyResponse setSanctions(Long id, ScreeningStatus status, String notes) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        party.setSanctionsStatus(status);
        party.setSanctionsNotes(notes);
        party.setSanctionsScreenedAt(Instant.now());
        return enrich(partyRepository.save(party));
    }

    @Transactional
    public PartyResponse setIdentityVerification(Long id, IdentityVerificationStatus status, String method) {
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        party.setIdentityVerificationStatus(status);
        party.setIdentityVerificationMethod(method);
        return enrich(partyRepository.save(party));
    }

    @Transactional
    public PartyResponse setDiscrepancy(Long id, String note) {
        if (note == null || note.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Discrepancy note is required");
        }
        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        party.setDiscrepancyNote(note);
        mailService.send(party.getEmail(), "DFS Corporate — Additional information required",
                "Tracking ID: " + party.getTrackingId() + "\n\n" + note + "\n\nPlease update your application.");
        return enrich(partyRepository.save(party));
    }

    @Transactional
    public PartnerAppUserResponse resendAppInvite(Long appUserId) {
        return partnerAppUserService.resend(appUserId);
    }

    @Transactional
    public PartnerAppUserResponse markAppKycComplete(Long appUserId) {
        return partnerAppUserService.markKycCompleted(appUserId);
    }

    @Transactional
    public PartnerInviteResponse resendPartnerInvite(Long inviteId) {
        return partnerInviteService.adminResend(inviteId);
    }

    public Map<String, Object> documentFile(Long docId) {
        PartyDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        Path path = fileStorageService.resolve(doc.getStoredPath());
        Resource resource = new FileSystemResource(path);
        String ct = doc.getContentType() != null ? doc.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        Map<String, Object> m = new HashMap<>();
        m.put("resource", resource);
        m.put("contentType", ct);
        m.put("filename", doc.getOriginalName());
        return m;
    }

    private PartyResponse enrich(Party party) {
        partnerAppUserService.tryAdvanceParty(party.getId());
        party = partyRepository.findById(party.getId()).orElse(party);

        PartyResponse res = PartyResponse.from(party);
        if (party.getBrandId() != null) {
            brandRepository.findById(party.getBrandId()).ifPresent(b -> {
                res.setBrandId(b.getId());
                res.setBrandCode(b.getCode());
                res.setBrandName(b.getName());
            });
        }
        res.setDocuments(documentRepository.findByPartyIdOrderByUploadedAtDesc(party.getId()).stream()
                .map(PartyResponse.DocumentItem::from)
                .collect(Collectors.toList()));
        int docsPending = (int) res.getDocuments().stream()
                .filter(d -> d.getStatus() == DocumentStatus.PENDING).count();
        int docsRejected = (int) res.getDocuments().stream()
                .filter(d -> d.getStatus() == DocumentStatus.REJECTED).count();
        res.setDocsPending(docsPending);
        res.setDocsRejected(docsRejected);
        res.setDocsReadyForApprove(docsPending == 0 && docsRejected == 0 && !res.getDocuments().isEmpty());
        res.setAssociatedPersons(associatedPersonRepository.findByPartyIdOrderByIdAsc(party.getId()).stream()
                .map(PartyResponse.AssociatedPersonItem::from)
                .collect(Collectors.toList()));
        List<PartnerInviteResponse> invites = partnerInviteService.listForParty(party.getId());
        res.setPartnerInvites(invites);
        List<com.dfs.corporate.web.dto.PartnerAppUserResponse> appUsers = partnerAppUserService.listForParty(party.getId());
        res.setPartnerAppUsers(appUsers);
        if (!appUsers.isEmpty()) {
            res.setPartnerKycTotal(appUsers.size());
            res.setPartnerKycCompleted((int) appUsers.stream()
                    .filter(u -> u.getStatus() == PartnerAppKycStatus.KYC_COMPLETED)
                    .count());
        } else {
            res.setPartnerKycTotal(invites.size());
            res.setPartnerKycCompleted((int) invites.stream()
                    .filter(i -> i.getStatus() == PartnerInviteStatus.COMPLETED)
                    .count());
        }
        if (party.getDecisionDueAt() != null
                && (party.getStatus() == PartyStatus.PENDING_APPROVAL || party.getStatus() == PartyStatus.SUBMITTED)) {
            res.setTatOverdue(party.getDecisionDueAt().isBefore(Instant.now()));
        } else {
            res.setTatOverdue(false);
        }
        return res;
    }

    private PartyStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return null;
        try {
            return PartyStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid status filter");
        }
    }

    private String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
