-- Soft brand for Zindigi Corporate portal (shared dfs_corporate DB).
INSERT INTO brands (public_id, code, name, active)
SELECT '00000000-0000-0000-0000-000000000010', 'ZINDIGI', 'Zindigi Corporate', TRUE
WHERE NOT EXISTS (SELECT 1 FROM brands WHERE code = 'ZINDIGI');
