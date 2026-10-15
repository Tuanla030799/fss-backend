UPDATE sizes
SET value = to_char(value::numeric, 'FM999999990.999999'),
    label = to_char(value::numeric, 'FM999999990.999999')
WHERE value ~ '^[0-9]+(\.[0-9]+)?$';

UPDATE sizes
SET sort_order = ((value::numeric * 2)::int - 70)
WHERE value ~ '^[0-9]+(\.[0-9]+)?$'
  AND value::numeric BETWEEN 35.5 AND 52.5;
