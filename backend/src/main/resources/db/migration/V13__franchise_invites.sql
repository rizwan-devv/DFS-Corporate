-- Franchise / child-wallet invites (parent pre-bound via secure token)

CREATE TABLE IF NOT EXISTS franchise_invites (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_token         VARCHAR(64)  NOT NULL UNIQUE,
    parent_party_id      BIGINT       NOT NULL,
    email                VARCHAR(200) NOT NULL,
    phone                VARCHAR(40)  NOT NULL,
    contact_name         VARCHAR(200) NOT NULL,
    business_name        VARCHAR(200),
    entity_type          VARCHAR(40),
    status               VARCHAR(32)  NOT NULL,
    child_party_id       BIGINT NULL,
    invited_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at         TIMESTAMP NULL,
    expires_at           TIMESTAMP    NOT NULL,
    CONSTRAINT fk_franchise_invite_parent FOREIGN KEY (parent_party_id) REFERENCES parties(id),
    CONSTRAINT fk_franchise_invite_child FOREIGN KEY (child_party_id) REFERENCES parties(id)
);

CREATE INDEX idx_franchise_invites_parent ON franchise_invites(parent_party_id);
CREATE INDEX idx_franchise_invites_token ON franchise_invites(public_token);

-- Lighter doc pack label for franchise (SUB_MERCHANT) — PARENT_AUTH stays mandatory
UPDATE required_documents
SET document_label = 'Parent corporate authorization letter'
WHERE party_type = 'SUB_MERCHANT' AND document_code = 'PARENT_AUTH';
