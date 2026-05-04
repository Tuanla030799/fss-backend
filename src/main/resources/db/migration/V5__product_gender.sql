ALTER TABLE products ADD COLUMN gender VARCHAR(20) NOT NULL DEFAULT 'UNISEX';
ALTER TABLE products ADD CONSTRAINT chk_products_gender CHECK (gender IN ('MALE', 'FEMALE', 'UNISEX'));
CREATE INDEX idx_products_gender ON products(gender, status, created_at DESC) WHERE deleted_at IS NULL;
