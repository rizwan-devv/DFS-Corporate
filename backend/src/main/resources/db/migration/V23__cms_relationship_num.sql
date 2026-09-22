-- CMS card relationship (AgentApp /card/inquiry key). Separate from dfs_account_id.

ALTER TABLE parties ADD COLUMN cms_relationship_num VARCHAR(64) NULL;
ALTER TABLE parties ADD COLUMN cms_relationship_linked_at TIMESTAMP NULL;
ALTER TABLE parties ADD COLUMN cms_relationship_source VARCHAR(32) NULL;
