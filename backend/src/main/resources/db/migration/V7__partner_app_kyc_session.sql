-- Phase 4: partner app KYC session + profile fields

ALTER TABLE partner_app_users ADD COLUMN session_token VARCHAR(64);
ALTER TABLE partner_app_users ADD COLUMN cnic_number VARCHAR(40);
ALTER TABLE partner_app_users ADD COLUMN cnic_full_name VARCHAR(200);
ALTER TABLE partner_app_users ADD COLUMN date_of_birth DATE;
ALTER TABLE partner_app_users ADD COLUMN failure_reason VARCHAR(500);
ALTER TABLE partner_app_users ADD COLUMN video_kyc_ref VARCHAR(500);
ALTER TABLE partner_app_users ADD COLUMN biometric_ref VARCHAR(500);
ALTER TABLE partner_app_users ADD COLUMN selfie_uploaded BOOLEAN DEFAULT FALSE;

CREATE UNIQUE INDEX uq_pau_session ON partner_app_users(session_token);
