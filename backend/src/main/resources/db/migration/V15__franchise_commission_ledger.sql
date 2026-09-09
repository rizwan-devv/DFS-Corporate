-- Real-time franchise commission ledger + party wallet balances

CREATE TABLE IF NOT EXISTS party_wallet_balances (
    id                  BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    party_id            BIGINT       NOT NULL,
    currency            VARCHAR(8)   NOT NULL DEFAULT 'PKR',
    available_balance   DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    commission_earned   DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pwb_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT uk_pwb_party_ccy UNIQUE (party_id, currency)
);

CREATE TABLE IF NOT EXISTS franchise_commission_entries (
    id                      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id               VARCHAR(36)  NOT NULL,
    parent_party_id         BIGINT       NOT NULL,
    child_party_id          BIGINT       NOT NULL,
    plan_id                 BIGINT NULL,
    external_txn_ref        VARCHAR(120) NOT NULL,
    txn_type                VARCHAR(40)  NOT NULL DEFAULT 'INCOMING',
    currency                VARCHAR(8)   NOT NULL DEFAULT 'PKR',
    gross_amount            DECIMAL(18,2) NOT NULL,
    rate_percent            DECIMAL(8,4)  NOT NULL DEFAULT 0.0000,
    commission_amount       DECIMAL(18,2) NOT NULL,
    child_net_amount        DECIMAL(18,2) NOT NULL,
    status                  VARCHAR(32)  NOT NULL,
    source                  VARCHAR(40)  NOT NULL DEFAULT 'API',
    notes                   VARCHAR(500),
    posted_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_at             TIMESTAMP NULL,
    reverse_of_id           BIGINT NULL,
    CONSTRAINT fk_fce_parent FOREIGN KEY (parent_party_id) REFERENCES parties(id),
    CONSTRAINT fk_fce_child FOREIGN KEY (child_party_id) REFERENCES parties(id),
    CONSTRAINT fk_fce_plan FOREIGN KEY (plan_id) REFERENCES franchise_commission_plans(id),
    CONSTRAINT fk_fce_reverse_of FOREIGN KEY (reverse_of_id) REFERENCES franchise_commission_entries(id),
    CONSTRAINT uk_fce_public UNIQUE (public_id),
    CONSTRAINT uk_fce_ext_ref UNIQUE (external_txn_ref)
);

CREATE INDEX idx_fce_parent_posted ON franchise_commission_entries (parent_party_id, posted_at);
CREATE INDEX idx_fce_child_posted ON franchise_commission_entries (child_party_id, posted_at);
CREATE INDEX idx_fce_status ON franchise_commission_entries (status);
