CREATE TABLE parties (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(36)  NOT NULL UNIQUE,
    party_type      VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    business_name   VARCHAR(200),
    email           VARCHAR(200) NOT NULL UNIQUE,
    phone           VARCHAR(40)  NOT NULL,
    address_line    VARCHAR(500),
    city            VARCHAR(100),
    country         VARCHAR(100),
    ntn_number      VARCHAR(50),
    parent_party_id BIGINT,
    rejection_reason VARCHAR(1000),
    approved_at     TIMESTAMP,
    approved_by     VARCHAR(200),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(36)  NOT NULL UNIQUE,
    party_id        BIGINT       NOT NULL UNIQUE,
    email           VARCHAR(200) NOT NULL UNIQUE,
    password_hash   VARCHAR(255),
    role            VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    is_first_login  BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_accounts_party FOREIGN KEY (party_id) REFERENCES parties(id)
);

CREATE TABLE otp_codes (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(200) NOT NULL,
    code        VARCHAR(10)  NOT NULL,
    purpose     VARCHAR(40)  NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    consumed    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE required_documents (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    party_type      VARCHAR(32)  NOT NULL,
    document_code   VARCHAR(64)  NOT NULL,
    document_label  VARCHAR(200) NOT NULL,
    mandatory       BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (party_type, document_code)
);

CREATE TABLE party_documents (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    party_id        BIGINT       NOT NULL,
    document_code   VARCHAR(64)  NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    stored_path     VARCHAR(500) NOT NULL,
    content_type    VARCHAR(120),
    status          VARCHAR(32)  NOT NULL,
    review_note     VARCHAR(500),
    uploaded_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_docs_party FOREIGN KEY (party_id) REFERENCES parties(id),
    UNIQUE (party_id, document_code)
);

-- Seed required documents per party type
INSERT INTO required_documents (party_type, document_code, document_label, mandatory) VALUES
('MERCHANT', 'CNIC', 'CNIC (front & back)', TRUE),
('MERCHANT', 'NTN', 'NTN Certificate', TRUE),
('MERCHANT', 'BANK_LETTER', 'Bank Account Maintenance Letter', TRUE),
('MERCHANT', 'BUSINESS_PROOF', 'Business Registration / Shop Proof', TRUE),

('AGENT', 'CNIC', 'CNIC (front & back)', TRUE),
('AGENT', 'AGREEMENT', 'Agent Agreement', TRUE),
('AGENT', 'PHOTO', 'Passport-size Photograph', TRUE),

('CUSTOMER', 'CNIC', 'CNIC (front & back)', TRUE),
('CUSTOMER', 'PROOF_OF_ADDRESS', 'Proof of Address', TRUE),

('SUB_MERCHANT', 'CNIC', 'CNIC (front & back)', TRUE),
('SUB_MERCHANT', 'NTN', 'NTN Certificate', FALSE),
('SUB_MERCHANT', 'PARENT_AUTH', 'Parent Merchant Authorization Letter', TRUE),
('SUB_MERCHANT', 'BANK_LETTER', 'Bank Account Maintenance Letter', TRUE);

-- Default platform admin (password set on first boot by DataInitializer)
CREATE TABLE IF NOT EXISTS schema_meta (
    k VARCHAR(64) PRIMARY KEY,
    v VARCHAR(255)
);
