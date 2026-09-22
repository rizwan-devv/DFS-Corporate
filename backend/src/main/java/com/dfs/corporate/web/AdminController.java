package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.AdminOnboardingService;
import com.dfs.corporate.service.AmlWatchlistImportService;
import com.dfs.corporate.service.CmsCardService;
import com.dfs.corporate.web.dto.PartnerAppUserResponse;
import com.dfs.corporate.web.dto.PartnerInviteResponse;
import com.dfs.corporate.web.dto.PartyResponse;
import com.dfs.corporate.web.dto.RejectRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminOnboardingService adminOnboardingService;
    private final AmlWatchlistImportService amlWatchlistImportService;
    private final CmsCardService cmsCardService;

    public AdminController(AdminOnboardingService adminOnboardingService,
                           AmlWatchlistImportService amlWatchlistImportService,
                           CmsCardService cmsCardService) {
        this.adminOnboardingService = adminOnboardingService;
        this.amlWatchlistImportService = amlWatchlistImportService;
        this.cmsCardService = cmsCardService;
    }

    @GetMapping("/brands")
    public List<Map<String, Object>> brands() {
        return adminOnboardingService.brands();
    }

    @GetMapping("/parties/pending")
    public List<PartyResponse> pending() {
        return adminOnboardingService.pending();
    }

    @GetMapping("/parties")
    public List<PartyResponse> all(@RequestParam(required = false) Long brandId,
                                   @RequestParam(required = false) String status) {
        return adminOnboardingService.all(brandId, status);
    }

    @GetMapping("/parties/{id}")
    public PartyResponse get(@PathVariable Long id) {
        return adminOnboardingService.get(id);
    }

    @PostMapping("/parties/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id, @AuthenticationPrincipal AccountPrincipal admin) {
        return adminOnboardingService.approve(id, admin);
    }

    @PostMapping("/parties/{id}/retry-account-provision")
    public PartyResponse retryAccountProvision(@PathVariable Long id) {
        return adminOnboardingService.retryAccountProvision(id);
    }

    @PostMapping("/parties/{id}/reject")
    public PartyResponse reject(@PathVariable Long id,
                                @Valid @RequestBody RejectRequest req,
                                @AuthenticationPrincipal AccountPrincipal admin) {
        return adminOnboardingService.reject(id, req, admin);
    }

    @PostMapping("/documents/{id}/approve")
    public PartyResponse approveDoc(@PathVariable Long id) {
        return adminOnboardingService.reviewDocument(id, true, null);
    }

    @PostMapping("/documents/{id}/reject")
    public PartyResponse rejectDoc(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String note = body != null ? body.get("note") : null;
        return adminOnboardingService.reviewDocument(id, false, note);
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<Resource> documentFile(@PathVariable Long id) {
        Map<String, Object> file = adminOnboardingService.documentFile(id);
        Resource resource = (Resource) file.get("resource");
        String contentType = (String) file.get("contentType");
        String filename = (String) file.get("filename");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @PostMapping("/parties/{id}/sanctions")
    public PartyResponse sanctions(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return adminOnboardingService.setSanctions(id,
                com.dfs.corporate.domain.ScreeningStatus.valueOf(body.getOrDefault("status", "CLEAR")),
                body.get("notes"));
    }

    @PostMapping("/parties/{id}/sanctions/screen")
    public PartyResponse rescreen(@PathVariable Long id) {
        return adminOnboardingService.rescreenSanctions(id);
    }

    @GetMapping("/parties/{id}/sanctions/results")
    public List<com.dfs.corporate.domain.AmlScreenResult> sanctionResults(@PathVariable Long id) {
        return adminOnboardingService.amlResults(id);
    }

    @GetMapping("/aml/watchlist")
    public Map<String, Object> amlWatchlist() {
        Map<String, Object> out = new HashMap<>();
        out.put("summary", adminOnboardingService.amlSummary());
        out.put("entries", adminOnboardingService.amlWatchlist());
        return out;
    }

    @PostMapping(value = "/aml/watchlist/csv", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, Object> importAml(@RequestBody String csv) {
        return adminOnboardingService.importAmlCsv(csv);
    }

    /** Download + parse official OFAC SDN/ALT CSV and UN consolidated XML (no scraping). */
    @PostMapping("/aml/watchlist/refresh")
    public Map<String, Object> refreshAmlWatchlist() {
        return amlWatchlistImportService.refreshOfficialLists();
    }

    @PostMapping("/parties/{id}/identity-verification")
    public PartyResponse identity(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return adminOnboardingService.setIdentityVerification(id,
                com.dfs.corporate.domain.IdentityVerificationStatus.valueOf(body.getOrDefault("status", "WAIVED_MANUAL")),
                body.getOrDefault("method", "ADMIN"));
    }

    @PostMapping("/parties/{id}/discrepancy")
    public PartyResponse discrepancy(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return adminOnboardingService.setDiscrepancy(id, body.get("note"));
    }

    /** Link CMS Relationship # (AgentApp /card/inquiry) — does not change DFS wallet id. */
    @PutMapping("/parties/{id}/cms-relationship")
    public PartyResponse linkCmsRelationship(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String rel = body != null ? body.get("relationshipNum") : null;
        return cmsCardService.linkRelationshipForParty(id, rel);
    }

    @PostMapping("/partner-invites/{id}/resend")
    public PartnerInviteResponse resendInvite(@PathVariable Long id) {
        return adminOnboardingService.resendPartnerInvite(id);
    }

    @PostMapping("/partner-app-users/{id}/resend")
    public PartnerAppUserResponse resendAppInvite(@PathVariable Long id) {
        return adminOnboardingService.resendAppInvite(id);
    }

    /** Dev / ops stub until mobile app is live */
    @PostMapping("/partner-app-users/{id}/mark-kyc-complete")
    public PartnerAppUserResponse markAppKycComplete(@PathVariable Long id) {
        return adminOnboardingService.markAppKycComplete(id);
    }

    /** After 3 phone KYC fails — bank visit; backoffice completes this partner only with reason. */
    @PostMapping("/partner-app-users/{id}/manual-kyc-approve")
    public PartnerAppUserResponse manualKycApprove(@PathVariable Long id,
                                                   @RequestBody Map<String, String> body,
                                                   @AuthenticationPrincipal AccountPrincipal admin) {
        return adminOnboardingService.manualKycApprove(id, body != null ? body.get("reason") : null, admin);
    }
}
