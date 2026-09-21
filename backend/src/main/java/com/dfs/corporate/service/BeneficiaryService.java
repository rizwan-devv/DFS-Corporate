package com.dfs.corporate.service;

import com.dfs.corporate.domain.Beneficiary;
import com.dfs.corporate.domain.BeneficiaryRail;
import com.dfs.corporate.domain.Party;
import com.dfs.corporate.domain.PartyStatus;
import com.dfs.corporate.repository.BeneficiaryRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.BeneficiaryRequest;
import com.dfs.corporate.web.dto.BeneficiaryResponse;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final PartyRepository partyRepository;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository, PartyRepository partyRepository) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.partyRepository = partyRepository;
    }

    public List<BeneficiaryResponse> list(AccountPrincipal principal, String forProduct, boolean activeOnly) {
        Party party = requireActiveParty(principal);
        List<Beneficiary> rows = activeOnly
                ? beneficiaryRepository.findByPartyIdAndActiveTrueOrderByAliasNameAsc(party.getId())
                : beneficiaryRepository.findByPartyIdOrderByAliasNameAsc(party.getId());

        Set<BeneficiaryRail> allowed = railsForProduct(forProduct);
        return rows.stream()
                .filter(b -> allowed == null || allowed.contains(b.getRailScope()))
                .map(BeneficiaryResponse::from)
                .toList();
    }

    public BeneficiaryResponse get(AccountPrincipal principal, String publicId) {
        return BeneficiaryResponse.from(requireOwned(principal, publicId));
    }

    @Transactional
    public BeneficiaryResponse create(AccountPrincipal principal, BeneficiaryRequest req) {
        Party party = requireActiveParty(principal);
        BeneficiaryRail rail = parseRail(req.getRailScope());
        validateFields(rail, req);

        Beneficiary b = new Beneficiary();
        b.setPublicId(UUID.randomUUID().toString());
        b.setPartyId(party.getId());
        b.setCreatedBy(principal.getUsername());
        apply(b, req, rail);
        b.setActive(req.getActive() == null || req.getActive());
        b.setCreatedAt(Instant.now());
        b.setUpdatedAt(Instant.now());
        return BeneficiaryResponse.from(beneficiaryRepository.save(b));
    }

    @Transactional
    public BeneficiaryResponse update(AccountPrincipal principal, String publicId, BeneficiaryRequest req) {
        Beneficiary b = requireOwned(principal, publicId);
        BeneficiaryRail rail = parseRail(req.getRailScope());
        validateFields(rail, req);
        apply(b, req, rail);
        if (req.getActive() != null) {
            b.setActive(req.getActive());
        }
        b.setUpdatedAt(Instant.now());
        return BeneficiaryResponse.from(beneficiaryRepository.save(b));
    }

    @Transactional
    public void deactivate(AccountPrincipal principal, String publicId) {
        Beneficiary b = requireOwned(principal, publicId);
        b.setActive(false);
        b.setUpdatedAt(Instant.now());
        beneficiaryRepository.save(b);
    }

    private void apply(Beneficiary b, BeneficiaryRequest req, BeneficiaryRail rail) {
        b.setAliasName(trimRequired(req.getAliasName(), "aliasName"));
        b.setFullName(trimRequired(req.getFullName(), "fullName"));
        b.setAccountNumber(trim(req.getAccountNumber()));
        b.setBankName(trim(req.getBankName()));
        b.setRaastId(trim(req.getRaastId()));
        b.setMobile(trim(req.getMobile()));
        b.setCnic(trim(req.getCnic()));
        b.setRailScope(rail);
        b.setNotes(trim(req.getNotes()));
    }

    private void validateFields(BeneficiaryRail rail, BeneficiaryRequest req) {
        if (rail == BeneficiaryRail.RAAST) {
            if (isBlank(req.getRaastId()) && isBlank(req.getAccountNumber())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Raast ID or IBAN/account number is required");
            }
        } else {
            if (isBlank(req.getAccountNumber())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Account number is required");
            }
            if ((rail == BeneficiaryRail.IBFT || rail == BeneficiaryRail.FT_IBFT) && isBlank(req.getBankName())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bank name is required for IBFT beneficiaries");
            }
        }
    }

    /**
     * @return null = no filter; otherwise rails that apply to the transfer product
     */
    private Set<BeneficiaryRail> railsForProduct(String forProduct) {
        if (forProduct == null || forProduct.isBlank()) {
            return null;
        }
        String p = forProduct.trim().toUpperCase(Locale.ROOT);
        return switch (p) {
            case "FT" -> EnumSet.of(BeneficiaryRail.FT, BeneficiaryRail.FT_IBFT);
            case "IBFT" -> EnumSet.of(BeneficiaryRail.IBFT, BeneficiaryRail.FT_IBFT);
            case "RAAST" -> EnumSet.of(BeneficiaryRail.RAAST);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "forProduct must be FT, IBFT, or RAAST");
        };
    }

    private BeneficiaryRail parseRail(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "railScope is required (FT, IBFT, FT_IBFT, RAAST)");
        }
        try {
            return BeneficiaryRail.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid railScope: " + raw);
        }
    }

    private Beneficiary requireOwned(AccountPrincipal principal, String publicId) {
        Party party = requireActiveParty(principal);
        return beneficiaryRepository.findByPublicIdAndPartyId(publicId, party.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Beneficiary not found"));
    }

    private Party requireActiveParty(AccountPrincipal principal) {
        if (principal.getPartyId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Merchant party required");
        }
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Beneficiaries available after entity is ACTIVE");
        }
        return party;
    }

    private static String trimRequired(String s, String field) {
        if (isBlank(s)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
