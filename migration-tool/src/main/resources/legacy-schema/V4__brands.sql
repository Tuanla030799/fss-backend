CREATE TABLE brands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    description TEXT,
    file_id UUID REFERENCES files(id),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by UUID REFERENCES admin_users(id),
    updated_by UUID REFERENCES admin_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_brands_slug_active ON brands(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_brands_status_sort ON brands(status, sort_order, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_brands_name_trgm ON brands USING gin (name gin_trgm_ops);
CREATE TRIGGER trg_brands_set_updated_at BEFORE UPDATE ON brands FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE products ADD COLUMN brand_id UUID REFERENCES brands(id);
CREATE INDEX idx_products_brand ON products(brand_id, status, created_at DESC) WHERE deleted_at IS NULL;

INSERT INTO brands(name, slug, status, sort_order)
VALUES ('Adidas', 'adidas', 'ACTIVE', 10),
       ('Nike', 'nike', 'ACTIVE', 20);
