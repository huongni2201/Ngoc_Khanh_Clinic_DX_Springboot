-- Local-only repeatable access-control seed (loaded from application-local.yaml).
-- Roles, permissions and their grants come from V004/V005 (ADR-0015). The demo staff account
-- `admin` receives the CLINIC_MANAGER and ADMINISTRATOR roles so that local runs can use the
-- organization, batch, participant and catalog screens; it grants them to itself.

INSERT INTO public.staff_members (staff_code, full_name, status)
VALUES ('LOCAL_DEMO_STAFF', 'Local Demo Staff', 'ACTIVE')
ON CONFLICT (staff_code) DO NOTHING;

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

-- Earlier versions of this seed created the local-only roles ADMIN and USER.
DELETE FROM public.account_roles
WHERE role_id IN (SELECT id FROM public.roles WHERE code IN ('ADMIN', 'USER'));

DELETE FROM public.role_permissions
WHERE role_id IN (SELECT id FROM public.roles WHERE code IN ('ADMIN', 'USER'));

DELETE FROM public.roles WHERE code IN ('ADMIN', 'USER');

INSERT INTO public.account_roles (account_id, role_id, granted_by)
SELECT account.id, role.id, account.id
FROM public.accounts AS account
JOIN public.staff_members AS staff
  ON staff.id = account.staff_member_id AND staff.staff_code = 'LOCAL_DEMO_STAFF'
JOIN public.roles AS role ON role.code IN ('CLINIC_MANAGER', 'ADMINISTRATOR') AND role.active
WHERE account.username = 'admin'
  AND account.account_type = 'STAFF'
  AND account.status = 'ACTIVE'
ON CONFLICT (account_id, role_id) DO NOTHING;
