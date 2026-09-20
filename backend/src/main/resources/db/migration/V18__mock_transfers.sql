-- Portal mock transfers (FT / IBFT / UBP / Raast) — demo only, not live rails

CREATE TABLE mock_transfers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    party_id BIGINT NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    mock_txn_ref VARCHAR(64) NOT NULL,
    account_number VARCHAR(64),
    ipin VARCHAR(64),
    bank_name VARCHAR(200),
    amount DECIMAL(18, 2),
    cnic VARCHAR(40),
    mobile VARCHAR(40),
    beneficiary_name VARCHAR(200),
    notes VARCHAR(1000),
    bulk_file_name VARCHAR(255),
    bulk_row_count INT,
    bulk_summary TEXT,
    raast_qr_payload VARCHAR(1000),
    created_by VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mock_transfer_public UNIQUE (public_id),
    CONSTRAINT fk_mock_transfer_party FOREIGN KEY (party_id) REFERENCES parties(id)
);

CREATE INDEX idx_mock_transfer_party ON mock_transfers(party_id);
CREATE INDEX idx_mock_transfer_created ON mock_transfers(created_at);
