ALTER TABLE collections
    ADD COLUMN IF NOT EXISTS description_html TEXT;

UPDATE collections
SET description_html = description
WHERE description_html IS NULL
  AND description IS NOT NULL;

COMMENT ON COLUMN collections.description_html IS 'Sanitized HTML description for collection detail pages.';
