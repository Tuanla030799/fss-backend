CREATE INDEX IF NOT EXISTS idx_product_images_file_id ON product_images(file_id);
CREATE INDEX IF NOT EXISTS idx_product_variants_image_file_id ON product_variants(image_file_id);
CREATE INDEX IF NOT EXISTS idx_landing_banners_file_id ON landing_banners(file_id);
CREATE INDEX IF NOT EXISTS idx_collections_file_id ON collections(file_id);
CREATE INDEX IF NOT EXISTS idx_blog_posts_cover_file_id ON blog_posts(cover_file_id);
CREATE INDEX IF NOT EXISTS idx_brands_file_id ON brands(file_id);
CREATE INDEX IF NOT EXISTS idx_brands_file_size_id ON brands(file_size_id);
