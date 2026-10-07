-- Local-only repeatable access-control seed (loaded from application-local.yaml).
-- The demo staff account records its bootstrap ADMIN grant. Only the Participant roster permissions
-- (see V003) are granted to the local ADMIN and CLINIC_MANAGER roles; no other permission is seeded.

INSERT INTO public.staff_members (staff_code, full_name, status)
VALUES ('LOCAL_DEMO_STAFF', 'Local Demo Staff', 'ACTIVE')
ON CONFLICT (staff_code) DO NOTHING;

INSERT INTO public.roles (code, name, description)
VALUES
    ('USER', 'User', 'Local development role; no permissions are assigned.'),
    ('ADMIN', 'Admin', 'Local development role; no permissions are assigned.'),
    ('CLINIC_MANAGER', 'Clinic Manager', 'Local development role; no permissions are assigned.')
ON CONFLICT (code) DO NOTHING;

UPDATE public.accounts AS account
SET username = 'admin', password_hash = '{bcrypt}$2a$10$jdFKPVm/lEHGPgMB5h5FZujxEMPq7ahPlqCuvWMVQy3PqZRaqtxO2', updated_at = CURRENT_TIMESTAMP
FROM public.staff_members AS staff
WHERE account.username = 'local.demo'
  AND account.account_type = 'STAFF'
  AND account.staff_member_id = staff.id
  AND staff.staff_code = 'LOCAL_DEMO_STAFF';

INSERT INTO public.accounts (account_type, username, password_hash, staff_member_id, status)
SELECT 'STAFF', 'admin', '{bcrypt}$2a$10$jdFKPVm/lEHGPgMB5h5FZujxEMPq7ahPlqCuvWMVQy3PqZRaqtxO2', staff.id, 'ACTIVE'
FROM public.staff_members AS staff
WHERE staff.staff_code = 'LOCAL_DEMO_STAFF'
  AND staff.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM public.accounts WHERE username = 'admin')
ON CONFLICT (username) DO NOTHING;

INSERT INTO public.account_roles (account_id, role_id, granted_by)
SELECT account.id, role.id, account.id
FROM public.accounts AS account
JOIN public.staff_members AS staff
  ON staff.id = account.staff_member_id AND staff.staff_code = 'LOCAL_DEMO_STAFF'
JOIN public.roles AS role ON role.code = 'ADMIN' AND role.active
WHERE account.username = 'admin'
  AND account.account_type = 'STAFF'
  AND account.status = 'ACTIVE'
ON CONFLICT (account_id, role_id) DO NOTHING;

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission
  ON permission.code IN ('HEALTH_EXAMINATION_PARTICIPANT_READ', 'HEALTH_EXAMINATION_PARTICIPANT_IMPORT')
WHERE role.code IN ('ADMIN', 'CLINIC_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
