-- Corporate KYC (SBP EMI Regulations — corporate accounts only)
ALTER TABLE parties ADD COLUMN father_or_spouse_name VARCHAR(200);
ALTER TABLE parties ADD COLUMN cnic_number VARCHAR(30);
ALTER TABLE parties ADD COLUMN id_document_type VARCHAR(40);
ALTER TABLE parties ADD COLUMN mother_name VARCHAR(200);
ALTER TABLE parties ADD COLUMN place_of_birth VARCHAR(120);
ALTER TABLE parties ADD COLUMN business_address VARCHAR(500);
ALTER TABLE parties ADD COLUMN secp_registration_no VARCHAR(80);
ALTER TABLE parties ADD COLUMN entity_type VARCHAR(40);
ALTER TABLE parties ADD COLUMN terms_accepted BOOLEAN DEFAULT FALSE;
ALTER TABLE parties ADD COLUMN terms_accepted_at TIMESTAMP NULL;
ALTER TABLE parties ADD COLUMN kyc_tier VARCHAR(32) DEFAULT 'CORPORATE';

-- Replace document matrix: corporate Merchant / Sub-merchant only (remove Agent/Customer packs)
DELETE FROM required_documents;

INSERT INTO required_documents (party_type, document_code, document_label, mandatory) VALUES
('MERCHANT', 'SECP_INCORPORATION', 'SECP Certificate of Incorporation', TRUE),
('MERCHANT', 'MOA', 'Memorandum of Association (MoA)', TRUE),
('MERCHANT', 'AOA', 'Articles of Association (AoA)', TRUE),
('MERCHANT', 'NTN_CERT', 'NTN Certificate', TRUE),
('MERCHANT', 'AUTH_CNIC_FRONT', 'Authorized Person CNIC — Front', TRUE),
('MERCHANT', 'AUTH_CNIC_BACK', 'Authorized Person CNIC — Back', TRUE),
('MERCHANT', 'AUTH_PHOTO', 'Authorized Person Live / Digital Photograph', TRUE),
('MERCHANT', 'BOARD_RESOLUTION', 'Board Resolution / Authority Letter to Open Account', TRUE),
('MERCHANT', 'BANK_LETTER', 'Bank Account Maintenance Letter', TRUE),
('MERCHANT', 'BUSINESS_PROOF', 'Business Registration / Shop Proof (Sole Proprietor)', FALSE),
('MERCHANT', 'PARTNERSHIP_DEED', 'Partnership Deed (Partnership / AOP)', FALSE),
('MERCHANT', 'FORM_29', 'SECP Form 29 (optional)', FALSE),

('SUB_MERCHANT', 'SECP_INCORPORATION', 'SECP Certificate of Incorporation', FALSE),
('SUB_MERCHANT', 'NTN_CERT', 'NTN Certificate', FALSE),
('SUB_MERCHANT', 'AUTH_CNIC_FRONT', 'Authorized Person CNIC — Front', TRUE),
('SUB_MERCHANT', 'AUTH_CNIC_BACK', 'Authorized Person CNIC — Back', TRUE),
('SUB_MERCHANT', 'AUTH_PHOTO', 'Authorized Person Live / Digital Photograph', TRUE),
('SUB_MERCHANT', 'PARENT_AUTH', 'Parent Merchant Authorization Letter', TRUE),
('SUB_MERCHANT', 'BOARD_RESOLUTION', 'Authority Letter to Open Sub-merchant Account', TRUE),
('SUB_MERCHANT', 'BANK_LETTER', 'Bank Account Maintenance Letter', TRUE),
('SUB_MERCHANT', 'BUSINESS_PROOF', 'Business Registration / Shop Proof', FALSE);
