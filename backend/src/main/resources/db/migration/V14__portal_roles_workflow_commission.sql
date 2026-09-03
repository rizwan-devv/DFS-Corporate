-- Portal workflow roles, approval engine, franchise commission

-- Allow multiple portal accounts per corporate party
ALTER TABLE accounts DROP INDEX party_id;
CREATE INDEX idx_accounts_party ON accounts (party_id);

CREATE TABLE IF NOT EXISTS account_portal_roles (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_id   BIGINT       NOT NULL,
    portal_role  VARCHAR(32)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_apr_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    UNIQUE KEY uk_account_portal_role (account_id, portal_role)
);

CREATE INDEX idx_apr_account ON account_portal_roles (account_id);

-- Seed: existing party users on ACTIVE merchants get PARTY_ADMIN + all workflow roles
INSERT INTO account_portal_roles (account_id, portal_role)
SELECT a.id, r.portal_role
FROM accounts a
JOIN parties p ON p.id = a.party_id
CROSS JOIN (
    SELECT 'PARTY_ADMIN' AS portal_role UNION ALL
    SELECT 'MAKER' UNION ALL
    SELECT 'CHECKER' UNION ALL
    SELECT 'APPROVER' UNION ALL
    SELECT 'RELEASER'
) r
WHERE a.role = 'PARTY_USER'
  AND p.party_type = 'MERCHANT'
  AND NOT EXISTS (
      SELECT 1 FROM account_portal_roles x
      WHERE x.account_id = a.id AND x.portal_role = r.portal_role
  );

-- Approval workflow (generic — payments/bulk will attach later)
CREATE TABLE IF NOT EXISTS approval_requests (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(36)  NOT NULL UNIQUE,
    party_id        BIGINT       NOT NULL,
    request_type    VARCHAR(40)  NOT NULL,
    reference_key   VARCHAR(120),
    title           VARCHAR(300) NOT NULL,
    payload_json    TEXT,
    status          VARCHAR(32)  NOT NULL,
    current_step    VARCHAR(32)  NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP NULL,
    CONSTRAINT fk_ar_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT fk_ar_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_ar_party_status ON approval_requests (party_id, status);
CREATE INDEX idx_ar_step ON approval_requests (party_id, current_step, status);

CREATE TABLE IF NOT EXISTS approval_actions (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id      BIGINT       NOT NULL,
    step            VARCHAR(32)  NOT NULL,
    decision        VARCHAR(32)  NOT NULL,
    actor_account_id BIGINT      NOT NULL,
    actor_email     VARCHAR(200) NOT NULL,
    comment_text    VARCHAR(1000),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aa_request FOREIGN KEY (request_id) REFERENCES approval_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_aa_actor FOREIGN KEY (actor_account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_aa_request ON approval_actions (request_id);

-- Proposed commission on franchise invite
ALTER TABLE franchise_invites
    ADD COLUMN commission_rate_percent DECIMAL(8,4) NULL,
    ADD COLUMN commission_type VARCHAR(40) NULL,
    ADD COLUMN commission_notes VARCHAR(500) NULL;

-- Locked commission plan on franchise (child party)
CREATE TABLE IF NOT EXISTS franchise_commission_plans (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_party_id     BIGINT       NOT NULL,
    child_party_id      BIGINT       NOT NULL,
    invite_id           BIGINT NULL,
    commission_rate_percent DECIMAL(8,4) NOT NULL,
    commission_type     VARCHAR(40)  NOT NULL DEFAULT 'PERCENT_GROSS',
    notes               VARCHAR(500),
    status              VARCHAR(32)  NOT NULL,
    proposed_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at           TIMESTAMP NULL,
    locked_by_account_id BIGINT NULL,
    locked_by_source    VARCHAR(40) NULL,
    version_no          INT          NOT NULL DEFAULT 1,
    CONSTRAINT fk_fcp_parent FOREIGN KEY (parent_party_id) REFERENCES parties(id),
    CONSTRAINT fk_fcp_child FOREIGN KEY (child_party_id) REFERENCES parties(id),
    CONSTRAINT fk_fcp_invite FOREIGN KEY (invite_id) REFERENCES franchise_invites(id),
    CONSTRAINT fk_fcp_locker FOREIGN KEY (locked_by_account_id) REFERENCES accounts(id)
);

CREATE UNIQUE INDEX uk_fcp_child ON franchise_commission_plans (child_party_id);
CREATE INDEX idx_fcp_parent ON franchise_commission_plans (parent_party_id);
