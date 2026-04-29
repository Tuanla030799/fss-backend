INSERT INTO admin_users(id, name, email, password_hash, role, status)
VALUES (
    gen_random_uuid(),
    'Super Admin',
    'tuanla0307@gmail.com',
    '$2b$10$smCFTSHozzh65eJPpTvY6urdgl.50BBgPffZn3FVuFzQEMuY32RFS',
    'super_admin',
    'ACTIVE'
)
ON CONFLICT (email) DO NOTHING;
