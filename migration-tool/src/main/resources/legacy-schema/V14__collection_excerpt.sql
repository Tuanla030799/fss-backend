ALTER TABLE collections
    ADD COLUMN IF NOT EXISTS excerpt TEXT;

UPDATE collections
SET excerpt = NULLIF(TRIM(regexp_replace(COALESCE(description, ''), '<[^>]*>', '', 'g')), '')
WHERE excerpt IS NULL;

COMMENT ON COLUMN collections.excerpt IS 'Plain text summary for collection cards and forms.';
COMMENT ON COLUMN collections.description IS 'Sanitized HTML description. Prefer request/response field descriptionHtml.';
