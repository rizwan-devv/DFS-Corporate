-- Phase 1: Incomplete docs flow, KYC fail attempts / bank visit, partner signature

ALTER TABLE partner_app_users ADD COLUMN kyc_fail_count INT NOT NULL DEFAULT 0;
ALTER TABLE partner_app_users ADD COLUMN bank_visit_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE partner_app_users ADD COLUMN manual_kyc_approve_reason VARCHAR(1000);
ALTER TABLE partner_app_users ADD COLUMN manual_kyc_approved_by VARCHAR(200);
ALTER TABLE partner_app_users ADD COLUMN manual_kyc_approved_at TIMESTAMP NULL;
ALTER TABLE partner_app_users ADD COLUMN signature_uploaded BOOLEAN NOT NULL DEFAULT FALSE;
