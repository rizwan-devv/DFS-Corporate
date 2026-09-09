package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.FranchiseCommissionEntryRepository;
import com.dfs.corporate.repository.FranchiseCommissionPlanRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.repository.PartyWalletBalanceRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.FranchiseCommissionEntryResponse;
import com.dfs.corporate.web.dto.FranchiseTxnPostRequest;
import com.dfs.corporate.web.dto.FranchiseWalletResponse;
import com.dfs.corporate.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Real-time franchise commission split on successful child transactions.
 * Uses LOCKED PERCENT_GROSS plan: parent gets rate%, child gets residual.
 */
@Service
public class FranchiseCommissionLedgerService {

    private static final Logger log = LoggerFactory.getLogger(FranchiseCommissionLedgerService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FranchiseCommissionEntryRepository entryRepository;
    private final FranchiseCommissionPlanRepository planRepository;
    private final PartyWalletBalanceRepository walletRepository;
    private final PartyRepository partyRepository;

    public FranchiseCommissionLedgerService(FranchiseCommissionEntryRepository entryRepository,
                                            FranchiseCommissionPlanRepository planRepository,
                                            PartyWalletBalanceRepository walletRepository,
                                            PartyRepository partyRepository) {
        this.entryRepository = entryRepository;
        this.planRepository = planRepository;
        this.walletRepository = walletRepository;
        this.partyRepository = partyRepository;
    }

    @Transactional
    public FranchiseCommissionEntryResponse post(AccountPrincipal principal, FranchiseTxnPostRequest req) {
        Party actor = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        Party child = partyRepository.findById(req.getChildPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Child franchise not found"));

        assertCanPost(actor, principal, child);

        String ref = req.getExternalTxnRef().trim();
        var existing = entryRepository.findByExternalTxnRef(ref);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        return postInternal(child, ref, req.getGrossAmount(),
                normalizeCurrency(req.getCurrency()),
                trimOr(req.getTxnType(), "INCOMING"),
                trimOr(req.getSource(), "API"),
                trim(req.getNotes()));
    }

    /**
     * Core split used by portal/API and future payment-rail webhooks.
     * If no LOCKED plan: full gross stays with child (no parent commission).
     */
    @Transactional
    public FranchiseCommissionEntryResponse postInternal(Party child,
                                                         String externalTxnRef,
                                                         BigDecimal grossAmount,
                                                         String currency,
                                                         String txnType,
                                                         String source,
                                                         String notes) {
        if (child.getPartyType() != PartyType.SUB_MERCHANT || child.getParentPartyId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Commission split only applies to franchise (child) parties");
        }
        if (child.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Child franchise must be ACTIVE to post transactions");
        }
        Party parent = partyRepository.findById(child.getParentPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Parent corporate not found"));
        if (parent.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Parent corporate must be ACTIVE");
        }

        BigDecimal gross = grossAmount.setScale(2, RoundingMode.HALF_UP);
        if (gross.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Gross amount must be greater than zero");
        }

        FranchiseCommissionPlan plan = planRepository.findByChildPartyId(child.getId()).orElse(null);
        BigDecimal rate = BigDecimal.ZERO;
        Long planId = null;
        if (plan != null && plan.getStatus() == CommissionPlanStatus.LOCKED) {
            rate = plan.getCommissionRatePercent() != null ? plan.getCommissionRatePercent() : BigDecimal.ZERO;
            planId = plan.getId();
        }

        BigDecimal commission = gross.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        if (commission.compareTo(gross) > 0) {
            commission = gross;
        }
        BigDecimal childNet = gross.subtract(commission).setScale(2, RoundingMode.HALF_UP);

        FranchiseCommissionEntry entry = new FranchiseCommissionEntry();
        entry.setPublicId(UUID.randomUUID().toString());
        entry.setParentPartyId(parent.getId());
        entry.setChildPartyId(child.getId());
        entry.setPlanId(planId);
        entry.setExternalTxnRef(externalTxnRef);
        entry.setTxnType(txnType);
        entry.setCurrency(currency);
        entry.setGrossAmount(gross);
        entry.setRatePercent(rate);
        entry.setCommissionAmount(commission);
        entry.setChildNetAmount(childNet);
        entry.setStatus(CommissionEntryStatus.POSTED);
        entry.setSource(source);
        entry.setNotes(notes);
        entry.setPostedAt(Instant.now());
        entryRepository.save(entry);

        credit(parent.getId(), currency, commission, true);
        credit(child.getId(), currency, childNet, false);

        log.info("Franchise commission posted ref={} child={} gross={} rate={}% parent={} childNet={}",
                externalTxnRef, child.getId(), gross, rate, commission, childNet);

        return toResponse(entry);
    }

    @Transactional
    public FranchiseCommissionEntryResponse reverse(AccountPrincipal principal, String publicId) {
        Party actor = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        FranchiseCommissionEntry entry = entryRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Commission entry not found"));

        assertCanManageEntry(actor, principal, entry);

        if (entry.getStatus() == CommissionEntryStatus.REVERSED) {
            return toResponse(entry);
        }
        if (entryRepository.existsByReverseOfId(entry.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Entry already reversed");
        }

        debit(entry.getParentPartyId(), entry.getCurrency(), entry.getCommissionAmount(), true);
        debit(entry.getChildPartyId(), entry.getCurrency(), entry.getChildNetAmount(), false);

        entry.setStatus(CommissionEntryStatus.REVERSED);
        entry.setReversedAt(Instant.now());
        entryRepository.save(entry);

        FranchiseCommissionEntry reverseRow = new FranchiseCommissionEntry();
        reverseRow.setPublicId(UUID.randomUUID().toString());
        reverseRow.setParentPartyId(entry.getParentPartyId());
        reverseRow.setChildPartyId(entry.getChildPartyId());
        reverseRow.setPlanId(entry.getPlanId());
        reverseRow.setExternalTxnRef(entry.getExternalTxnRef() + "-REV-" + Instant.now().toEpochMilli());
        reverseRow.setTxnType("REVERSAL");
        reverseRow.setCurrency(entry.getCurrency());
        reverseRow.setGrossAmount(entry.getGrossAmount().negate());
        reverseRow.setRatePercent(entry.getRatePercent());
        reverseRow.setCommissionAmount(entry.getCommissionAmount().negate());
        reverseRow.setChildNetAmount(entry.getChildNetAmount().negate());
        reverseRow.setStatus(CommissionEntryStatus.POSTED);
        reverseRow.setSource(entry.getSource());
        reverseRow.setNotes("Reversal of " + entry.getPublicId());
        reverseRow.setPostedAt(Instant.now());
        reverseRow.setReverseOfId(entry.getId());
        entryRepository.save(reverseRow);

        log.info("Franchise commission reversed publicId={} ref={}", entry.getPublicId(), entry.getExternalTxnRef());
        return toResponse(entry);
    }

    public List<FranchiseCommissionEntryResponse> list(AccountPrincipal principal) {
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        List<FranchiseCommissionEntry> rows;
        if (party.getPartyType() == PartyType.MERCHANT) {
            rows = entryRepository.findByParentPartyIdOrderByPostedAtDesc(party.getId());
        } else {
            rows = entryRepository.findByChildPartyIdOrderByPostedAtDesc(party.getId());
        }
        return rows.stream().map(this::toResponse).toList();
    }

    public FranchiseWalletResponse wallet(AccountPrincipal principal, String currency) {
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        String ccy = normalizeCurrency(currency);
        PartyWalletBalance bal = walletRepository.findByPartyIdAndCurrency(party.getId(), ccy)
                .orElse(null);
        FranchiseWalletResponse r = new FranchiseWalletResponse();
        r.setPartyId(party.getId());
        r.setCurrency(ccy);
        r.setAvailableBalance(bal != null ? bal.getAvailableBalance() : BigDecimal.ZERO.setScale(2));
        r.setCommissionEarned(bal != null ? bal.getCommissionEarned() : BigDecimal.ZERO.setScale(2));
        r.setUpdatedAt(bal != null ? bal.getUpdatedAt() : null);
        return r;
    }

    private void assertCanPost(Party actor, AccountPrincipal principal, Party child) {
        if (principal.getRole() == Role.PLATFORM_ADMIN) {
            return;
        }
        if (actor.getPartyType() == PartyType.MERCHANT
                && Objects.equals(child.getParentPartyId(), actor.getId())
                && actor.getStatus() == PartyStatus.ACTIVE) {
            return;
        }
        if (Objects.equals(actor.getId(), child.getId()) && actor.getStatus() == PartyStatus.ACTIVE) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Not allowed to post this franchise transaction");
    }

    private void assertCanManageEntry(Party actor, AccountPrincipal principal, FranchiseCommissionEntry entry) {
        if (principal.getRole() == Role.PLATFORM_ADMIN) {
            return;
        }
        if (actor.getPartyType() == PartyType.MERCHANT
                && Objects.equals(actor.getId(), entry.getParentPartyId())
                && actor.getStatus() == PartyStatus.ACTIVE) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Only parent corporate can reverse commission entries");
    }

    private void credit(Long partyId, String currency, BigDecimal amount, boolean isCommission) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        PartyWalletBalance bal = getOrCreate(partyId, currency);
        bal.setAvailableBalance(bal.getAvailableBalance().add(amount).setScale(2, RoundingMode.HALF_UP));
        if (isCommission) {
            bal.setCommissionEarned(bal.getCommissionEarned().add(amount).setScale(2, RoundingMode.HALF_UP));
        }
        bal.setUpdatedAt(Instant.now());
        walletRepository.save(bal);
    }

    private void debit(Long partyId, String currency, BigDecimal amount, boolean isCommission) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        PartyWalletBalance bal = getOrCreate(partyId, currency);
        bal.setAvailableBalance(bal.getAvailableBalance().subtract(amount).setScale(2, RoundingMode.HALF_UP));
        if (isCommission) {
            bal.setCommissionEarned(bal.getCommissionEarned().subtract(amount).setScale(2, RoundingMode.HALF_UP));
        }
        bal.setUpdatedAt(Instant.now());
        walletRepository.save(bal);
    }

    private PartyWalletBalance getOrCreate(Long partyId, String currency) {
        return walletRepository.findByPartyIdAndCurrency(partyId, currency).orElseGet(() -> {
            PartyWalletBalance b = new PartyWalletBalance();
            b.setPartyId(partyId);
            b.setCurrency(currency);
            b.setAvailableBalance(BigDecimal.ZERO.setScale(2));
            b.setCommissionEarned(BigDecimal.ZERO.setScale(2));
            b.setUpdatedAt(Instant.now());
            return walletRepository.save(b);
        });
    }

    private FranchiseCommissionEntryResponse toResponse(FranchiseCommissionEntry e) {
        FranchiseCommissionEntryResponse r = new FranchiseCommissionEntryResponse();
        r.setId(e.getId());
        r.setPublicId(e.getPublicId());
        r.setParentPartyId(e.getParentPartyId());
        r.setChildPartyId(e.getChildPartyId());
        r.setPlanId(e.getPlanId());
        r.setExternalTxnRef(e.getExternalTxnRef());
        r.setTxnType(e.getTxnType());
        r.setCurrency(e.getCurrency());
        r.setGrossAmount(e.getGrossAmount());
        r.setRatePercent(e.getRatePercent());
        r.setCommissionAmount(e.getCommissionAmount());
        r.setChildNetAmount(e.getChildNetAmount());
        r.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        r.setSource(e.getSource());
        r.setNotes(e.getNotes());
        r.setPostedAt(e.getPostedAt());
        r.setReversedAt(e.getReversedAt());
        partyRepository.findById(e.getChildPartyId()).ifPresent(c -> {
            r.setChildTrackingId(c.getTrackingId());
            r.setChildBusinessName(c.getBusinessName() != null ? c.getBusinessName() : c.getFullName());
        });
        return r;
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) return "PKR";
        return currency.trim().toUpperCase();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String trimOr(String s, String fallback) {
        String t = trim(s);
        return t != null ? t : fallback;
    }
}
