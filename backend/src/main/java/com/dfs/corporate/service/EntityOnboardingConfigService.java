package com.dfs.corporate.service;

import com.dfs.corporate.domain.CorporateEntityType;
import com.dfs.corporate.domain.EntityOnboardingDocument;
import com.dfs.corporate.domain.PartyType;
import com.dfs.corporate.domain.RequiredDocument;
import com.dfs.corporate.repository.EntityOnboardingDocumentRepository;
import com.dfs.corporate.repository.RequiredDocumentRepository;
import com.dfs.corporate.web.dto.PartyResponse;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Back-office catalog of onboarding documents per entity type.
 * Seeded from Annex-C. Extra documents added here are required on submit.
 */
@Service
public class EntityOnboardingConfigService {

    private final EntityOnboardingDocumentRepository documentRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;

    public EntityOnboardingConfigService(EntityOnboardingDocumentRepository documentRepository,
                                         RequiredDocumentRepository requiredDocumentRepository) {
        this.documentRepository = documentRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
    }

    public boolean hasCatalog(CorporateEntityType entityType) {
        return entityType != null && documentRepository.existsByEntityType(entityType.name());
    }

    public boolean acceptsUpload(CorporateEntityType entityType, String code) {
        if (entityType == null || code == null || code.isBlank()) return false;
        return documentRepository.findByEntityTypeAndDocumentCode(entityType.name(), code.trim().toUpperCase(Locale.ROOT))
                .filter(EntityOnboardingDocument::isActive)
                .isPresent();
    }

    public List<EntityOnboardingDocument> activeRows(CorporateEntityType entityType, boolean partnershipUnregistered) {
        if (entityType == null) return List.of();
        List<EntityOnboardingDocument> rows =
                documentRepository.findByEntityTypeAndActiveTrueOrderBySortOrderAscIdAsc(entityType.name());
        if (entityType == CorporateEntityType.PARTNERSHIP && partnershipUnregistered) {
            return rows.stream().filter(r -> !"PARTNERSHIP_REG_CERT".equals(r.getDocumentCode())).toList();
        }
        return rows;
    }

    public List<PartyResponse.RequiredItem> checklist(CorporateEntityType entityType,
                                                      boolean partnershipUnregistered,
                                                      Set<String> uploaded) {
        List<PartyResponse.RequiredItem> required = new ArrayList<>();
        for (EntityOnboardingDocument row : activeRows(entityType, partnershipUnregistered)) {
            required.add(new PartyResponse.RequiredItem(
                    row.getDocumentCode(),
                    row.getDocumentLabel(),
                    "REQUIRED".equals(row.getRequirement()),
                    uploaded.contains(row.getDocumentCode())));
        }
        return required;
    }

    /** Null when the uploaded set satisfies this entity's catalog. */
    public String submissionGap(CorporateEntityType entityType, boolean partnershipUnregistered, Set<String> uploaded) {
        List<EntityOnboardingDocument> rows = activeRows(entityType, partnershipUnregistered);
        List<String> missing = new ArrayList<>();
        for (EntityOnboardingDocument row : rows) {
            if ("REQUIRED".equals(row.getRequirement()) && !uploaded.contains(row.getDocumentCode())) {
                missing.add(row.getDocumentLabel());
            }
        }
        Map<Integer, List<EntityOnboardingDocument>> groups = new LinkedHashMap<>();
        for (EntityOnboardingDocument row : rows) {
            if (!"ONE_OF".equals(row.getRequirement())) continue;
            groups.computeIfAbsent(row.getOneOfGroup(), k -> new ArrayList<>()).add(row);
        }
        List<String> oneOfMissing = new ArrayList<>();
        for (List<EntityOnboardingDocument> group : groups.values()) {
            boolean any = group.stream().anyMatch(r -> uploaded.contains(r.getDocumentCode()));
            if (!any) {
                oneOfMissing.add(group.stream().map(EntityOnboardingDocument::getDocumentLabel).reduce((a, b) -> a + "; " + b).orElse(""));
            }
        }
        if (missing.isEmpty() && oneOfMissing.isEmpty()) return null;
        StringBuilder msg = new StringBuilder();
        if (!missing.isEmpty()) {
            msg.append("Missing required documents: ").append(String.join(", ", missing));
        }
        if (!oneOfMissing.isEmpty()) {
            if (!msg.isEmpty()) msg.append(". ");
            msg.append("Upload at least one of: ").append(String.join(" | ", oneOfMissing));
        }
        return msg.toString();
    }

    public List<Map<String, Object>> list(String entityType) {
        CorporateEntityType type = parseType(entityType);
        List<Map<String, Object>> out = new ArrayList<>();
        for (EntityOnboardingDocument row : documentRepository.findByEntityTypeAndActiveTrueOrderBySortOrderAscIdAsc(type.name())) {
            out.add(toView(row));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> update(Long id, String label, Boolean mandatory) {
        EntityOnboardingDocument row = documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!row.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document is no longer on the checklist");
        }
        String nextLabel = cleanLabel(label);
        row.setDocumentLabel(nextLabel);
        if (!"ONE_OF".equals(row.getRequirement()) && mandatory != null) {
            row.setMandatory(mandatory);
            row.setRequirement(mandatory ? "REQUIRED" : "OPTIONAL");
        }
        documentRepository.save(row);
        syncUploadCatalog(row);
        return toView(row);
    }

    @Transactional
    public Map<String, Object> add(String entityType, String label, boolean mandatory) {
        CorporateEntityType type = parseType(entityType);
        String nextLabel = cleanLabel(label);
        String code = uniqueCode(type, nextLabel);
        EntityOnboardingDocument existing = documentRepository.findByEntityTypeAndDocumentCode(type.name(), code).orElse(null);
        EntityOnboardingDocument row = existing != null ? existing : new EntityOnboardingDocument();
        row.setEntityType(type.name());
        row.setDocumentCode(code);
        row.setDocumentLabel(nextLabel);
        row.setMandatory(mandatory);
        row.setRequirement(mandatory ? "REQUIRED" : "OPTIONAL");
        row.setOneOfGroup(0);
        row.setActive(true);
        if (existing == null) {
            int next = documentRepository.findFirstByEntityTypeOrderBySortOrderDesc(type.name())
                    .map(r -> r.getSortOrder() + 1)
                    .orElse(1);
            row.setSortOrder(next);
        }
        documentRepository.save(row);
        syncUploadCatalog(row);
        return toView(row);
    }

    @Transactional
    public void remove(Long id) {
        EntityOnboardingDocument row = documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        if (row.getDocumentCode() == null || !row.getDocumentCode().startsWith("CUSTOM_")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only documents added in back office can be removed");
        }
        row.setActive(false);
        documentRepository.save(row);
    }

    private void syncUploadCatalog(EntityOnboardingDocument row) {
        RequiredDocument catalog = requiredDocumentRepository
                .findByPartyTypeAndDocumentCode(PartyType.MERCHANT, row.getDocumentCode())
                .orElseGet(RequiredDocument::new);
        catalog.setPartyType(PartyType.MERCHANT);
        catalog.setDocumentCode(row.getDocumentCode());
        catalog.setDocumentLabel(row.getDocumentLabel());
        catalog.setMandatory("REQUIRED".equals(row.getRequirement()));
        requiredDocumentRepository.save(catalog);
    }

    private String uniqueCode(CorporateEntityType type, String label) {
        String slug = label.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        if (slug.length() > 40) slug = slug.substring(0, 40).replaceAll("_+$", "");
        if (slug.isBlank()) slug = "DOC";
        String code = "CUSTOM_" + slug;
        Set<String> taken = new LinkedHashSet<>();
        documentRepository.findByEntityTypeAndActiveTrueOrderBySortOrderAscIdAsc(type.name())
                .forEach(r -> taken.add(r.getDocumentCode()));
        if (!taken.contains(code)) return code;
        for (int i = 2; i < 50; i++) {
            String candidate = code + "_" + i;
            if (candidate.length() > 64) candidate = candidate.substring(0, 64);
            if (!taken.contains(candidate)) return candidate;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Could not create a document code for that name");
    }

    private static String cleanLabel(String label) {
        String next = label == null ? "" : label.trim();
        if (next.length() < 2 || next.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document name must be 2–200 characters");
        }
        return next;
    }

    private static CorporateEntityType parseType(String entityType) {
        try {
            return CorporateEntityType.valueOf(entityType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown entity type");
        }
    }

    private static Map<String, Object> toView(EntityOnboardingDocument row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("entityType", row.getEntityType());
        m.put("documentCode", row.getDocumentCode());
        m.put("documentLabel", row.getDocumentLabel());
        m.put("mandatory", "REQUIRED".equals(row.getRequirement()));
        m.put("requirement", row.getRequirement());
        m.put("custom", row.getDocumentCode() != null && row.getDocumentCode().startsWith("CUSTOM_"));
        return m;
    }
}
