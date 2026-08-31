-- DFS core banking account provisioning (filled after admin approve / KYC complete)
ALTER TABLE parties ADD COLUMN account_provision_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED';
ALTER TABLE parties ADD COLUMN dfs_account_id VARCHAR(100);
ALTER TABLE parties ADD COLUMN account_provision_error VARCHAR(1000);
ALTER TABLE parties ADD COLUMN account_provisioned_at TIMESTAMP NULL;
ALTER TABLE parties ADD COLUMN account_provision_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE parties ADD COLUMN account_provision_last_attempt_at TIMESTAMP NULL;

CREATE INDEX idx_parties_account_provision ON parties (account_provision_status);
