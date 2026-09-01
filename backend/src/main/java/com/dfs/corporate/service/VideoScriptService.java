package com.dfs.corporate.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Four read-aloud scripts (~10 seconds). Server picks one at random per challenge.
 */
@Service
public class VideoScriptService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final List<Map.Entry<String, String>> TEMPLATES = List.of(
            Map.entry("V01",
                    "My name is {fullName}. Today is {date}. I am opening a corporate account for {businessName}."),
            Map.entry("V02",
                    "I, {fullName}, confirm that I am the authorized person for {businessName} on {date}."),
            Map.entry("V03",
                    "This is {fullName}. I am completing video verification for {businessName} today, {date}."),
            Map.entry("V04",
                    "Please note: my verification code is {code}. I am {fullName} for {businessName} on {date}.")
    );

    public record ScriptPick(String templateId, String scriptText) {}

    /** Two UI lines: first sentence, then remainder (~10 sec read-aloud). */
    public List<String> toDisplayLines(String scriptText) {
        if (scriptText == null || scriptText.isBlank()) {
            return List.of("", "");
        }
        String trimmed = scriptText.trim();
        int split = trimmed.indexOf(". ");
        if (split > 0 && split < trimmed.length() - 2) {
            return List.of(
                    trimmed.substring(0, split + 1).trim(),
                    trimmed.substring(split + 2).trim());
        }
        return List.of(trimmed, "");
    }

    public ScriptPick pickRandom(String fullName, String businessName) {
        Map.Entry<String, String> template = TEMPLATES.get(
                ThreadLocalRandom.current().nextInt(TEMPLATES.size()));
        String text = template.getValue()
                .replace("{fullName}", safe(fullName))
                .replace("{businessName}", safe(businessName))
                .replace("{date}", LocalDate.now().format(DATE_FMT))
                .replace("{code}", String.valueOf(1000 + ThreadLocalRandom.current().nextInt(9000)));
        return new ScriptPick(template.getKey(), text);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "the applicant" : value.trim();
    }
}
