-- Video read-aloud verification (mobile KYC)

ALTER TABLE partner_app_users
    ADD COLUMN video_verification_status VARCHAR(32) NOT NULL DEFAULT 'NONE';

CREATE TABLE video_challenges (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    challenge_id    VARCHAR(64) NOT NULL,
    app_user_id     BIGINT NOT NULL,
    template_id     VARCHAR(10) NOT NULL,
    script_text     VARCHAR(500) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    consumed_at     TIMESTAMP NULL,
    video_path      VARCHAR(500) NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_video_challenge_id UNIQUE (challenge_id),
    CONSTRAINT fk_video_challenge_app_user FOREIGN KEY (app_user_id) REFERENCES partner_app_users(id)
);

CREATE INDEX idx_video_challenges_app_user ON video_challenges(app_user_id);
