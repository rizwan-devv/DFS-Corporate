-- App KYC: city/province selection + plaintext password for DFS Account API (cleared after provision)

ALTER TABLE partner_app_users ADD COLUMN province_id VARCHAR(32);
ALTER TABLE partner_app_users ADD COLUMN city_id VARCHAR(32);
ALTER TABLE partner_app_users ADD COLUMN password_plain VARCHAR(128);
