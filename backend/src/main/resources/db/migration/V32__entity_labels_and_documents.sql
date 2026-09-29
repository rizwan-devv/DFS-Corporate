-- Back office can rename an entity type and edit or add its onboarding documents.

ALTER TABLE entity_kyc_policy
    ADD COLUMN display_label VARCHAR(120) NULL;

CREATE TABLE entity_onboarding_documents (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    entity_type     VARCHAR(40)  NOT NULL,
    document_code   VARCHAR(64)  NOT NULL,
    document_label  VARCHAR(200) NOT NULL,
    mandatory       BOOLEAN      NOT NULL DEFAULT TRUE,
    requirement     VARCHAR(20)  NOT NULL DEFAULT 'REQUIRED',
    one_of_group    INT          NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_entity_onboarding_doc UNIQUE (entity_type, document_code)
);

CREATE INDEX idx_entity_onboarding_docs ON entity_onboarding_documents (entity_type, active, sort_order);

INSERT INTO entity_onboarding_documents
    (entity_type, document_code, document_label, mandatory, requirement, one_of_group, sort_order)
VALUES
('SOLE_PROPRIETORSHIP', 'AUTH_ID_FRONT', 'Authorized / account holder ID — front', TRUE, 'REQUIRED', 0, 1),
('SOLE_PROPRIETORSHIP', 'AUTH_ID_BACK', 'Authorized / account holder ID — back', TRUE, 'REQUIRED', 0, 2),
('SOLE_PROPRIETORSHIP', 'AUTH_LIVE_PHOTO', 'Live / digital photograph', TRUE, 'REQUIRED', 0, 3),
('SOLE_PROPRIETORSHIP', 'NTN_OR_TAX', 'Sales tax registration or NTN certificate (one of the sole-prop / small-business options)', FALSE, 'ONE_OF', 1, 4),
('SOLE_PROPRIETORSHIP', 'TRADE_BODY_MEMBERSHIP', 'Trade body membership certificate (one of the options)', FALSE, 'ONE_OF', 1, 5),
('SOLE_PROPRIETORSHIP', 'SOLE_LETTERHEAD_DECL', 'Sole proprietorship declaration on letterhead (one of the options)', FALSE, 'ONE_OF', 1, 6),
('SOLE_PROPRIETORSHIP', 'SOLE_ACCOUNT_REQ', 'Account opening requisition on letterhead (one of the options)', FALSE, 'ONE_OF', 1, 7),

('SMALL_BUSINESS', 'AUTH_ID_FRONT', 'Authorized / account holder ID — front', TRUE, 'REQUIRED', 0, 1),
('SMALL_BUSINESS', 'AUTH_ID_BACK', 'Authorized / account holder ID — back', TRUE, 'REQUIRED', 0, 2),
('SMALL_BUSINESS', 'AUTH_LIVE_PHOTO', 'Live / digital photograph', TRUE, 'REQUIRED', 0, 3),
('SMALL_BUSINESS', 'REGISTRATION_CERT', 'Registration certificate (one of the small-business options)', FALSE, 'ONE_OF', 1, 4),
('SMALL_BUSINESS', 'NTN_OR_TAX', 'Sales tax registration or NTN certificate (one of the sole-prop / small-business options)', FALSE, 'ONE_OF', 1, 5),
('SMALL_BUSINESS', 'TRADE_BODY_MEMBERSHIP', 'Trade body membership certificate (one of the options)', FALSE, 'ONE_OF', 1, 6),
('SMALL_BUSINESS', 'PROOF_OF_FUNDS', 'Proof of source of funds / income (one of the small-business options)', FALSE, 'ONE_OF', 1, 7),

('PARTNERSHIP', 'PARTNERSHIP_DEED', 'Attested partnership deed signed by all partners', TRUE, 'REQUIRED', 0, 1),
('PARTNERSHIP', 'PARTNERSHIP_AUTHORITY', 'Authority letter signed by all partners', TRUE, 'REQUIRED', 0, 2),
('PARTNERSHIP', 'PARTNERSHIP_REG_CERT', 'Registration certificate with Registrar of Firms', TRUE, 'REQUIRED', 0, 3),

('LLP', 'LLP_DEED', 'LLP deed / agreement', TRUE, 'REQUIRED', 0, 1),
('LLP', 'LLP_SECP_CERT', 'LLP registration certificate (SECP)', TRUE, 'REQUIRED', 0, 2),
('LLP', 'LLP_AUTHORITY', 'Authority letter by all partners to operate the account', TRUE, 'REQUIRED', 0, 3),

('LIMITED_COMPANY', 'LC_DIRECTOR_ID', 'Identity documents of all directors and authorized signatories', TRUE, 'REQUIRED', 0, 1),
('LIMITED_COMPANY', 'LC_BOARD_RESOLUTION', 'Board resolution to open the account and name who may operate it', TRUE, 'REQUIRED', 0, 2),
('LIMITED_COMPANY', 'LC_MOA_AOA', 'Memorandum and Articles of Association', TRUE, 'REQUIRED', 0, 3),
('LIMITED_COMPANY', 'LC_FORM_A', 'Latest Form-A (annual return)', TRUE, 'REQUIRED', 0, 4),
('LIMITED_COMPANY', 'LC_FORM_1', 'Form 1 — newly incorporated company (upload this or Form 9)', FALSE, 'ONE_OF', 1, 5),
('LIMITED_COMPANY', 'LC_FORM_9', 'Form 9 — already incorporated company (upload this or Form 1)', FALSE, 'ONE_OF', 1, 6),

('FOREIGN_BRANCH', 'FB_OFFICIAL_ID', 'Identity document of the senior official and authorized signatories', TRUE, 'REQUIRED', 0, 1),
('FOREIGN_BRANCH', 'FB_BOI_PERMISSION', 'Permission letter from the Board of Investment', TRUE, 'REQUIRED', 0, 2),
('FOREIGN_BRANCH', 'FB_DIRECTOR_LIST', 'List of directors on letterhead or the prescribed format', TRUE, 'REQUIRED', 0, 3),
('FOREIGN_BRANCH', 'FB_FORM_2', 'Form 2 — registration of documents of a foreign company', TRUE, 'REQUIRED', 0, 4),
('FOREIGN_BRANCH', 'FB_FORM_5', 'Form 5 — registration of alterations of a foreign company', TRUE, 'REQUIRED', 0, 5),
('FOREIGN_BRANCH', 'FB_PRINCIPAL_LETTER', 'Letter from the principal officer authorizing who may open and operate the account', TRUE, 'REQUIRED', 0, 6),

('TRUST_SOCIETY', 'TS_GOVERNING_ID', 'Identity documents of the governing body (board, trustees, or executive committee)', TRUE, 'REQUIRED', 0, 1),
('TRUST_SOCIETY', 'TS_SIGNATORY_ID', 'Identity documents of all authorized signatories', TRUE, 'REQUIRED', 0, 2),
('TRUST_SOCIETY', 'TS_TRUST_PARTIES_ID', 'Identity documents of settlor, trustee(s), protector (if any), and beneficiaries', TRUE, 'REQUIRED', 0, 3),
('TRUST_SOCIETY', 'TS_CONTROL_DECLARATION', 'Declaration on ultimate control, purpose, and source of funds', TRUE, 'REQUIRED', 0, 4),
('TRUST_SOCIETY', 'TS_REG_OR_INSTRUMENT', 'Certificate of registration or instrument of trust', TRUE, 'REQUIRED', 0, 5),
('TRUST_SOCIETY', 'TS_BYLAWS', 'By-laws / rules and regulations', TRUE, 'REQUIRED', 0, 6),
('TRUST_SOCIETY', 'TS_RESOLUTION', 'Resolution authorizing the person(s) to open and operate the account', TRUE, 'REQUIRED', 0, 7),

('NGO_NPO', 'NGO_GOVERNING_ID', 'Identity documents of the ultimate governing body', TRUE, 'REQUIRED', 0, 1),
('NGO_NPO', 'NGO_SIGNATORY_ID', 'Identity documents of all authorized signatories', TRUE, 'REQUIRED', 0, 2),
('NGO_NPO', 'NGO_REGISTRATION', 'Registration, certificate of incorporation, or licence (SECP or other authority)', TRUE, 'REQUIRED', 0, 3),
('NGO_NPO', 'NGO_MOA_AOA', 'Memorandum and Articles of Association', TRUE, 'REQUIRED', 0, 4),
('NGO_NPO', 'NGO_RESOLUTION', 'Resolution of the governing body authorizing who may operate the account', TRUE, 'REQUIRED', 0, 5),
('NGO_NPO', 'NGO_FORM_1', 'Form 1 — newly incorporated (upload this or Form 9)', FALSE, 'ONE_OF', 1, 6),
('NGO_NPO', 'NGO_FORM_9', 'Form 9 — already incorporated (upload this or Form 1)', FALSE, 'ONE_OF', 1, 7),
('NGO_NPO', 'NGO_ANNUAL_ACCOUNTS', 'Annual accounts or financial statements (if available)', FALSE, 'OPTIONAL', 0, 8);
