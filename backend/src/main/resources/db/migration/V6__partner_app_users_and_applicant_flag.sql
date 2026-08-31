-- Phase 1: applicant-is-partner + partner app users (mobile KYC)

ALTER TABLE parties ADD COLUMN applicant_is_partner BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS partner_app_users (
    id                   BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    party_id             BIGINT       NOT NULL,
    associated_person_id BIGINT,
    phone                VARCHAR(40)  NOT NULL,
    email                VARCHAR(200),
    full_name            VARCHAR(200) NOT NULL,
    temp_pin             VARCHAR(20)  NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    app_invite_token     VARCHAR(64)  NOT NULL UNIQUE,
    invited_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at         TIMESTAMP NULL,
    CONSTRAINT fk_pau_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT uq_pau_party_phone UNIQUE (party_id, phone)
);

CREATE INDEX idx_pau_party ON partner_app_users(party_id);
CREATE INDEX idx_pau_token ON partner_app_users(app_invite_token);
