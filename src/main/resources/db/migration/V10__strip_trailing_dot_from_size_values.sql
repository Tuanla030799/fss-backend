UPDATE sizes
SET value = regexp_replace(value, '\.$', ''),
    label = regexp_replace(label, '\.$', '')
WHERE value LIKE '%.'
   OR label LIKE '%.';
