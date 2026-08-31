-- Fields for KYC app journey + DFS backend corporateonboarding payload
-- Use TEXT for long strings to stay under MySQL InnoDB row-size limit
ALTER TABLE parties ADD COLUMN gender VARCHAR(10);
ALTER TABLE parties ADD COLUMN date_of_birth DATE;
ALTER TABLE parties ADD COLUMN nid_issuance_date DATE;
ALTER TABLE parties ADD COLUMN permanent_address TEXT;
ALTER TABLE parties ADD COLUMN present_address TEXT;
ALTER TABLE parties ADD COLUMN city_id VARCHAR(32);
ALTER TABLE parties ADD COLUMN business_type_id VARCHAR(32);
ALTER TABLE parties ADD COLUMN expected_monthly_volume_id VARCHAR(32);
ALTER TABLE parties ADD COLUMN parent_agent_id VARCHAR(64);
ALTER TABLE parties ADD COLUMN level_code VARCHAR(16) DEFAULT 'L4';
ALTER TABLE parties ADD COLUMN wallet_pin VARCHAR(20);

ALTER TABLE partner_app_users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE partner_app_users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE partner_app_users ADD COLUMN mobile_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE partner_app_users ADD COLUMN father_name VARCHAR(200);
ALTER TABLE partner_app_users ADD COLUMN gender VARCHAR(10);
ALTER TABLE partner_app_users ADD COLUMN permanent_address TEXT;
ALTER TABLE partner_app_users ADD COLUMN present_address TEXT;
ALTER TABLE partner_app_users ADD COLUMN nid_issuance_date DATE;
ALTER TABLE partner_app_users ADD COLUMN wallet_pin VARCHAR(20);
ALTER TABLE partner_app_users ADD COLUMN imei_no VARCHAR(64);
ALTER TABLE partner_app_users ADD COLUMN device_model VARCHAR(100);
ALTER TABLE partner_app_users ADD COLUMN app_version VARCHAR(40);
ALTER TABLE partner_app_users ADD COLUMN password_changed_at TIMESTAMP NULL;
