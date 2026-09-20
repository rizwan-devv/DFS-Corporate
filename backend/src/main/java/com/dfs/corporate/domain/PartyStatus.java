package com.dfs.corporate.domain;

public enum PartyStatus {
    DRAFT,
    SUBMITTED,
    /** System-set: required docs missing or at least one document rejected (re-upload needed). */
    INCOMPLETE,
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED,
    SUSPENDED
}
