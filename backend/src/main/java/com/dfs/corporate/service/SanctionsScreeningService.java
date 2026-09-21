package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AmlScreenResultRepository;
import com.dfs.corporate.repository.AmlWatchlistRepository;
import com.dfs.corporate.repository.AssociatedPersonRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interim AML/CFT screening against locally loaded official lists (UN, OFAC, NACTA, INTERNAL).
 * CNIC exact → HIT. Name similarity at/above threshold → MANUAL_REVIEW.
 * A backoffice CLEAR (sanctionsManualClear) is not overwritten unless force=true.
 */
@Service
public class SanctionsScreeningService {

    private final AmlWatchlistRepository watchlistRepository;
    private final AmlScreenResultRepository screenResultRepository;
    private final PartyRepository partyRepository;
    private final AssociatedPersonRepository associatedPersonRepository;
    private final int fuzzyThreshold;

    public SanctionsScreeningService(AmlWatchlistRepository watchlistRepository,
                                     AmlScreenResultRepository screenResultRepository,
                                     PartyRepository partyRepository,
                                     AssociatedPersonRepository associatedPersonRepository,
                                     @Value("${aml.fuzzy-threshold:88}") int fuzzyThreshold) {
        this.watchlistRepository = watchlistRepository;
        this.screenResultRepository = screenResultRepository;
        this.partyRepository = partyRepository;
        this.associatedPersonRepository = associatedPersonRepository;
        this.fuzzyThreshold = fuzzyThreshold;
    }

    public void assertClearForAccountOpen(Party party) {
        if (party.getSanctionsStatus() == ScreeningStatus.HIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Cannot open DFS account: AML/CFT screening HIT. Backoffice must CLEAR with a reason, or reject.");
        }
        if (party.getSanctionsStatus() != ScreeningStatus.CLEAR) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Cannot open DFS account: sanctions status is "
                            + party.getSanctionsStatus()
                            + ". Run screening and CLEAR before approve.");
        }
    }

    /**
     * Screen party + associated persons. Skips overwrite when admin already CLEARed, unless force.
     */
    @Transactional
    public Party screen(Long partyId, boolean force) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (!force && party.isSanctionsManualClear() && party.getSanctionsStatus() == ScreeningStatus.CLEAR) {
            return party;
        }

        List<AmlWatchlistEntry> list = watchlistRepository.findByActiveTrue();
        List<Subject> subjects = subjects(party);
        ScreeningStatus worst = ScreeningStatus.CLEAR;
        StringBuilder notes = new StringBuilder();

        for (Subject s : subjects) {
            Hit hit = bestHit(s, list);
            AmlScreenResult row = new AmlScreenResult();
            row.setPartyId(party.getId());
            row.setSubjectType(s.type);
            row.setSubjectName(s.name);
            row.setSubjectCnic(s.cnic);
            row.setScreenedAt(Instant.now());
            if (hit == null) {
                row.setOutcome(ScreeningStatus.CLEAR.name());
                row.setMatchType("NONE");
                row.setDetail("No CNIC or name match");
            } else {
                row.setOutcome(hit.outcome.name());
                row.setMatchType(hit.matchType);
                row.setMatchScore(hit.score);
                row.setWatchlistId(hit.entry.getId());
                row.setDetail(hit.entry.getListSource() + " / " + hit.entry.getFullName()
                        + (hit.entry.getCnic() != null ? " CNIC " + hit.entry.getCnic() : ""));
                if (hit.outcome == ScreeningStatus.HIT) {
                    worst = ScreeningStatus.HIT;
                } else if (hit.outcome == ScreeningStatus.MANUAL_REVIEW && worst != ScreeningStatus.HIT) {
                    worst = ScreeningStatus.MANUAL_REVIEW;
                }
                if (notes.length() < 800) {
                    notes.append(s.type).append(' ').append(s.name).append(": ")
                            .append(hit.outcome).append(' ').append(hit.matchType)
                            .append(" vs ").append(hit.entry.getListSource())
                            .append(' ').append(hit.entry.getFullName()).append("; ");
                }
            }
            screenResultRepository.save(row);

            if ("PERSON".equals(s.type) && s.person != null) {
                s.person.setSanctionsStatus(hit == null ? ScreeningStatus.CLEAR : hit.outcome);
                associatedPersonRepository.save(s.person);
            }
        }

        party.setSanctionsStatus(worst);
        party.setSanctionsScreenedAt(Instant.now());
        party.setSanctionsManualClear(false);
        party.setSanctionsNotes(notes.isEmpty()
                ? "Screened against local AML watchlist — no match"
                : notes.toString());
        return partyRepository.save(party);
    }

    @Transactional
    public int importCsv(String csv, String sourceVersion) {
        if (csv == null || csv.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV is empty");
        }
        String[] lines = csv.split("\\R");
        int start = 0;
        if (lines.length > 0 && lines[0].toLowerCase(Locale.ROOT).contains("full_name")) {
            start = 1;
        }
        int n = 0;
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] cols = line.split(",", -1);
            if (cols.length < 2) continue;
            AmlListSource source = parseSource(cols[0]);
            String name = cols.length > 2 ? cols[2].trim() : "";
            // columns: source, list_name, full_name, cnic, notes
            String listName = cols.length > 1 ? blank(cols[1]) : null;
            if (cols.length >= 3 && !cols[2].isBlank()) {
                name = cols[2].trim();
            } else if (cols.length == 2) {
                name = cols[1].trim();
                listName = null;
            }
            if (name.isBlank()) continue;
            String cnic = cols.length > 3 ? IdentityFormats.cnicDigits(cols[3]) : null;
            String notes = cols.length > 4 ? blank(cols[4]) : null;

            AmlWatchlistEntry e = new AmlWatchlistEntry();
            e.setListSource(source);
            e.setListName(listName);
            e.setFullName(name);
            e.setCnic(cnic);
            e.setNotes(notes);
            e.setActive(true);
            e.setSourceVersion(sourceVersion != null ? sourceVersion : "csv-import");
            e.setImportedAt(Instant.now());
            watchlistRepository.save(e);
            n++;
        }
        return n;
    }

    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activeEntries", watchlistRepository.countByActiveTrue());
        out.put("fuzzyThreshold", fuzzyThreshold);
        out.put("sources", List.of("UN", "OFAC", "NACTA", "INTERNAL"));
        out.put("rules", "CNIC exact → HIT; name similarity >= threshold → MANUAL_REVIEW; else CLEAR");
        return out;
    }

    public List<AmlWatchlistEntry> activeEntries() {
        return watchlistRepository.findByActiveTrue();
    }

    public List<AmlScreenResult> recentForParty(Long partyId) {
        return screenResultRepository.findTop20ByPartyIdOrderByScreenedAtDesc(partyId);
    }

    private List<Subject> subjects(Party party) {
        List<Subject> out = new ArrayList<>();
        String partyName = party.getBusinessName() != null && !party.getBusinessName().isBlank()
                ? party.getBusinessName() : party.getFullName();
        out.add(new Subject("PARTY", partyName, IdentityFormats.cnicDigits(party.getCnicNumber()), null));
        if (party.getFullName() != null && partyName != null
                && !normalize(party.getFullName()).equals(normalize(partyName))) {
            out.add(new Subject("PARTY_PERSON", party.getFullName(),
                    IdentityFormats.cnicDigits(party.getCnicNumber()), null));
        }
        for (AssociatedPerson p : associatedPersonRepository.findByPartyIdOrderByIdAsc(party.getId())) {
            out.add(new Subject("PERSON", p.getFullName(),
                    IdentityFormats.cnicDigits(p.getIdDocumentNumber()), p));
        }
        return out;
    }

    private Hit bestHit(Subject subject, List<AmlWatchlistEntry> list) {
        Hit best = null;
        for (AmlWatchlistEntry e : list) {
            if (subject.cnic != null && e.getCnic() != null && subject.cnic.equals(e.getCnic())) {
                return new Hit(ScreeningStatus.HIT, "CNIC", 100, e);
            }
            int score = similarity(subject.name, e.getFullName());
            if (score >= fuzzyThreshold) {
                if (best == null || score > best.score) {
                    best = new Hit(ScreeningStatus.MANUAL_REVIEW, "NAME", score, e);
                }
            }
        }
        return best;
    }

    static int similarity(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return 0;
        if (na.equals(nb)) return 100;
        if (na.contains(nb) || nb.contains(na)) {
            int shorter = Math.min(na.length(), nb.length());
            int longer = Math.max(na.length(), nb.length());
            return (int) Math.round(100.0 * shorter / longer);
        }
        int dist = levenshtein(na, nb);
        int max = Math.max(na.length(), nb.length());
        return (int) Math.round(100.0 * (max - dist) / max);
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.length()];
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static AmlListSource parseSource(String raw) {
        if (raw == null || raw.isBlank()) return AmlListSource.INTERNAL;
        try {
            return AmlListSource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return AmlListSource.INTERNAL;
        }
    }

    private static String blank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private record Subject(String type, String name, String cnic, AssociatedPerson person) {}

    private record Hit(ScreeningStatus outcome, String matchType, int score, AmlWatchlistEntry entry) {}
}
