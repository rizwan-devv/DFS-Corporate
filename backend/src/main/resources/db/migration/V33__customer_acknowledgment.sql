-- Pre-approval customer acknowledgment (secure email link before back-office approve)
ALTER TABLE parties
    ADD COLUMN acknowledgment_token VARCHAR(64) NULL,
    ADD COLUMN acknowledgment_sent_at TIMESTAMP NULL,
    ADD COLUMN acknowledgment_expires_at TIMESTAMP NULL,
    ADD COLUMN acknowledged_at TIMESTAMP NULL,
    ADD COLUMN acknowledgment_ip VARCHAR(64) NULL;

CREATE UNIQUE INDEX uk_parties_acknowledgment_token ON parties (acknowledgment_token);
