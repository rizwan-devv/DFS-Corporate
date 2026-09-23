-- Corporate bulk employee onboarding: park rows on DFS, await account-open confirmation

CREATE TABLE employee_bulk_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    party_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    file_name VARCHAR(255),
    total_rows INT NOT NULL DEFAULT 0,
    parked_rows INT NOT NULL DEFAULT 0,
    open_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    created_by VARCHAR(200),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    parked_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    CONSTRAINT uk_employee_bulk_batches_public UNIQUE (public_id),
    CONSTRAINT fk_employee_bulk_batches_party FOREIGN KEY (party_id) REFERENCES parties(id)
);

CREATE INDEX idx_employee_bulk_batches_party ON employee_bulk_batches(party_id);
CREATE INDEX idx_employee_bulk_batches_status ON employee_bulk_batches(status);

CREATE TABLE employee_bulk_rows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(36) NOT NULL,
    batch_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    employee_code VARCHAR(64),
    full_name VARCHAR(200),
    father_name VARCHAR(200),
    mobile VARCHAR(32),
    cnic VARCHAR(32),
    date_of_birth VARCHAR(32),
    gender VARCHAR(16),
    email VARCHAR(200),
    department VARCHAR(120),
    park_ref VARCHAR(120),
    dfs_account_no VARCHAR(64),
    dfs_customer_id VARCHAR(64),
    response_message VARCHAR(1000),
    raw_line VARCHAR(1500),
    parked_at TIMESTAMP NULL,
    confirmed_at TIMESTAMP NULL,
    CONSTRAINT uk_employee_bulk_rows_public UNIQUE (public_id),
    CONSTRAINT fk_employee_bulk_rows_batch FOREIGN KEY (batch_id) REFERENCES employee_bulk_batches(id) ON DELETE CASCADE
);

CREATE INDEX idx_employee_bulk_rows_batch ON employee_bulk_rows(batch_id);
CREATE INDEX idx_employee_bulk_rows_status ON employee_bulk_rows(status);
CREATE INDEX idx_employee_bulk_rows_mobile ON employee_bulk_rows(mobile);
