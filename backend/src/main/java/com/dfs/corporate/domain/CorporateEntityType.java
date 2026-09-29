package com.dfs.corporate.domain;

/**
 * Annex-C entity types (SBP Consolidated Customer Onboarding Framework).
 */
public enum CorporateEntityType {
    SOLE_PROPRIETORSHIP,
    SMALL_BUSINESS,
    PARTNERSHIP,
    LLP,
    /** 5 — Limited companies / corporations */
    LIMITED_COMPANY,
    /** 6 — Branch / liaison office of a foreign company. Shown as Corporate. KYC is back office only. */
    FOREIGN_BRANCH,
    /** 7 — Trusts, clubs, societies and associations */
    TRUST_SOCIETY,
    /** 8 — INGOs / NGOs / NPOs / charities */
    NGO_NPO
}
