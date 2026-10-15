-- Sample production-like data on top of the legacy schema (V1..V18 already applied).
INSERT INTO files(id, path, status, created_at, updated_at) VALUES
 ('11111111-1111-1111-1111-111111111111', '2026-06-01/aaa-main.jpg', 'ACTIVE', now() - interval '30 days', now() - interval '30 days'),
 ('22222222-2222-2222-2222-222222222222', '2026-06-01/bbb-inline image.png', 'ACTIVE', now() - interval '30 days', now() - interval '30 days'),
 ('33333333-3333-3333-3333-333333333333', '2026-06-01/ccc-cover.jpg', 'ACTIVE', now() - interval '30 days', now() - interval '30 days'),
 ('44444444-4444-4444-4444-444444444444', '2026-06-02/ddd-orphan.jpg', 'INACTIVE', now() - interval '3 days', now() - interval '3 days');

INSERT INTO categories(id, name, slug, status, sort_order) VALUES
 ('c0000000-0000-0000-0000-000000000001', 'Giày chạy', 'giay-chay', 'ACTIVE', 1);
UPDATE brands SET file_id = '33333333-3333-3333-3333-333333333333' WHERE slug = 'nike';

INSERT INTO products(id, category_id, brand_id, gender, name, slug, short_description, description_html, status, is_featured, featured_order, created_at)
SELECT 'b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', id, 'MALE', 'Pegasus 41', 'pegasus-41',
       'Êm', '<p>Mô tả</p><img src="https://thepocketshoes.store/files/2026-06-01/bbb-inline%20image.png">', 'ACTIVE', true, 1,
       now() - interval '10 days'
FROM brands WHERE slug = 'nike';
INSERT INTO products(id, category_id, name, slug, status, deleted_at)
VALUES ('b0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'Old', 'old', 'ACTIVE', now());

INSERT INTO product_images(id, product_id, file_id, image_type, sort_order, is_primary)
VALUES ('d0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'GALLERY', 0, true);

INSERT INTO product_variants(id, product_id, name, color_id, status, sort_order)
SELECT 'e0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'Đen', id, 'ACTIVE', 0
FROM colors WHERE name = 'black' AND deleted_at IS NULL;

INSERT INTO skus(id, product_id, variant_id, sku_code, size_id, price, sale_price, stock, status)
SELECT 'f0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001',
       'PEG-40', id, 3200000.00, 2990000.50, 7, 'ACTIVE' FROM sizes WHERE value = '40' AND deleted_at IS NULL;
INSERT INTO skus(id, product_id, variant_id, sku_code, size_id, price, stock, status)
SELECT 'f0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001',
       'PEG-41', id, 3200000.00, 0, 'ACTIVE' FROM sizes WHERE value = '41' AND deleted_at IS NULL;

INSERT INTO coupons(id, code, discount_type, discount_value, usage_limit, used_count, status)
VALUES ('a0000000-0000-0000-0000-000000000001', 'SUMMER10', 'PERCENT', 10, 100, 1, 'ACTIVE');

INSERT INTO customers(id, full_name, email, phone) VALUES ('a1000000-0000-0000-0000-000000000001', 'Khách', 'Khach@Example.com', '0900000001');

INSERT INTO orders(id, order_code, customer_name, customer_phone, shipping_address, status, coupon_id, customer_id,
                   subtotal_amount, discount_amount, shipping_fee, total_amount, created_at)
VALUES ('a2000000-0000-0000-0000-000000000001', 'SHOE-20260601-ABC', 'Khách', '0900000001', 'HN', 'CONFIRMED',
        'a0000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 2990000.50, 299000.05, 30000, 2721000.45,
        now() - interval '5 days');
INSERT INTO order_items(id, order_id, sku_id, product_name, sku_code, variant_name, size, unit_price, quantity, line_total)
VALUES ('a3000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001',
        'Pegasus 41', 'PEG-40', 'Đen', '40', 2990000.50, 1, 2990000.50);
INSERT INTO order_status_history(id, order_id, old_status, new_status, note, created_at) VALUES
 ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', NULL, 'PENDING', 'Order created', now() - interval '5 days'),
 ('a4000000-0000-0000-0000-000000000002', 'a2000000-0000-0000-0000-000000000001', 'PENDING', 'CONFIRMED', 'ok', now() - interval '4 days');

INSERT INTO collections(id, name, slug, excerpt, description_html, status, sort_order)
VALUES ('a5000000-0000-0000-0000-000000000001', 'Summer', 'summer', 'Hè', '<p>Hè</p>', 'ACTIVE', 0);
INSERT INTO collection_products(collection_id, product_id, sort_order) VALUES
 ('a5000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 0),
 ('a5000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000002', 1);

INSERT INTO blog_posts(id, title, slug, content_html, cover_file_id, status, published_at)
VALUES ('a6000000-0000-0000-0000-000000000001', 'Tin', 'tin', '<p>x</p>', '33333333-3333-3333-3333-333333333333', 'ACTIVE', now() - interval '1 day');

INSERT INTO landing_banners(id, title, file_id, status) VALUES ('a7000000-0000-0000-0000-000000000001', 'Hero', '11111111-1111-1111-1111-111111111111', 'ACTIVE');
