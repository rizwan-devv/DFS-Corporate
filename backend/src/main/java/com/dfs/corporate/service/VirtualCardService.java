package com.dfs.corporate.service;

import com.dfs.corporate.domain.Party;
import com.dfs.corporate.domain.VirtualCardOrder;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.repository.VirtualCardOrderRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VirtualCardService {

    private final VirtualCardOrderRepository orderRepository;
    private final PartyRepository partyRepository;
    private final SecureRandom random = new SecureRandom();

    public VirtualCardService(VirtualCardOrderRepository orderRepository, PartyRepository partyRepository) {
        this.orderRepository = orderRepository;
        this.partyRepository = partyRepository;
    }

    public Map<String, Object> mine(AccountPrincipal principal) {
        Party party = requireParty(principal);
        VirtualCardOrder current = orderRepository.findByPartyIdOrderByIdDesc(party.getId()).stream()
                .filter(o -> "PENDING".equals(o.getStatus()) || "APPROVED".equals(o.getStatus()))
                .findFirst()
                .orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("relationshipNum", relationshipOf(party));
        out.put("printable", false);
        out.put("order", current == null ? null : toView(current, party));
        return out;
    }

    @Transactional
    public Map<String, Object> order(AccountPrincipal principal, String embossName) {
        Party party = requireParty(principal);
        if (party.getPartyType() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Party is required");
        }
        String name = embossName == null ? "" : embossName.trim().toUpperCase();
        if (name.length() < 2 || name.length() > 26) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Name on card must be 2–26 characters");
        }
        if (orderRepository.existsByPartyIdAndStatusIn(party.getId(), List.of("PENDING", "APPROVED"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "This account already has a virtual card or a request waiting for back office");
        }
        VirtualCardOrder row = new VirtualCardOrder();
        row.setPublicId(UUID.randomUUID().toString());
        row.setPartyId(party.getId());
        row.setRelationshipNum(relationshipOf(party));
        row.setEmbossName(name);
        row.setStatus("PENDING");
        row.setRequestedBy(principal.getUsername());
        row.setCreatedAt(Instant.now());
        orderRepository.save(row);
        return toView(row, party);
    }

    public List<Map<String, Object>> pending() {
        return orderRepository.findByStatusOrderByCreatedAtAsc("PENDING").stream()
                .map(o -> toView(o, partyRepository.findById(o.getPartyId()).orElse(null)))
                .toList();
    }

    @Transactional
    public Map<String, Object> approve(Long id, String officer) {
        VirtualCardOrder row = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Virtual card request not found"));
        if (!"PENDING".equals(row.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a pending request can be approved");
        }
        String last4 = String.format("%04d", random.nextInt(10000));
        YearMonth exp = YearMonth.now().plusYears(3);
        row.setLast4(last4);
        row.setMaskedPan("4532 **** **** " + last4);
        row.setExpiry(String.format("%02d/%02d", exp.getMonthValue(), exp.getYear() % 100));
        row.setStatus("APPROVED");
        row.setDecidedBy(officer != null ? officer : "BACKOFFICE");
        row.setDecidedAt(Instant.now());
        row.setDecisionNote("Mock virtual card. Not sent to CMS. Not printable.");
        orderRepository.save(row);
        return toView(row, partyRepository.findById(row.getPartyId()).orElse(null));
    }

    @Transactional
    public Map<String, Object> reject(Long id, String note, String officer) {
        VirtualCardOrder row = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Virtual card request not found"));
        if (!"PENDING".equals(row.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a pending request can be rejected");
        }
        row.setStatus("REJECTED");
        row.setDecidedBy(officer != null ? officer : "BACKOFFICE");
        row.setDecidedAt(Instant.now());
        row.setDecisionNote(note == null || note.isBlank() ? "Rejected by back office" : note.trim());
        orderRepository.save(row);
        return toView(row, partyRepository.findById(row.getPartyId()).orElse(null));
    }

    private Party requireParty(AccountPrincipal principal) {
        if (principal == null || principal.getPartyId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Login required");
        }
        return partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
    }

    private static String relationshipOf(Party party) {
        if (party.getCmsRelationshipNum() != null && !party.getCmsRelationshipNum().isBlank()) {
            return party.getCmsRelationshipNum().trim();
        }
        if (party.getCnicNumber() != null && !party.getCnicNumber().isBlank()) {
            String digits = IdentityFormats.cnicDigits(party.getCnicNumber());
            if (!digits.isBlank()) return digits;
        }
        return party.getId() != null ? String.valueOf(party.getId()) : "";
    }

    private static Map<String, Object> toView(VirtualCardOrder row, Party party) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("publicId", row.getPublicId());
        m.put("partyId", row.getPartyId());
        m.put("businessName", party != null ? party.getBusinessName() : null);
        m.put("relationshipNum", row.getRelationshipNum());
        m.put("embossName", row.getEmbossName());
        m.put("status", row.getStatus());
        m.put("cardForm", "VIRTUAL");
        m.put("printable", false);
        m.put("maskedPan", row.getMaskedPan());
        m.put("last4", row.getLast4());
        m.put("expiry", row.getExpiry());
        m.put("network", "DFS Pay");
        m.put("productName", "Virtual");
        m.put("requestedBy", row.getRequestedBy());
        m.put("decidedBy", row.getDecidedBy());
        m.put("decisionNote", row.getDecisionNote());
        m.put("createdAt", row.getCreatedAt());
        m.put("decidedAt", row.getDecidedAt());
        return m;
    }
}
