INSERT INTO admin_users(id, name, email, password_hash, role, status)
SELECT
    gen_random_uuid(),
    'Super Admin',
    'tuanla0307@gmail.com',
    '$2b$10$smCFTSHozzh65eJPpTvY6urdgl.50BBgPffZn3FVuFzQEMuY32RFS',
    'super_admin',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM admin_users
    WHERE LOWER(email) = LOWER('tuanla0307@gmail.com')
      AND deleted_at IS NULL
);
