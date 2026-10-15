ALTER TABLE products
    ADD COLUMN IF NOT EXISTS description_html TEXT;

ALTER TABLE blog_posts
    ADD COLUMN IF NOT EXISTS content_html TEXT;

CREATE INDEX IF NOT EXISTS idx_files_path ON files(path);

COMMENT ON COLUMN products.description_json IS 'Legacy Editor.js JSON content kept for backward compatibility.';
COMMENT ON COLUMN products.description_html IS 'Sanitized TinyMCE HTML content. Prefer this over description_json when present.';
COMMENT ON COLUMN blog_posts.content_json IS 'Legacy Editor.js JSON content kept for backward compatibility.';
COMMENT ON COLUMN blog_posts.content_html IS 'Sanitized TinyMCE HTML content. Prefer this over content_json when present.';
COMMENT ON COLUMN collections.description IS 'Collection description stored as sanitized HTML when submitted by TinyMCE.';

-- Incremental migration strategy:
-- keep legacy Editor.js JSON untouched, store new TinyMCE content in *_html fields,
-- and run a separate data backfill/job later if full JSON-to-HTML conversion is needed.
-- Protect existing Editor.js image uploads from the inactive-file cleanup by activating
-- files referenced from common image block shapes.
WITH legacy_file_paths AS (
    SELECT regexp_replace(value #>> '{}', '^/files/', '') AS path
    FROM products p
    CROSS JOIN LATERAL jsonb_path_query(p.description_json, '$.blocks[*].data.file.url') AS refs(value)
    WHERE p.deleted_at IS NULL
    UNION
    SELECT regexp_replace(value #>> '{}', '^/files/', '') AS path
    FROM blog_posts b
    CROSS JOIN LATERAL jsonb_path_query(b.content_json, '$.blocks[*].data.file.url') AS refs(value)
    WHERE b.deleted_at IS NULL
),
legacy_file_ids AS (
    SELECT (value #>> '{}')::uuid AS id
    FROM products p
    CROSS JOIN LATERAL jsonb_path_query(p.description_json, '$.blocks[*].data.fileId') AS refs(value)
    WHERE p.deleted_at IS NULL AND (value #>> '{}') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    UNION
    SELECT (value #>> '{}')::uuid AS id
    FROM products p
    CROSS JOIN LATERAL jsonb_path_query(p.description_json, '$.blocks[*].data.file.fileId') AS refs(value)
    WHERE p.deleted_at IS NULL AND (value #>> '{}') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    UNION
    SELECT (value #>> '{}')::uuid AS id
    FROM blog_posts b
    CROSS JOIN LATERAL jsonb_path_query(b.content_json, '$.blocks[*].data.fileId') AS refs(value)
    WHERE b.deleted_at IS NULL AND (value #>> '{}') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    UNION
    SELECT (value #>> '{}')::uuid AS id
    FROM blog_posts b
    CROSS JOIN LATERAL jsonb_path_query(b.content_json, '$.blocks[*].data.file.fileId') AS refs(value)
    WHERE b.deleted_at IS NULL AND (value #>> '{}') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
)
UPDATE files f
SET status = 'ACTIVE', updated_at = now()
WHERE f.status = 'INACTIVE'
  AND (
      EXISTS (SELECT 1 FROM legacy_file_paths refs WHERE refs.path = f.path)
      OR EXISTS (SELECT 1 FROM legacy_file_ids refs WHERE refs.id = f.id)
  );
