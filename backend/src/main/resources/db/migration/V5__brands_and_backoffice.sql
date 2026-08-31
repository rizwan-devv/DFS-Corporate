-- Brands (multi-tenant) + party.brand_id for backoffice

CREATE TABLE IF NOT EXISTS brands (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id   VARCHAR(36)  NOT NULL UNIQUE,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO brands (public_id, code, name, active) VALUES
('00000000-0000-0000-0000-000000000001', 'DFS', 'DFS Corporate', TRUE),
('00000000-0000-0000-0000-000000000002', 'ALPHA', 'Brand Alpha', TRUE),
('00000000-0000-0000-0000-000000000003', 'BETA', 'Brand Beta', TRUE);

ALTER TABLE parties ADD COLUMN brand_id BIGINT;

UPDATE parties p
SET p.brand_id = (SELECT b.id FROM (SELECT id FROM brands WHERE code = 'DFS' LIMIT 1) b)
WHERE p.brand_id IS NULL;

ALTER TABLE parties ADD CONSTRAINT fk_parties_brand
    FOREIGN KEY (brand_id) REFERENCES brands(id);

CREATE INDEX idx_parties_brand ON parties(brand_id);
CREATE INDEX idx_parties_status ON parties(status);
