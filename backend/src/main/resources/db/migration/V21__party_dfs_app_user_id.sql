-- Optional DFS App user id for local FT (fundsTransferLocal requires payer appUserId)

ALTER TABLE parties
    ADD COLUMN dfs_app_user_id VARCHAR(32) NULL AFTER dfs_account_id;
