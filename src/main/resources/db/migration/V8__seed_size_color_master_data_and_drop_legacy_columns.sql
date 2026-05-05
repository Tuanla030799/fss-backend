INSERT INTO colors(name, color_code, status, sort_order)
VALUES
    ('black', '#000000', 'ACTIVE', 10),
    ('blue', '#0000FF', 'ACTIVE', 20),
    ('brown', '#A52A2A', 'ACTIVE', 30),
    ('green', '#008000', 'ACTIVE', 40),
    ('gray', '#808080', 'ACTIVE', 50),
    ('multi-color', NULL, 'ACTIVE', 60),
    ('orange', '#FFA500', 'ACTIVE', 70),
    ('pink', '#FFC0CB', 'ACTIVE', 80),
    ('purple', '#800080', 'ACTIVE', 90),
    ('red', '#FF0000', 'ACTIVE', 100),
    ('white', '#FFFFFF', 'ACTIVE', 110),
    ('yellow', '#FFFF00', 'ACTIVE', 120)
ON CONFLICT DO NOTHING;

INSERT INTO sizes(value, label, status, sort_order)
SELECT value, value, 'ACTIVE', ROW_NUMBER() OVER (ORDER BY sort_value)
FROM (
    SELECT n::numeric / 2 AS sort_value,
           CASE
               WHEN n % 2 = 0 THEN (n / 2)::text
               ELSE (n::numeric / 2)::text
           END AS value
    FROM generate_series(71, 105) AS n
) generated_sizes
ON CONFLICT DO NOTHING;

UPDATE product_variants
SET color_id = (SELECT id FROM colors WHERE LOWER(name) = 'multi-color' AND deleted_at IS NULL LIMIT 1)
WHERE color_id IS NULL;

DELETE FROM skus WHERE size_id IS NULL;

ALTER TABLE product_variants ALTER COLUMN color_id SET NOT NULL;
ALTER TABLE skus ALTER COLUMN size_id SET NOT NULL;

DROP INDEX IF EXISTS idx_skus_variant_size;
DROP INDEX IF EXISTS idx_skus_size;

ALTER TABLE product_variants DROP COLUMN color_name;
ALTER TABLE product_variants DROP COLUMN color_code;
ALTER TABLE skus DROP COLUMN size;

CREATE INDEX idx_skus_variant_size ON skus(variant_id, size_id) WHERE deleted_at IS NULL;
