package com.dfs.corporate.service;

import com.dfs.corporate.domain.Party;
import com.dfs.corporate.domain.PartyStatus;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.AcknowledgmentPublicResponse;
import com.dfs.corporate.web.dto.PartyResponse;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Pre-approval acknowledgment: back office emails a secure link to the registered
 * applicant email; the account holder confirms before Approve is allowed.
 */
@Service
public class CustomerAcknowledgmentService {

    private static final int TOKEN_BYTES = 32;
    private static final int LINK_VALID_DAYS = 7;

    private final PartyRepository partyRepository;
    private final MailService mailService;
    private final String frontendBaseUrl;
    private final String brandName;
    private final SecureRandom random = new SecureRandom();

    public CustomerAcknowledgmentService(
            PartyRepository partyRepository,
            MailService mailService,
            @Value("${app.frontend-base-url}") String frontendBaseUrl,
            @Value("${app.brand-name:PayFast Corporate}") String brandName) {
        this.partyRepository = partyRepository;
        this.mailService = mailService;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        this.brandName = brandName != null && !brandName.isBlank() ? brandName.trim() : "PayFast Corporate";
    }

    @Transactional
    public Map<String, Object> sendAcknowledgment(Long partyId, AccountPrincipal admin) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.PENDING_APPROVAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Acknowledgment can only be sent when the application is ready for approval");
        }
        if (party.getEmail() == null || party.getEmail().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Party has no email address");
        }
        if (party.getAcknowledgedAt() != null) {
            Map<String, Object> already = new HashMap<>();
            already.put("sent", false);
            already.put("alreadyAcknowledged", true);
            already.put("acknowledgedAt", party.getAcknowledgedAt());
            already.put("email", party.getEmail());
            already.put("message", "Customer already acknowledged this application");
            return already;
        }

        String token = newToken();
        Instant now = Instant.now();
        party.setAcknowledgmentToken(token);
        party.setAcknowledgmentSentAt(now);
        party.setAcknowledgmentExpiresAt(now.plus(LINK_VALID_DAYS, ChronoUnit.DAYS));
        party.setAcknowledgmentIp(null);
        partyRepository.save(party);

        String link = frontendBaseUrl + "/acknowledge/" + token;
        String entityLabel = party.getBusinessName() != null && !party.getBusinessName().isBlank()
                ? party.getBusinessName()
                : party.getFullName();
        String tracking = party.getTrackingId() != null ? party.getTrackingId() : String.valueOf(party.getId());
        String officer = admin != null ? admin.getUsername() : "BACKOFFICE";

        mailService.send(
                party.getEmail(),
                brandName + " — Please confirm your corporate application",
                """
                Dear %s,

                Your corporate application for %s (Tracking ID: %s) has been reviewed and is ready for final approval.

                For your security, we need you — the registered account holder — to confirm that you authorised this application and that the details are correct.

                Please open the secure link below and confirm:
                %s

                This link expires in %d days. If you did not request this account, do not confirm the link and contact us immediately.

                Requested by back office: %s

                — %s
                """.formatted(party.getFullName(), entityLabel, tracking, link, LINK_VALID_DAYS, officer, brandName));

        Map<String, Object> out = new HashMap<>();
        out.put("sent", true);
        out.put("alreadyAcknowledged", false);
        out.put("email", party.getEmail());
        out.put("acknowledgmentSentAt", party.getAcknowledgmentSentAt());
        out.put("acknowledgmentExpiresAt", party.getAcknowledgmentExpiresAt());
        out.put("message", "Acknowledgment email sent to " + party.getEmail());
        return out;
    }

    public AcknowledgmentPublicResponse getPublic(String token) {
        AcknowledgmentPublicResponse res = basePublic();
        if (token == null || token.isBlank()) {
            res.setStatus("INVALID");
            res.setMessage("This acknowledgment link is invalid.");
            return res;
        }
        Party party = partyRepository.findByAcknowledgmentToken(token.trim()).orElse(null);
        if (party == null) {
            res.setStatus("INVALID");
            res.setMessage("This acknowledgment link is invalid or has already been used.");
            return res;
        }
        fillPartySummary(res, party);
        if (party.getAcknowledgedAt() != null) {
            res.setStatus("ACKNOWLEDGED");
            res.setMessage("Thank you. Your acknowledgment has already been recorded.");
            res.setAcknowledgedAt(party.getAcknowledgedAt());
            res.setCanConfirm(false);
            return res;
        }
        if (party.getStatus() != PartyStatus.PENDING_APPROVAL) {
            res.setStatus("UNAVAILABLE");
            res.setMessage("This application is no longer awaiting acknowledgment.");
            res.setCanConfirm(false);
            return res;
        }
        if (party.getAcknowledgmentExpiresAt() != null
                && party.getAcknowledgmentExpiresAt().isBefore(Instant.now())) {
            res.setStatus("EXPIRED");
            res.setMessage("This acknowledgment link has expired. Please ask the bank to send a new email.");
            res.setCanConfirm(false);
            return res;
        }
        res.setStatus("PENDING");
        res.setMessage("Please confirm that you authorised this corporate application.");
        res.setCanConfirm(true);
        return res;
    }

    @Transactional
    public AcknowledgmentPublicResponse confirm(String token, String clientIp) {
        AcknowledgmentPublicResponse preview = getPublic(token);
        if (!preview.isCanConfirm()) {
            return preview;
        }
        Party party = partyRepository.findByAcknowledgmentToken(token.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invalid acknowledgment link"));
        party.setAcknowledgedAt(Instant.now());
        if (clientIp != null && !clientIp.isBlank()) {
            party.setAcknowledgmentIp(clientIp.length() > 64 ? clientIp.substring(0, 64) : clientIp);
        }
        // Invalidate token after use so the link cannot be replayed
        party.setAcknowledgmentToken(null);
        partyRepository.save(party);

        mailService.send(
                party.getEmail(),
                brandName + " — Acknowledgment received",
                """
                Dear %s,

                We have recorded your confirmation for application %s.

                Our back office can now complete final approval. You will receive another email when your account is activated.

                — %s
                """.formatted(
                        party.getFullName(),
                        party.getTrackingId() != null ? party.getTrackingId() : party.getId(),
                        brandName));

        AcknowledgmentPublicResponse res = basePublic();
        fillPartySummary(res, party);
        res.setStatus("ACKNOWLEDGED");
        res.setMessage("Thank you. Your acknowledgment has been recorded. The bank may now approve your application.");
        res.setAcknowledgedAt(party.getAcknowledgedAt());
        res.setCanConfirm(false);
        return res;
    }

    public void assertAcknowledged(Party party) {
        if (party.getAcknowledgedAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Cannot approve: customer has not acknowledged via the emailed confirmation link");
        }
    }

    public void applyToResponse(Party party, PartyResponse res) {
        res.setAcknowledgmentSentAt(party.getAcknowledgmentSentAt());
        res.setAcknowledgmentExpiresAt(party.getAcknowledgmentExpiresAt());
        res.setAcknowledgedAt(party.getAcknowledgedAt());
        res.setCustomerAcknowledged(party.getAcknowledgedAt() != null);
        res.setAcknowledgmentPending(party.getAcknowledgedAt() == null
                && party.getAcknowledgmentSentAt() != null
                && party.getStatus() == PartyStatus.PENDING_APPROVAL);
    }

    private AcknowledgmentPublicResponse basePublic() {
        AcknowledgmentPublicResponse res = new AcknowledgmentPublicResponse();
        res.setBrandName(brandName);
        res.setCanConfirm(false);
        return res;
    }

    private void fillPartySummary(AcknowledgmentPublicResponse res, Party party) {
        res.setBusinessName(party.getBusinessName() != null && !party.getBusinessName().isBlank()
                ? party.getBusinessName()
                : party.getFullName());
        res.setTrackingId(party.getTrackingId());
        res.setEmailMasked(maskEmail(party.getEmail()));
        res.setExpiresAt(party.getAcknowledgmentExpiresAt());
    }

    private String newToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }
}
