-- Test-only demo fixture. Never load this through runtime Flyway locations.
-- Roles and accounts are seeded without role grants or permissions; add them only after the matrix is approved.

INSERT INTO public.staff_members (staff_code, full_name, status)
VALUES ('LOCAL_DEMO_STAFF', 'Local Demo Staff', 'ACTIVE')
ON CONFLICT (staff_code) DO NOTHING;

INSERT INTO public.roles (code, name, description)
VALUES
    ('USER', 'User', 'Local development role; no permissions are assigned.'),
    ('ADMIN', 'Admin', 'Local development role; no permissions are assigned.'),
    ('CLINIC_MANAGER', 'Clinic Manager', 'Local development role; no permissions are assigned.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.accounts (account_type, username, password_hash, staff_member_id, status)
SELECT 'STAFF', 'admin', '{bcrypt}$2a$10$O/YXhDKQL.hIXmXKTOCw3OVRjIewaCi0T/IzqJKjUlXOFzju0ApDS', staff.id, 'ACTIVE'
FROM public.staff_members staff
WHERE staff.staff_code = 'LOCAL_DEMO_STAFF' AND staff.status = 'ACTIVE'
ON CONFLICT (username) DO NOTHING;