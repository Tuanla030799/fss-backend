CREATE TABLE collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    description TEXT,
    description_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    file_id UUID REFERENCES files(id),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by UUID REFERENCES admin_users(id),
    updated_by UUID REFERENCES admin_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_collections_slug_active ON collections(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_collections_public ON collections(status, sort_order, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_collections_name_trgm ON collections USING gin (name gin_trgm_ops);
CREATE TRIGGER trg_collections_set_updated_at BEFORE UPDATE ON collections FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE collection_products (
    collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (collection_id, product_id)
);
CREATE INDEX idx_collection_products_collection ON collection_products(collection_id, sort_order);
CREATE INDEX idx_collection_products_product ON collection_products(product_id);

CREATE TABLE blog_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(220) NOT NULL,
    slug VARCHAR(260) NOT NULL,
    excerpt TEXT,
    content_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    cover_file_id UUID REFERENCES files(id),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    created_by UUID REFERENCES admin_users(id),
    updated_by UUID REFERENCES admin_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_blog_posts_slug_active ON blog_posts(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_blog_posts_public ON blog_posts(status, published_at DESC, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_blog_posts_title_trgm ON blog_posts USING gin (title gin_trgm_ops);
CREATE TRIGGER trg_blog_posts_set_updated_at BEFORE UPDATE ON blog_posts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
