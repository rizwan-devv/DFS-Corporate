-- Saved beneficiaries for portal mock transfers (FT / IBFT / Raast)

CREATE TABLE beneficiaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    party_id BIGINT NOT NULL,
    alias_name VARCHAR(120) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    account_number VARCHAR(64),
    bank_name VARCHAR(200),
    raast_id VARCHAR(64),
    mobile VARCHAR(40),
    cnic VARCHAR(40),
    rail_scope VARCHAR(16) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    notes VARCHAR(500),
    created_by VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_beneficiary_public UNIQUE (public_id),
    CONSTRAINT fk_beneficiary_party FOREIGN KEY (party_id) REFERENCES parties(id)
);

CREATE INDEX idx_beneficiary_party ON beneficiaries(party_id);
CREATE INDEX idx_beneficiary_party_active ON beneficiaries(party_id, active);
