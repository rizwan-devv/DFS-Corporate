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

    @Transactional
    public Map<String, Object> mine(AccountPrincipal principal) {
        Party party = requireParty(principal);
        VirtualCardOrder current = orderRepository.findByPartyIdOrderByIdDesc(party.getId()).stream()
                .filter(o -> "PENDING".equals(o.getStatus()) || "APPROVED".equals(o.getStatus()))
                .findFirst()
                .orElse(null);
        if (current != null && "PENDING".equals(current.getStatus())) {
            markApproved(current, "AUTO");
        } else if (current != null) {
            issueIfMissing(current);
        }
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "This account already has a virtual card");
        }
        VirtualCardOrder row = new VirtualCardOrder();
        row.setPublicId(UUID.randomUUID().toString());
        row.setPartyId(party.getId());
        row.setRelationshipNum(relationshipOf(party));
        row.setEmbossName(name);
        row.setRequestedBy(principal.getUsername());
        row.setCreatedAt(Instant.now());
        markApproved(row, "AUTO");
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
        markApproved(row, officer != null ? officer : "BACKOFFICE");
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

    private void markApproved(VirtualCardOrder row, String officer) {
        issueNew(row);
        row.setStatus("APPROVED");
        row.setDecidedBy(officer != null ? officer : "AUTO");
        row.setDecidedAt(Instant.now());
        row.setDecisionNote("Mock PayFast virtual card. Issued automatically. Not sent to CMS. Not printable.");
        orderRepository.save(row);
    }

    private void issueNew(VirtualCardOrder row) {
        String pan = newPan();
        YearMonth exp = YearMonth.now().plusYears(3);
        row.setPan(pan);
        row.setMaskedPan(pan);
        row.setLast4(pan.substring(pan.length() - 4));
        row.setCvv(newCvv());
        row.setExpiry(String.format("%02d/%02d", exp.getMonthValue(), exp.getYear() % 100));
    }

    /** Cards approved before the full number existed get one the next time they are opened. */
    private void issueIfMissing(VirtualCardOrder row) {
        if (!"APPROVED".equals(row.getStatus())) {
            return;
        }
        boolean missingPan = row.getPan() == null || row.getPan().isBlank();
        boolean missingCvv = row.getCvv() == null || row.getCvv().isBlank();
        if (!missingPan && !missingCvv) {
            return;
        }
        if (missingPan) {
            String pan = newPan();
            row.setPan(pan);
            row.setMaskedPan(pan);
            row.setLast4(pan.substring(pan.length() - 4));
        }
        if (missingCvv) {
            row.setCvv(newCvv());
        }
        if (row.getExpiry() == null || row.getExpiry().isBlank()) {
            YearMonth exp = YearMonth.now().plusYears(3);
            row.setExpiry(String.format("%02d/%02d", exp.getMonthValue(), exp.getYear() % 100));
        }
        orderRepository.save(row);
    }

    private String newPan() {
        int[] digits = new int[16];
        digits[0] = 4;
        digits[1] = 5;
        digits[2] = 3;
        digits[3] = 2;
        for (int i = 4; i < 15; i++) {
            digits[i] = random.nextInt(10);
        }
        digits[15] = luhnCheck(digits);
        StringBuilder pan = new StringBuilder(19);
        for (int i = 0; i < digits.length; i++) {
            if (i > 0 && i % 4 == 0) {
                pan.append(' ');
            }
            pan.append(digits[i]);
        }
        return pan.toString();
    }

    private String newCvv() {
        return String.format("%03d", random.nextInt(1000));
    }

    /** Check digit for a 16-digit payload whose last element is unused. */
    private static int luhnCheck(int[] digits) {
        int sum = 0;
        for (int i = 0; i < 15; i++) {
            int n = digits[i];
            if ((15 - i) % 2 == 1) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
        }
        return (10 - (sum % 10)) % 10;
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
        String pan = row.getPan() != null && !row.getPan().isBlank() ? row.getPan() : row.getMaskedPan();
        m.put("status", row.getStatus());
        m.put("cardForm", "VIRTUAL");
        m.put("printable", false);
        m.put("pan", pan);
        m.put("maskedPan", pan);
        m.put("cvv", row.getCvv());
        m.put("last4", row.getLast4());
        m.put("expiry", row.getExpiry());
        m.put("network", "PayFast");
        m.put("productName", "Virtual");
        m.put("requestedBy", row.getRequestedBy());
        m.put("decidedBy", row.getDecidedBy());
        m.put("decisionNote", row.getDecisionNote());
        m.put("createdAt", row.getCreatedAt());
        m.put("decidedAt", row.getDecidedAt());
        return m;
    }
}
