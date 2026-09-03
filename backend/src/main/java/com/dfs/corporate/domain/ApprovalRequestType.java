package com.dfs.corporate.domain;

public enum ApprovalRequestType {
    /** Placeholder / demo workflow item */
    GENERIC,
    /** Future: single transfer / vendor payment */
    PAYMENT,
    /** Future: salary / vendor bulk */
    BULK_PAYMENT,
    /** Commission plan change after lock */
    COMMISSION_CHANGE
}
