package com.dfs.corporate.integration.dfs;

public record DfsAccountCreateResult(
        boolean success,
        String dfsAccountId,
        String errorMessage,
        boolean deferred,
        String dfsAppUserId,
        String nidNo
) {
    public static DfsAccountCreateResult ok(String dfsAccountId) {
        return ok(dfsAccountId, null, null);
    }

    public static DfsAccountCreateResult ok(String dfsAccountId, String dfsAppUserId, String nidNo) {
        return new DfsAccountCreateResult(true, dfsAccountId, null, false, dfsAppUserId, nidNo);
    }

    public static DfsAccountCreateResult failed(String errorMessage) {
        return new DfsAccountCreateResult(false, null, errorMessage, false, null, null);
    }

    /** API not configured yet — leave status PENDING for later integration */
    public static DfsAccountCreateResult deferred(String message) {
        return new DfsAccountCreateResult(false, null, message, true, null, null);
    }
}
