-- Official-list AML/CFT watchlist (interim; no vendor API) + screen audit

CREATE TABLE aml_watchlist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    list_source VARCHAR(16) NOT NULL,
    list_name VARCHAR(120),
    full_name VARCHAR(300) NOT NULL,
    cnic VARCHAR(20),
    notes VARCHAR(500),
    active TINYINT(1) NOT NULL DEFAULT 1,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_version VARCHAR(80)
);

CREATE INDEX idx_aml_watch_cnic ON aml_watchlist(cnic);
CREATE INDEX idx_aml_watch_active ON aml_watchlist(active);

CREATE TABLE aml_screen_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    party_id BIGINT NOT NULL,
    subject_type VARCHAR(24) NOT NULL,
    subject_name VARCHAR(300),
    subject_cnic VARCHAR(20),
    outcome VARCHAR(24) NOT NULL,
    match_type VARCHAR(24),
    match_score INT,
    watchlist_id BIGINT,
    detail VARCHAR(1000),
    screened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aml_screen_party FOREIGN KEY (party_id) REFERENCES parties(id)
);

CREATE INDEX idx_aml_screen_party ON aml_screen_results(party_id);

ALTER TABLE parties
    ADD COLUMN sanctions_manual_clear TINYINT(1) NOT NULL DEFAULT 0 AFTER sanctions_notes;

-- Demo rows so a test CNIC proves HIT. Not a live UN/OFAC dump.
INSERT INTO aml_watchlist (list_source, list_name, full_name, cnic, notes, source_version) VALUES
('INTERNAL', 'DEMO', 'Demo Blacklisted Person', '9999999999991', 'Synthetic CNIC for portal demo — set party cnic_number to this to force HIT', 'demo-v1'),
('OFAC', 'SDN-SAMPLE', 'Osama Bin Laden', NULL, 'Illustrative name-only sample (public OFAC-style). Fuzzy name match → MANUAL_REVIEW', 'demo-v1'),
('UN', 'UNSC-SAMPLE', 'Islamic State', NULL, 'Illustrative entity name sample for fuzzy match demo', 'demo-v1'),
('NACTA', 'NACTA-SAMPLE', 'Proscribed Demo Org', '8888888888882', 'Synthetic NACTA-style row for demo', 'demo-v1');
