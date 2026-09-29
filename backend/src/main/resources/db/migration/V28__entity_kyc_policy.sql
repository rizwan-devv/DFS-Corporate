-- Back office: whether mobile/manual KYC is required for each Annex-C entity type.
-- Default on, so existing cases still need one completed KYC before approval.

CREATE TABLE entity_kyc_policy (
    entity_type   VARCHAR(40)  NOT NULL PRIMARY KEY,
    kyc_required  BOOLEAN      NOT NULL,
    updated_at    TIMESTAMP NULL,
    updated_by    VARCHAR(200) NULL
);

INSERT INTO entity_kyc_policy (entity_type, kyc_required) VALUES
('SOLE_PROPRIETORSHIP', TRUE),
('SMALL_BUSINESS', TRUE),
('PARTNERSHIP', TRUE),
('LLP', TRUE),
('LIMITED_COMPANY', TRUE),
('FOREIGN_BRANCH', TRUE),
('TRUST_SOCIETY', TRUE),
('NGO_NPO', TRUE);
