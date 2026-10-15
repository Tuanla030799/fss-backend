UPDATE products
SET description_json = '{}'::jsonb
WHERE description_json <> '{}'::jsonb;

UPDATE blog_posts
SET content_json = '{}'::jsonb
WHERE content_json <> '{}'::jsonb;

COMMENT ON COLUMN products.description_json IS 'Deprecated legacy Editor.js JSON field. Backend writes empty JSON; use description_html.';
COMMENT ON COLUMN blog_posts.content_json IS 'Deprecated legacy Editor.js JSON field. Backend writes empty JSON; use content_html.';
