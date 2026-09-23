-- Live bulk transfer batches (FT / IBFT / UBP) — orchestrates single DFS calls per row

CREATE TABLE live_bulk_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    party_id BIGINT NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    file_name VARCHAR(255),
    total_rows INT NOT NULL DEFAULT 0,
    success_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    created_by VARCHAR(200),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    CONSTRAINT uk_live_bulk_batches_public UNIQUE (public_id),
    CONSTRAINT fk_live_bulk_batches_party FOREIGN KEY (party_id) REFERENCES parties(id)
);

CREATE INDEX idx_live_bulk_batches_party ON live_bulk_batches(party_id);
CREATE INDEX idx_live_bulk_batches_status ON live_bulk_batches(status);

CREATE TABLE live_bulk_rows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    beneficiary_account VARCHAR(64),
    bank_imd VARCHAR(32),
    utility_company_code VARCHAR(64),
    consumer_no VARCHAR(64),
    amount VARCHAR(32),
    narration VARCHAR(500),
    response_code VARCHAR(16),
    response_message VARCHAR(1000),
    txn_ref VARCHAR(120),
    raw_line VARCHAR(1000),
    processed_at TIMESTAMP NULL,
    CONSTRAINT fk_live_bulk_rows_batch FOREIGN KEY (batch_id) REFERENCES live_bulk_batches(id) ON DELETE CASCADE
);

CREATE INDEX idx_live_bulk_rows_batch ON live_bulk_rows(batch_id);
