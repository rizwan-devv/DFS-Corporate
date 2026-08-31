-- SBP Consolidated Customer Onboarding Framework — Entity (Section E) + F/G/I stubs

ALTER TABLE parties ADD COLUMN tracking_id VARCHAR(40);
ALTER TABLE parties ADD COLUMN incorporation_number VARCHAR(80);
ALTER TABLE parties ADD COLUMN incorporation_date DATE;
ALTER TABLE parties ADD COLUMN incorporation_country VARCHAR(100);
ALTER TABLE parties ADD COLUMN incorporation_authority VARCHAR(200);
ALTER TABLE parties ADD COLUMN tax_country VARCHAR(100);
ALTER TABLE parties ADD COLUMN tax_exemption_evidence VARCHAR(500);
ALTER TABLE parties ADD COLUMN fatca_crs_declared BOOLEAN DEFAULT FALSE;
ALTER TABLE parties ADD COLUMN fatca_crs_details VARCHAR(1000);
ALTER TABLE parties ADD COLUMN registered_address VARCHAR(500);
ALTER TABLE parties ADD COLUMN mailing_address VARCHAR(500);
ALTER TABLE parties ADD COLUMN place_of_business VARCHAR(500);
ALTER TABLE parties ADD COLUMN address_difference_reason VARCHAR(500);
ALTER TABLE parties ADD COLUMN nature_of_business VARCHAR(1000);
ALTER TABLE parties ADD COLUMN business_license_details VARCHAR(500);
ALTER TABLE parties ADD COLUMN purpose_of_account VARCHAR(500);
ALTER TABLE parties ADD COLUMN intended_relationship VARCHAR(500);
ALTER TABLE parties ADD COLUMN client_ip VARCHAR(64);
ALTER TABLE parties ADD COLUMN geo_location VARCHAR(200);
ALTER TABLE parties ADD COLUMN user_agent VARCHAR(500);
ALTER TABLE parties ADD COLUMN sanctions_status VARCHAR(32) DEFAULT 'PENDING';
ALTER TABLE parties ADD COLUMN sanctions_screened_at TIMESTAMP NULL;
ALTER TABLE parties ADD COLUMN sanctions_notes VARCHAR(1000);
ALTER TABLE parties ADD COLUMN identity_verification_status VARCHAR(32) DEFAULT 'PENDING';
ALTER TABLE parties ADD COLUMN identity_verification_method VARCHAR(64);
ALTER TABLE parties ADD COLUMN risk_rating VARCHAR(16) DEFAULT 'MEDIUM';
ALTER TABLE parties ADD COLUMN edd_required BOOLEAN DEFAULT FALSE;
ALTER TABLE parties ADD COLUMN edd_notes VARCHAR(1000);
ALTER TABLE parties ADD COLUMN video_kyc_ref VARCHAR(500);
ALTER TABLE parties ADD COLUMN submitted_at TIMESTAMP NULL;
ALTER TABLE parties ADD COLUMN decision_due_at TIMESTAMP NULL;
ALTER TABLE parties ADD COLUMN discrepancy_note VARCHAR(1000);
ALTER TABLE parties ADD COLUMN draft_expires_at TIMESTAMP NULL;
ALTER TABLE parties ADD COLUMN onboarding_step INT DEFAULT 1;

CREATE UNIQUE INDEX idx_parties_tracking ON parties(tracking_id);

CREATE TABLE IF NOT EXISTS associated_persons (
    id                      BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    party_id                BIGINT NOT NULL,
    role_type               VARCHAR(40) NOT NULL,
    full_name               VARCHAR(200) NOT NULL,
    father_or_spouse_name   VARCHAR(200),
    date_of_birth           DATE,
    mother_maiden_name      VARCHAR(200),
    place_of_birth          VARCHAR(120),
    id_document_type        VARCHAR(40),
    id_document_number      VARCHAR(40),
    id_issue_date           DATE,
    id_expiry_date          DATE,
    passport_number         VARCHAR(40),
    passport_country        VARCHAR(80),
    nationalities           VARCHAR(300),
    tax_residencies         VARCHAR(300),
    email                   VARCHAR(200),
    phone                   VARCHAR(40),
    mailing_address         VARCHAR(500),
    occupation              VARCHAR(200),
    ownership_percent       DECIMAL(5,2),
    authorized_to_operate   BOOLEAN DEFAULT FALSE,
    fatca_crs_declared      BOOLEAN DEFAULT FALSE,
    fatca_crs_details       VARCHAR(500),
    sanctions_status        VARCHAR(32) DEFAULT 'PENDING',
    identity_verification_status VARCHAR(32) DEFAULT 'PENDING',
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assoc_party FOREIGN KEY (party_id) REFERENCES parties(id)
);

-- Annex-C document catalog (entity_type scoped via document_code prefixes; mandatory resolved in app)
DELETE FROM required_documents;

INSERT INTO required_documents (party_type, document_code, document_label, mandatory) VALUES
('MERCHANT', 'AUTH_ID_FRONT', 'Authorized signatory valid ID — Front', TRUE),
('MERCHANT', 'AUTH_ID_BACK', 'Authorized signatory valid ID — Back', TRUE),
('MERCHANT', 'AUTH_LIVE_PHOTO', 'Authorized signatory live / digital photograph', TRUE),
('MERCHANT', 'NTN_OR_TAX', 'NTN / Tax registration certificate (if available)', FALSE),
('MERCHANT', 'BANK_LETTER', 'Bank account maintenance letter', FALSE),
('MERCHANT', 'SOLE_LETTERHEAD_DECL', 'Sole proprietorship declaration on business letterhead', FALSE),
('MERCHANT', 'SOLE_ACCOUNT_REQ', 'Account opening requisition on business letterhead', FALSE),
('MERCHANT', 'TRADE_BODY_MEMBERSHIP', 'Trade body membership certificate', FALSE),
('MERCHANT', 'PARTNERSHIP_DEED', 'Attested Partnership Deed (all partners)', FALSE),
('MERCHANT', 'PARTNERSHIP_REG_CERT', 'Registration Certificate with Registrar of Firms', FALSE),
('MERCHANT', 'PARTNERSHIP_AUTHORITY', 'Authority letter signed by all partners', FALSE),
('MERCHANT', 'LLP_DEED', 'LLP Deed / Agreement', FALSE),
('MERCHANT', 'LLP_FORM_III', 'LLP Form-III (partners detail)', FALSE),
('MERCHANT', 'LLP_SECP_CERT', 'LLP Registration Certificate (SECP)', FALSE),
('MERCHANT', 'LLP_FORM_V', 'LLP Form-V (change in partners, if applicable)', FALSE),
('MERCHANT', 'LLP_AUTHORITY', 'Authority letter by all partners to operate account', FALSE),
('MERCHANT', 'BOARD_RESOLUTION', 'Board resolution authorizing account open/operate', FALSE),
('MERCHANT', 'MOA', 'Memorandum of Association', FALSE),
('MERCHANT', 'AOA', 'Articles of Association', FALSE),
('MERCHANT', 'FORM_A', 'Latest Form-A / Form-9 (or Form-1 if newly incorporated)', FALSE),
('MERCHANT', 'SECP_INCORPORATION', 'Certificate of Incorporation (SECP)', FALSE),
('MERCHANT', 'TRUST_DEED', 'Certificate of Registration / Instrument of Trust', FALSE),
('MERCHANT', 'BYLAWS', 'By-laws / Rules & Regulations', FALSE),
('MERCHANT', 'GOVERNING_RESOLUTION', 'Governing body resolution to open/operate account', FALSE),
('MERCHANT', 'ANNUAL_ACCOUNTS', 'Annual accounts / financial disclosures (if applicable)', FALSE),
('SUB_MERCHANT', 'AUTH_ID_FRONT', 'Authorized signatory valid ID — Front', TRUE),
('SUB_MERCHANT', 'AUTH_ID_BACK', 'Authorized signatory valid ID — Back', TRUE),
('SUB_MERCHANT', 'AUTH_LIVE_PHOTO', 'Authorized signatory live / digital photograph', TRUE),
('SUB_MERCHANT', 'PARENT_AUTH', 'Parent merchant authorization letter', TRUE),
('SUB_MERCHANT', 'BOARD_RESOLUTION', 'Authority letter to open sub-merchant account', TRUE),
('SUB_MERCHANT', 'NTN_OR_TAX', 'NTN / Tax registration certificate (if available)', FALSE),
('SUB_MERCHANT', 'BANK_LETTER', 'Bank account maintenance letter', FALSE),
('SUB_MERCHANT', 'BUSINESS_PROOF', 'Business registration / shop proof', FALSE);
