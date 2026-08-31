package com.dfs.corporate.service;

import com.dfs.corporate.domain.CorporateEntityType;
import com.dfs.corporate.domain.PartyType;
import com.dfs.corporate.domain.RiskRating;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Annex-C mandatory docs for Sole Prop, Small Business, Partnership, LLP only.
 */
public final class ConsolidatedKycRules {

    private ConsolidatedKycRules() {}

    public static Set<String> mandatoryDocuments(PartyType partyType,
                                                 CorporateEntityType entityType,
                                                 boolean partnershipUnregistered) {
        Set<String> codes = new LinkedHashSet<>();

        if (partyType == PartyType.SUB_MERCHANT) {
            codes.add("AUTH_ID_FRONT");
            codes.add("AUTH_ID_BACK");
            codes.add("AUTH_LIVE_PHOTO");
            codes.add("PARENT_AUTH");
            return codes;
        }
        if (entityType == null) return codes;

        switch (entityType) {
            case SOLE_PROPRIETORSHIP, SMALL_BUSINESS -> {
                codes.add("AUTH_ID_FRONT");
                codes.add("AUTH_ID_BACK");
                codes.add("AUTH_LIVE_PHOTO");
            }
            case PARTNERSHIP -> {
                codes.add("PARTNERSHIP_DEED");
                codes.add("PARTNERSHIP_AUTHORITY");
                if (!partnershipUnregistered) {
                    codes.add("PARTNERSHIP_REG_CERT");
                }
            }
            case LLP -> {
                codes.add("LLP_DEED");
                codes.add("LLP_SECP_CERT");
                codes.add("LLP_AUTHORITY");
            }
        }
        return codes;
    }

    public static Set<String> solePropAlternatives() {
        return Set.of("NTN_OR_TAX", "TRADE_BODY_MEMBERSHIP", "SOLE_LETTERHEAD_DECL", "SOLE_ACCOUNT_REQ");
    }

    public static Set<String> smallBusinessAlternatives() {
        return Set.of("REGISTRATION_CERT", "NTN_OR_TAX", "TRADE_BODY_MEMBERSHIP", "PROOF_OF_FUNDS");
    }

    public static boolean needsPartnerInvites(CorporateEntityType type) {
        // Deprecated: portal partner KYC links replaced by mobile app invites
        return false;
    }

    public static boolean needsPartnerRoster(CorporateEntityType type) {
        return type == CorporateEntityType.PARTNERSHIP || type == CorporateEntityType.LLP;
    }

    public static String partnerCnicFront(Long personId) {
        return "PARTNER_" + personId + "_CNIC_FRONT";
    }

    public static String partnerCnicBack(Long personId) {
        return "PARTNER_" + personId + "_CNIC_BACK";
    }

    public static String partnerAgreement(Long personId) {
        return "PARTNER_" + personId + "_AGREEMENT";
    }

    public static boolean isPartnerUploadCode(String code) {
        return code != null && code.toUpperCase().startsWith("PARTNER_")
                && (code.toUpperCase().endsWith("_CNIC_FRONT")
                || code.toUpperCase().endsWith("_CNIC_BACK")
                || code.toUpperCase().endsWith("_AGREEMENT")
                || code.toUpperCase().contains("_ID_FRONT")
                || code.toUpperCase().contains("_ID_BACK")
                || code.toUpperCase().contains("_PHOTO"));
    }

    public static boolean needsEdd(RiskRating rating, boolean flag) {
        return flag || rating == RiskRating.HIGH;
    }

    public static String partnerDocFront(Long personId) {
        return "PARTNER_" + personId + "_ID_FRONT";
    }

    public static String partnerDocBack(Long personId) {
        return "PARTNER_" + personId + "_ID_BACK";
    }

    public static String partnerDocPhoto(Long personId) {
        return "PARTNER_" + personId + "_PHOTO";
    }
}
