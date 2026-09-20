package com.dfs.corporate.domain;

public enum PartnerAppKycStatus {
    INVITED,
    KYC_IN_PROGRESS,
    KYC_COMPLETED,
    FAILED,
    /** After 3 phone KYC failures — partner must visit bank; backoffice may manual-approve. */
    BANK_VISIT_REQUIRED
}
