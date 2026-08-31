package com.dfs.corporate.domain;

/**
 * Lifecycle of creating the real DFS / core-banking account after portal approve.
 * Wire {@code dfs.account-api.enabled=true} when the external API is ready.
 */
public enum AccountProvisionStatus {
    /** Not approved yet, or provisioning not kicked off */
    NOT_STARTED,
    /** Approve done; waiting for / calling DFS Account API */
    PENDING,
    /** External account created */
    SUCCESS,
    /** Last attempt failed; admin can retry */
    FAILED
}
