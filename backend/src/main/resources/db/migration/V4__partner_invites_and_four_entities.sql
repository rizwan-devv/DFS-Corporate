-- Partner KYC invites + slim Annex-C (entities 1–4 only)

CREATE TABLE IF NOT EXISTS partner_invites (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_token    VARCHAR(64)  NOT NULL UNIQUE,
    party_id        BIGINT       NOT NULL,
    associated_person_id BIGINT   NOT NULL,
    email           VARCHAR(200) NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    invited_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP NULL,
    expires_at      TIMESTAMP    NOT NULL,
    kyc_payload     LONGTEXT,
    CONSTRAINT fk_invite_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT fk_invite_person FOREIGN KEY (associated_person_id) REFERENCES associated_persons(id)
);

CREATE INDEX idx_partner_invites_party ON partner_invites(party_id);

ALTER TABLE parties ADD COLUMN partnership_unregistered BOOLEAN DEFAULT FALSE;

UPDATE parties SET entity_type = 'LLP' WHERE entity_type IN ('LIMITED_COMPANY', 'PUBLIC_LIMITED', 'PRIVATE_LIMITED');
UPDATE parties SET entity_type = 'PARTNERSHIP' WHERE entity_type IN ('AOP');
UPDATE parties SET entity_type = 'SOLE_PROPRIETORSHIP'
  WHERE entity_type IS NOT NULL
    AND entity_type NOT IN ('SOLE_PROPRIETORSHIP', 'SMALL_BUSINESS', 'PARTNERSHIP', 'LLP');

-- Refresh document catalog for Annex-C 1–4
DELETE FROM required_documents;

INSERT INTO required_documents (party_type, document_code, document_label, mandatory) VALUES
('MERCHANT', 'AUTH_ID_FRONT', 'Authorized / account holder ID — Front', TRUE),
('MERCHANT', 'AUTH_ID_BACK', 'Authorized / account holder ID — Back', TRUE),
('MERCHANT', 'AUTH_LIVE_PHOTO', 'Live / digital photograph', TRUE),
('MERCHANT', 'NTN_OR_TAX', 'Sales tax registration or NTN certificate', FALSE),
('MERCHANT', 'TRADE_BODY_MEMBERSHIP', 'Trade body membership certificate', FALSE),
('MERCHANT', 'SOLE_LETTERHEAD_DECL', 'Sole proprietorship declaration on letterhead', FALSE),
('MERCHANT', 'SOLE_ACCOUNT_REQ', 'Account/wallet opening requisition on letterhead', FALSE),
('MERCHANT', 'REGISTRATION_CERT', 'Registration certificate (registered concerns)', FALSE),
('MERCHANT', 'PROOF_OF_FUNDS', 'Proof of source of funds / income', FALSE),
('MERCHANT', 'PARTNERSHIP_DEED', 'Attested Partnership Deed (all partners)', FALSE),
('MERCHANT', 'PARTNERSHIP_REG_CERT', 'Registration Certificate with Registrar of Firms', FALSE),
('MERCHANT', 'PARTNERSHIP_AUTHORITY', 'Authority letter signed by all partners', FALSE),
('MERCHANT', 'LLP_DEED', 'LLP Deed / Agreement', FALSE),
('MERCHANT', 'LLP_FORM_III', 'LLP Form-III (partners)', FALSE),
('MERCHANT', 'LLP_SECP_CERT', 'LLP Registration Certificate (SECP)', FALSE),
('MERCHANT', 'LLP_FORM_V', 'LLP Form-V (change in partners, if any)', FALSE),
('MERCHANT', 'LLP_AUTHORITY', 'Authority letter by all partners to operate account', FALSE),
('MERCHANT', 'PARTNER_ID_NOTE', 'Note: each partner completes ID KYC via invite link', FALSE),

('SUB_MERCHANT', 'AUTH_ID_FRONT', 'Authorized signatory ID — Front', TRUE),
('SUB_MERCHANT', 'AUTH_ID_BACK', 'Authorized signatory ID — Back', TRUE),
('SUB_MERCHANT', 'AUTH_LIVE_PHOTO', 'Live / digital photograph', TRUE),
('SUB_MERCHANT', 'PARENT_AUTH', 'Parent merchant authorization letter', TRUE),
('SUB_MERCHANT', 'NTN_OR_TAX', 'NTN / tax certificate (if available)', FALSE);
