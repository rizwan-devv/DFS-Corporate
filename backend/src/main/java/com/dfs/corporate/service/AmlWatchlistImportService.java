package com.dfs.corporate.service;

import com.dfs.corporate.domain.AmlListSource;
import com.dfs.corporate.domain.AmlWatchlistEntry;
import com.dfs.corporate.repository.AmlWatchlistRepository;
import com.dfs.corporate.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads official OFAC SDN (+ ALT) CSV and optional UN consolidated XML — no HTML scraping.
 * Replaces prior OFAC/UN rows; keeps INTERNAL / NACTA demo rows.
 */
@Service
public class AmlWatchlistImportService {

    private static final Logger log = LoggerFactory.getLogger(AmlWatchlistImportService.class);
    private static final Pattern UN_INDIVIDUAL = Pattern.compile(
            "<INDIVIDUAL>(.*?)</INDIVIDUAL>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern UN_ENTITY = Pattern.compile(
            "<ENTITY>(.*?)</ENTITY>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern UN_TAG = Pattern.compile(
            "<(FIRST_NAME|SECOND_NAME|THIRD_NAME|FOURTH_NAME|NAME_ORIGINAL_SCRIPT)>([^<]*)</\\1>",
            Pattern.CASE_INSENSITIVE);

    private final AmlWatchlistRepository watchlistRepository;
    private final TransactionTemplate transactionTemplate;
    private final boolean ofacEnabled;
    private final boolean unEnabled;
    private final String ofacSdnUrl;
    private final String ofacAltUrl;
    private final String unXmlUrl;
    private final int maxNames;
    private final RestClient restClient;

    public AmlWatchlistImportService(
            AmlWatchlistRepository watchlistRepository,
            PlatformTransactionManager transactionManager,
            @Value("${aml.import.ofac-enabled:true}") boolean ofacEnabled,
            @Value("${aml.import.un-enabled:true}") boolean unEnabled,
            @Value("${aml.import.ofac-sdn-url:https://sanctionslistservice.ofac.treas.gov/api/download/SDN.CSV}") String ofacSdnUrl,
            @Value("${aml.import.ofac-alt-url:https://sanctionslistservice.ofac.treas.gov/api/download/ALT.CSV}") String ofacAltUrl,
            @Value("${aml.import.un-xml-url:https://scsanctions.un.org/resources/xml/en/consolidated.xml}") String unXmlUrl,
            @Value("${aml.import.max-names:50000}") int maxNames) {
        this.watchlistRepository = watchlistRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.ofacEnabled = ofacEnabled;
        this.unEnabled = unEnabled;
        this.ofacSdnUrl = ofacSdnUrl;
        this.ofacAltUrl = ofacAltUrl;
        this.unXmlUrl = unXmlUrl;
        this.maxNames = maxNames;
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMinutes(5));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "DFS-Corporate-Portal/1.0 (AML list import; compliance)")
                .defaultHeader("Accept", "*/*")
                .build();
    }

    /** Download outside TX, then replace OFAC/UN rows in one transaction. */
    public Map<String, Object> refreshOfficialLists() {
        Instant started = Instant.now();
        String version = "import-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(started);

        List<String> errors = new ArrayList<>();
        byte[] sdn = null;
        byte[] alt = null;
        byte[] unXml = null;

        if (ofacEnabled) {
            try {
                sdn = download(ofacSdnUrl);
            } catch (Exception e) {
                log.warn("OFAC SDN download failed: {}", e.getMessage());
                errors.add("OFAC SDN: " + e.getMessage());
            }
            try {
                alt = download(ofacAltUrl);
            } catch (Exception e) {
                log.warn("OFAC ALT download failed: {}", e.getMessage());
                errors.add("OFAC ALT: " + e.getMessage());
            }
        }

        if (unEnabled) {
            try {
                unXml = download(unXmlUrl);
            } catch (Exception e) {
                log.warn("UN XML download failed: {}", e.getMessage());
                errors.add("UN XML: " + e.getMessage());
            }
        }

        if (sdn == null && alt == null && unXml == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "AML list refresh failed — no files downloaded: " + String.join("; ", errors));
        }

        final byte[] sdnFinal = sdn;
        final byte[] altFinal = alt;
        final byte[] unFinal = unXml;
        Map<String, Object> counts = transactionTemplate.execute(status ->
                persistDownloads(version, sdnFinal, altFinal, unFinal));
        if (counts == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AML persist returned null");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceVersion", version);
        out.putAll(counts);
        out.put("errors", errors);
        out.put("elapsedMs", Duration.between(started, Instant.now()).toMillis());
        out.put("message", errors.isEmpty()
                ? "Official lists refreshed (INTERNAL/NACTA demo rows kept)"
                : "Refresh finished with partial errors — see errors[]");
        log.info("AML watchlist refresh done: {}", out);
        return out;
    }

    private Map<String, Object> persistDownloads(String version, byte[] sdn, byte[] alt, byte[] unXml) {
        EnumSet<AmlListSource> toReplace = EnumSet.noneOf(AmlListSource.class);
        if (ofacEnabled && (sdn != null || alt != null)) toReplace.add(AmlListSource.OFAC);
        if (unEnabled && unXml != null) toReplace.add(AmlListSource.UN);

        int deactivated = 0;
        if (!toReplace.isEmpty()) {
            deactivated = watchlistRepository.deactivateByListSourceIn(toReplace);
        }

        int ofacPrimary = 0;
        int ofacAliases = 0;
        int unNames = 0;

        if (sdn != null) {
            ofacPrimary = importOfacSdn(sdn, version);
        }
        if (alt != null) {
            ofacAliases = importOfacAlt(alt, version);
        }
        if (unXml != null) {
            unNames = importUnXml(unXml, version);
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("deactivatedPriorOfacUn", deactivated);
        counts.put("ofacPrimaryNames", ofacPrimary);
        counts.put("ofacAliasNames", ofacAliases);
        counts.put("unNames", unNames);
        counts.put("activeTotal", watchlistRepository.countByActiveTrue());
        counts.put("activeOfac", watchlistRepository.countByActiveTrueAndListSource(AmlListSource.OFAC));
        counts.put("activeUn", watchlistRepository.countByActiveTrueAndListSource(AmlListSource.UN));
        counts.put("activeInternal", watchlistRepository.countByActiveTrueAndListSource(AmlListSource.INTERNAL));
        return counts;
    }

    private byte[] download(String url) {
        log.info("Downloading AML list → {}", url);
        byte[] body = restClient.get()
                .uri(url)
                .retrieve()
                .body(byte[].class);
        if (body == null || body.length == 0) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Empty download from " + url);
        }
        return body;
    }

    private int importOfacSdn(byte[] raw, String version) {
        Set<String> seen = new LinkedHashSet<>();
        List<AmlWatchlistEntry> batch = new ArrayList<>();
        int saved = 0;
        try (BufferedReader br = reader(raw)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = stripCtrlZ(line).trim();
                if (line.isEmpty()) continue;
                List<String> cols = parseCsvLine(line);
                if (cols.size() < 2) continue;
                if ("ent_num".equalsIgnoreCase(cols.get(0)) || "sdn_name".equalsIgnoreCase(cols.get(1))) {
                    continue;
                }
                String name = cols.get(1).trim();
                if (name.isEmpty() || "-0-".equals(name)) continue;
                if (!seen.add(normalizeKey(name))) continue;
                String program = cols.size() > 3 ? blankDash(cols.get(3)) : null;
                String type = cols.size() > 2 ? blankDash(cols.get(2)) : null;
                batch.add(entry(AmlListSource.OFAC, "SDN", name, null,
                        trim("type=" + nullToEmpty(type) + "; program=" + nullToEmpty(program), 500),
                        version));
                if (batch.size() >= 400) {
                    saved += flush(batch);
                }
                if (saved + batch.size() >= maxNames) break;
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "OFAC SDN parse failed: " + e.getMessage());
        }
        saved += flush(batch);
        return saved;
    }

    private int importOfacAlt(byte[] raw, String version) {
        Set<String> seen = new LinkedHashSet<>();
        List<AmlWatchlistEntry> batch = new ArrayList<>();
        int saved = 0;
        try (BufferedReader br = reader(raw)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = stripCtrlZ(line).trim();
                if (line.isEmpty()) continue;
                List<String> cols = parseCsvLine(line);
                if (cols.size() < 4) continue;
                if ("ent_num".equalsIgnoreCase(cols.get(0))) continue;
                String name = cols.get(3).trim();
                if (name.isEmpty() || "-0-".equals(name)) continue;
                if (!seen.add(normalizeKey(name))) continue;
                batch.add(entry(AmlListSource.OFAC, "SDN-ALT", name, null,
                        "OFAC alias", version));
                if (batch.size() >= 400) {
                    saved += flush(batch);
                }
                if (saved + batch.size() >= maxNames) break;
            }
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "OFAC ALT parse failed: " + e.getMessage());
        }
        saved += flush(batch);
        return saved;
    }

    private int importUnXml(byte[] raw, String version) {
        String xml = new String(raw, StandardCharsets.UTF_8);
        Set<String> seen = new LinkedHashSet<>();
        List<AmlWatchlistEntry> batch = new ArrayList<>();
        int saved = 0;

        Matcher ind = UN_INDIVIDUAL.matcher(xml);
        while (ind.find()) {
            String name = unNameFromBlock(ind.group(1));
            if (name == null || !seen.add(normalizeKey(name))) continue;
            batch.add(entry(AmlListSource.UN, "UNSC-INDIVIDUAL", name, null, "UN consolidated individual", version));
            if (batch.size() >= 400) saved += flush(batch);
            if (saved + batch.size() >= maxNames) break;
        }

        Matcher ent = UN_ENTITY.matcher(xml);
        while (ent.find() && saved + batch.size() < maxNames) {
            String name = unNameFromBlock(ent.group(1));
            if (name == null || !seen.add(normalizeKey(name))) continue;
            batch.add(entry(AmlListSource.UN, "UNSC-ENTITY", name, null, "UN consolidated entity", version));
            if (batch.size() >= 400) saved += flush(batch);
        }

        saved += flush(batch);
        return saved;
    }

    private static String unNameFromBlock(String block) {
        Matcher m = UN_TAG.matcher(block);
        List<String> parts = new ArrayList<>();
        while (m.find()) {
            String v = m.group(2).trim();
            if (!v.isEmpty()) parts.add(v);
        }
        if (parts.isEmpty()) return null;
        return String.join(" ", parts).replaceAll("\\s+", " ").trim();
    }

    private int flush(List<AmlWatchlistEntry> batch) {
        if (batch.isEmpty()) return 0;
        watchlistRepository.saveAll(batch);
        int n = batch.size();
        batch.clear();
        return n;
    }

    private static AmlWatchlistEntry entry(AmlListSource source, String listName, String fullName,
                                           String cnic, String notes, String version) {
        AmlWatchlistEntry e = new AmlWatchlistEntry();
        e.setListSource(source);
        e.setListName(listName);
        e.setFullName(fullName.length() > 300 ? fullName.substring(0, 300) : fullName);
        e.setCnic(cnic);
        e.setNotes(notes);
        e.setActive(true);
        e.setImportedAt(Instant.now());
        e.setSourceVersion(version);
        return e;
    }

    private static BufferedReader reader(byte[] raw) {
        Charset cs = Charset.forName("windows-1252");
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(raw), cs));
    }

    private static String stripCtrlZ(String line) {
        if (line == null) return "";
        int i = line.indexOf('\u001a');
        return i >= 0 ? line.substring(0, i) : line;
    }

    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String normalizeKey(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String blankDash(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || "-0-".equals(t)) return null;
        return t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
