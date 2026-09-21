-- Pakistan UBP (utility bill payment) mock fields

ALTER TABLE mock_transfers ADD COLUMN ubp_category VARCHAR(40);
ALTER TABLE mock_transfers ADD COLUMN ubp_company VARCHAR(120);
ALTER TABLE mock_transfers ADD COLUMN consumer_number VARCHAR(64);
ALTER TABLE mock_transfers ADD COLUMN billing_month VARCHAR(20);
ALTER TABLE mock_transfers ADD COLUMN bill_due_date VARCHAR(40);
