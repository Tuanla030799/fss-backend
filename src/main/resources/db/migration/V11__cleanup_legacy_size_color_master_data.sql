UPDATE product_variants
SET color_id = (SELECT id FROM colors WHERE LOWER(name) = 'multi-color' AND deleted_at IS NULL LIMIT 1)
WHERE color_id NOT IN (
    SELECT id
    FROM colors
    WHERE LOWER(name) IN ('black', 'blue', 'brown', 'green', 'gray', 'multi-color', 'orange', 'pink', 'purple', 'red', 'white', 'yellow')
      AND deleted_at IS NULL
);

UPDATE skus
SET size_id = (SELECT id FROM sizes WHERE value = '36' AND deleted_at IS NULL LIMIT 1)
WHERE size_id NOT IN (
    SELECT id
    FROM sizes
    WHERE value ~ '^[0-9]+(\.[0-9]+)?$'
      AND value::numeric BETWEEN 35.5 AND 52.5
      AND deleted_at IS NULL
);

UPDATE colors
SET deleted_at = now(),
    status = 'INACTIVE'
WHERE LOWER(name) NOT IN ('black', 'blue', 'brown', 'green', 'gray', 'multi-color', 'orange', 'pink', 'purple', 'red', 'white', 'yellow')
  AND deleted_at IS NULL;

UPDATE sizes
SET deleted_at = now(),
    status = 'INACTIVE'
WHERE deleted_at IS NULL
  AND (
      value !~ '^[0-9]+(\.[0-9]+)?$'
      OR value::numeric < 35.5
      OR value::numeric > 52.5
  );
