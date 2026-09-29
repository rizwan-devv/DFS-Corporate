package com.dfs.corporate.service;

import com.dfs.corporate.domain.CorporateEntityType;
import com.dfs.corporate.domain.DocumentStatus;
import com.dfs.corporate.domain.EntityKycPolicy;
import com.dfs.corporate.domain.Party;
import com.dfs.corporate.domain.PartyDocument;
import com.dfs.corporate.repository.EntityKycPolicyRepository;
import com.dfs.corporate.repository.PartyDocumentRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EntityKycPolicyService {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("SOLE_PROPRIETORSHIP", "Sole Proprietorship");
        LABELS.put("SMALL_BUSINESS", "Small business / freelance profession");
        LABELS.put("PARTNERSHIP", "Partnership");
        LABELS.put("LLP", "Limited Liability Partnership (LLP)");
        LABELS.put("LIMITED_COMPANY", "Limited company / corporation");
        LABELS.put("FOREIGN_BRANCH", "Corporate");
        LABELS.put("TRUST_SOCIETY", "Trust, club, society or association");
        LABELS.put("NGO_NPO", "INGO / NGO / NPO / charity");
    }

    private final EntityKycPolicyRepository policyRepository;
    private final PartyRepository partyRepository;
    private final PartyDocumentRepository documentRepository;
    private final FileStorageService fileStorageService;

    public EntityKycPolicyService(EntityKycPolicyRepository policyRepository,
                                  PartyRepository partyRepository,
                                  PartyDocumentRepository documentRepository,
                                  FileStorageService fileStorageService) {
        this.policyRepository = policyRepository;
        this.partyRepository = partyRepository;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
    }

    /** Missing row or unknown type stays required so a case is not approved with no identity check. */
    public boolean isKycRequired(CorporateEntityType entityType) {
        if (entityType == null) return true;
        if (ConsolidatedKycRules.backOfficeKycOnly(entityType)) return true;
        return policyRepository.findById(entityType.name()).map(EntityKycPolicy::isKycRequired).orElse(true);
    }

    public List<Map<String, Object>> list() {
        Map<String, EntityKycPolicy> saved = new LinkedHashMap<>();
        for (EntityKycPolicy row : policyRepository.findAll()) {
            saved.put(row.getEntityType(), row);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (CorporateEntityType type : CorporateEntityType.values()) {
            EntityKycPolicy row = saved.get(type.name());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entityType", type.name());
            item.put("label", LABELS.getOrDefault(type.name(), type.name()));
            boolean backOfficeOnly = ConsolidatedKycRules.backOfficeKycOnly(type);
            item.put("kycRequired", backOfficeOnly || row == null || row.isKycRequired());
            item.put("backOfficeKycOnly", backOfficeOnly);
            item.put("kycAppEnabled", !backOfficeOnly);
            item.put("updatedAt", row != null ? row.getUpdatedAt() : null);
            item.put("updatedBy", row != null ? row.getUpdatedBy() : null);
            out.add(item);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> setRequired(String entityType, boolean kycRequired, String updatedBy) {
        CorporateEntityType type;
        try {
            type = CorporateEntityType.valueOf(entityType.trim().toUpperCase());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown entity type");
        }
        EntityKycPolicy row = policyRepository.findById(type.name()).orElseGet(EntityKycPolicy::new);
        row.setEntityType(type.name());
        row.setKycRequired(ConsolidatedKycRules.backOfficeKycOnly(type) || kycRequired);
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(updatedBy != null ? updatedBy : "BACKOFFICE");
        policyRepository.save(row);
        return list().stream()
                .filter(m -> type.name().equals(m.get("entityType")))
                .findFirst()
                .orElseThrow();
    }

    public boolean manualPackComplete(Long partyId) {
        Set<String> ok = documentRepository.findByPartyIdOrderByUploadedAtDesc(partyId).stream()
                .filter(d -> d.getStatus() != DocumentStatus.REJECTED)
                .map(PartyDocument::getDocumentCode)
                .collect(Collectors.toSet());
        return ok.containsAll(ConsolidatedKycRules.manualKycCodes());
    }

    @Transactional
    public Party storeManualKyc(Long partyId, String documentCode, MultipartFile file, String actorNote, boolean backOffice) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (ConsolidatedKycRules.backOfficeKycOnly(party.getEntityType()) && !backOffice) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Corporate KYC (CNIC and photo) is completed by back office. The KYC app is not used.");
        }
        if (!isKycRequired(party.getEntityType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "KYC is turned off for this entity type");
        }
        String code = documentCode == null ? "" : documentCode.trim().toUpperCase();
        if (!ConsolidatedKycRules.isManualKycCode(code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Use MANUAL_KYC_ID_FRONT, MANUAL_KYC_ID_BACK, or MANUAL_KYC_PHOTO");
        }
        String path = fileStorageService.store(party.getId(), code, file);
        PartyDocument doc = documentRepository.findByPartyIdAndDocumentCode(party.getId(), code)
                .orElseGet(PartyDocument::new);
        if (doc.getStatus() == DocumentStatus.APPROVED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This KYC file is already approved");
        }
        doc.setPartyId(party.getId());
        doc.setDocumentCode(code);
        doc.setOriginalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : code);
        doc.setStoredPath(path);
        doc.setContentType(file.getContentType());
        doc.setStatus(DocumentStatus.PENDING);
        doc.setReviewNote(actorNote);
        doc.setUploadedAt(Instant.now());
        documentRepository.save(doc);
        return party;
    }
}
