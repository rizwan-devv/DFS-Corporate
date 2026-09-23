-- Inbound franchise commission: % of child credits transferred to parent wallet.

CREATE TABLE IF NOT EXISTS franchise_commission_entries (
    id                      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id               VARCHAR(36)   NOT NULL,
    parent_party_id         BIGINT        NOT NULL,
    child_party_id          BIGINT        NOT NULL,
    plan_id                 BIGINT        NULL,
    source_ref              VARCHAR(190)  NOT NULL,
    inbound_ref             VARCHAR(120),
    inbound_at              VARCHAR(64),
    currency                VARCHAR(8)    NOT NULL DEFAULT 'PKR',
    gross_amount            DECIMAL(18,2) NOT NULL,
    rate_percent            DECIMAL(8,4)  NOT NULL,
    commission_amount       DECIMAL(18,2) NOT NULL,
    status                  VARCHAR(32)   NOT NULL,
    dfs_auth_id             VARCHAR(80),
    error_message           VARCHAR(500),
    posted_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at              TIMESTAMP     NULL,
    CONSTRAINT fk_fce2_parent FOREIGN KEY (parent_party_id) REFERENCES parties(id),
    CONSTRAINT fk_fce2_child FOREIGN KEY (child_party_id) REFERENCES parties(id),
    CONSTRAINT fk_fce2_plan FOREIGN KEY (plan_id) REFERENCES franchise_commission_plans(id),
    CONSTRAINT uk_fce2_public UNIQUE (public_id),
    CONSTRAINT uk_fce2_child_src UNIQUE (child_party_id, source_ref)
);

CREATE INDEX idx_fce2_parent_posted ON franchise_commission_entries (parent_party_id, posted_at);
CREATE INDEX idx_fce2_status ON franchise_commission_entries (status);
