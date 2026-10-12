INSERT INTO sizes(value, label, status, sort_order)
VALUES ('DEFAULT', 'Default', 'ACTIVE', 0)
ON CONFLICT DO NOTHING;
