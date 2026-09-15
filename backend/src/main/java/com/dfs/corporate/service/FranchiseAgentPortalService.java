package com.dfs.corporate.service;

import com.dfs.corporate.domain.Party;
import com.dfs.corporate.domain.PartyStatus;
import com.dfs.corporate.domain.PartyType;
import com.dfs.corporate.integration.dfs.CorporatePortalAgentAppClient;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.web.dto.AgentAppPortalResponse;
import com.dfs.corporate.web.dto.FranchiseChildChangeMpinRequest;
import com.dfs.corporate.web.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * AgentApp portal balance / mini-statement / change MPIN for franchise children and own party.
 */
@Service
public class FranchiseAgentPortalService {

    private final PartyRepository partyRepository;
    private final CorporatePortalAgentAppClient portalClient;
    private final String defaultLevelCode;

    public FranchiseAgentPortalService(PartyRepository partyRepository,
                                       CorporatePortalAgentAppClient portalClient,
                                       @Value("${dfs.account-api.level-code:L4}") String defaultLevelCode) {
        this.partyRepository = partyRepository;
        this.portalClient = portalClient;
        this.defaultLevelCode = defaultLevelCode;
    }

    public AgentAppPortalResponse getBalance(AccountPrincipal principal, Long childPartyId) {
        Party child = requireOwnedChild(principal, childPartyId);
        String mobile = requireMobile(child);
        String level = levelOf(child);
        try {
            return toResponse(child, mobile, level, portalClient.getBalance(mobile, level));
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    public AgentAppPortalResponse miniStatement(AccountPrincipal principal, Long childPartyId,
                                                String fromDate, String toDate) {
        Party child = requireOwnedChild(principal, childPartyId);
        String mobile = requireMobile(child);
        String level = levelOf(child);
        boolean hasFrom = fromDate != null && !fromDate.isBlank();
        boolean hasTo = toDate != null && !toDate.isBlank();
        if (hasFrom != hasTo) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fromDate and toDate must both be supplied or both omitted");
        }
        try {
            return toResponse(child, mobile, level,
                    portalClient.miniStatement(mobile, level,
                            hasFrom ? fromDate.trim() : null,
                            hasTo ? toDate.trim() : null));
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    public AgentAppPortalResponse changeMpin(AccountPrincipal principal, Long childPartyId,
                                             FranchiseChildChangeMpinRequest req) {
        Party child = requireOwnedChild(principal, childPartyId);
        String mobile = requireMobile(child);
        String level = levelOf(child);
        if (!req.getNewMpin().equals(req.getConfirmMpin())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "newMpin and confirmMpin must match");
        }
        try {
            return toResponse(child, mobile, level,
                    portalClient.changeMpin(mobile, req.getCurrentMpin(), req.getNewMpin(), req.getConfirmMpin()));
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    /** Logged-in party's own AgentApp wallet balance (parent or active party). */
    public AgentAppPortalResponse getOwnBalance(AccountPrincipal principal) {
        Party party = requireActiveOwnParty(principal);
        String mobile = requireMobile(party);
        String level = levelOf(party);
        try {
            return toResponse(party, mobile, level, portalClient.getBalance(mobile, level));
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    /** Logged-in party's own AgentApp mini-statement. */
    public AgentAppPortalResponse getOwnMiniStatement(AccountPrincipal principal,
                                                      String fromDate, String toDate) {
        Party party = requireActiveOwnParty(principal);
        String mobile = requireMobile(party);
        String level = levelOf(party);
        boolean hasFrom = fromDate != null && !fromDate.isBlank();
        boolean hasTo = toDate != null && !toDate.isBlank();
        if (hasFrom != hasTo) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fromDate and toDate must both be supplied or both omitted");
        }
        try {
            return toResponse(party, mobile, level,
                    portalClient.miniStatement(mobile, level,
                            hasFrom ? fromDate.trim() : null,
                            hasTo ? toDate.trim() : null));
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    private Party requireActiveOwnParty(AccountPrincipal principal) {
        if (principal.getPartyId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No party linked to this account");
        }
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Party must be ACTIVE to view AgentApp balance/statement");
        }
        return party;
    }

    private Party requireOwnedChild(AccountPrincipal principal, Long childPartyId) {
        Party parent = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Parent party not found"));
        if (parent.getPartyType() != PartyType.MERCHANT || parent.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only an active corporate (master) can view franchise AgentApp data");
        }
        Party child = partyRepository.findById(childPartyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Franchise not found"));
        if (!java.util.Objects.equals(child.getParentPartyId(), parent.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Franchise does not belong to this corporate parent");
        }
        return child;
    }

    private String requireMobile(Party party) {
        String mobile = IdentityFormats.phoneDigits(party.getPhone());
        if (mobile == null || mobile.length() < 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Party has no valid mobile number for AgentApp lookup");
        }
        return mobile;
    }

    private String levelOf(Party party) {
        if (party.getLevelCode() != null && !party.getLevelCode().isBlank()) {
            return party.getLevelCode().trim();
        }
        return defaultLevelCode;
    }

    private static AgentAppPortalResponse toResponse(Party party, String mobile, String level, JsonNode root) {
        AgentAppPortalResponse r = new AgentAppPortalResponse();
        r.setChildPartyId(party.getId());
        r.setChildTrackingId(party.getTrackingId());
        r.setMobileNumber(mobile);
        r.setAccountLevelCode(level);
        if (root != null) {
            if (root.has("responsecode")) {
                r.setResponsecode(root.get("responsecode").asText(null));
            } else if (root.has("responseCode")) {
                r.setResponsecode(root.get("responseCode").asText(null));
            }
            if (root.has("messages")) {
                r.setMessages(root.get("messages").asText(null));
            } else if (root.has("message")) {
                r.setMessages(root.get("message").asText(null));
            }
            if (root.has("data")) {
                r.setData(root.get("data"));
            }
        }
        return r;
    }
}
