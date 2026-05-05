CREATE TABLE sizes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    value VARCHAR(20) NOT NULL,
    label VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by UUID REFERENCES admin_users(id),
    updated_by UUID REFERENCES admin_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_sizes_value_active ON sizes(LOWER(value)) WHERE deleted_at IS NULL;
CREATE INDEX idx_sizes_status_sort ON sizes(status, sort_order, value) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_sizes_set_updated_at BEFORE UPDATE ON sizes FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE colors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(80) NOT NULL,
    color_code VARCHAR(30),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by UUID REFERENCES admin_users(id),
    updated_by UUID REFERENCES admin_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_colors_name_active ON colors(LOWER(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_colors_status_sort ON colors(status, sort_order, name) WHERE deleted_at IS NULL;
CREATE INDEX idx_colors_code ON colors(color_code) WHERE deleted_at IS NULL AND color_code IS NOT NULL;
CREATE TRIGGER trg_colors_set_updated_at BEFORE UPDATE ON colors FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO sizes(value, label, status, sort_order)
SELECT value, value, 'ACTIVE', ROW_NUMBER() OVER (ORDER BY value)
FROM (
    SELECT MIN(TRIM(size)) AS value
    FROM skus
    WHERE size IS NOT NULL AND TRIM(size) <> ''
    GROUP BY LOWER(TRIM(size))
) existing_sizes
ON CONFLICT DO NOTHING;

INSERT INTO colors(name, color_code, status, sort_order)
SELECT name, color_code, 'ACTIVE', ROW_NUMBER() OVER (ORDER BY name)
FROM (
    SELECT DISTINCT ON (LOWER(name))
           name,
           color_code
    FROM (
        SELECT COALESCE(NULLIF(TRIM(color_name), ''), TRIM(name)) AS name,
               NULLIF(TRIM(color_code), '') AS color_code
        FROM product_variants
        WHERE COALESCE(NULLIF(TRIM(color_name), ''), NULLIF(TRIM(name), '')) IS NOT NULL
    ) variant_colors
    ORDER BY LOWER(name), color_code NULLS LAST
) existing_colors
ON CONFLICT DO NOTHING;

ALTER TABLE product_variants ADD COLUMN color_id UUID REFERENCES colors(id);
ALTER TABLE skus ADD COLUMN size_id UUID REFERENCES sizes(id);

UPDATE product_variants variant
SET color_id = color.id
FROM colors color
WHERE variant.color_id IS NULL
  AND LOWER(color.name) = LOWER(COALESCE(NULLIF(TRIM(variant.color_name), ''), TRIM(variant.name)));

UPDATE skus sku
SET size_id = size_ref.id
FROM sizes size_ref
WHERE sku.size_id IS NULL
  AND LOWER(size_ref.value) = LOWER(TRIM(sku.size));

CREATE INDEX idx_product_variants_color ON product_variants(color_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_skus_size_id ON skus(size_id, status) WHERE deleted_at IS NULL;
