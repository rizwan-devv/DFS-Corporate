package com.dfs.corporate.integration.dfs;

public record DfsAccountCreateResult(
        boolean success,
        String dfsAccountId,
        String errorMessage,
        boolean deferred
) {
    public static DfsAccountCreateResult ok(String dfsAccountId) {
        return new DfsAccountCreateResult(true, dfsAccountId, null, false);
    }

    public static DfsAccountCreateResult failed(String errorMessage) {
        return new DfsAccountCreateResult(false, null, errorMessage, false);
    }

    /** API not configured yet — leave status PENDING for later integration */
    public static DfsAccountCreateResult deferred(String message) {
        return new DfsAccountCreateResult(false, null, message, true);
    }
}
