-- Local-only repeatable access-control seed (loaded from application-local.yaml).
-- Creates one local-only account for each role in V002; every username is its lowercase role code.
-- These synthetic accounts all use the local development password 123456.

INSERT INTO public.staff_members (staff_code, full_name, status)
VALUES
    ('LOCAL_DEMO_STAFF', 'Local Administrator', 'ACTIVE'),
    ('LOCAL_ROLE_RECEPTIONIST', 'Local Receptionist', 'ACTIVE'),
    ('LOCAL_ROLE_GP', 'Local General Practitioner', 'ACTIVE'),
    ('LOCAL_ROLE_DIAGNOSTIC_DOCTOR', 'Local Diagnostic Doctor', 'ACTIVE'),
    ('LOCAL_ROLE_DATA_ENTRY_STAFF', 'Local Data Entry Staff', 'ACTIVE'),
    ('LOCAL_ROLE_CLINIC_MANAGER', 'Local Clinic Manager', 'ACTIVE')
ON CONFLICT (staff_code) DO NOTHING;

INSERT INTO public.patients (patient_code, full_name, date_of_birth, sex, identification_number, status)
VALUES ('LOCAL_ROLE_PATIENT', 'Local Patient', DATE '2000-01-01', 'OTHER', 'LOCAL-ROLE-PATIENT', 'ACTIVE')
ON CONFLICT (patient_code) DO NOTHING;

UPDATE public.accounts AS account
SET username = 'administrator', password_hash = '{bcrypt}$2a$10$Ul1wCsTw6f/gx29NKy0nXuYPlHGzLKxEg9fZpUDlkQKt/29jSEcIq', updated_at = CURRENT_TIMESTAMP
FROM public.staff_members AS staff
WHERE account.username IN ('admin', 'local.demo')
  AND account.account_type = 'STAFF'
  AND account.staff_member_id = staff.id
  AND staff.staff_code = 'LOCAL_DEMO_STAFF'
  AND NOT EXISTS (
      SELECT 1 FROM public.accounts AS existing
      WHERE existing.username = 'administrator' AND existing.id <> account.id
  );

WITH role_accounts(role_code, username, staff_code) AS (
    VALUES
        ('ADMINISTRATOR', 'administrator', 'LOCAL_DEMO_STAFF'),
        ('RECEPTIONIST', 'receptionist', 'LOCAL_ROLE_RECEPTIONIST'),
        ('GENERAL_PRACTITIONER', 'general_practitioner', 'LOCAL_ROLE_GP'),
        ('DIAGNOSTIC_DOCTOR', 'diagnostic_doctor', 'LOCAL_ROLE_DIAGNOSTIC_DOCTOR'),
        ('DATA_ENTRY_STAFF', 'data_entry_staff', 'LOCAL_ROLE_DATA_ENTRY_STAFF'),
        ('CLINIC_MANAGER', 'clinic_manager', 'LOCAL_ROLE_CLINIC_MANAGER')
)
INSERT INTO public.accounts AS existing
    (account_type, username, password_hash, staff_member_id, status)
SELECT 'STAFF', role_accounts.username,
       '{bcrypt}$2a$10$Ul1wCsTw6f/gx29NKy0nXuYPlHGzLKxEg9fZpUDlkQKt/29jSEcIq', staff.id, 'ACTIVE'
FROM role_accounts
JOIN public.staff_members AS staff ON staff.staff_code = role_accounts.staff_code AND staff.status = 'ACTIVE'
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash, updated_at = CURRENT_TIMESTAMP
WHERE existing.account_type = EXCLUDED.account_type
  AND existing.staff_member_id = EXCLUDED.staff_member_id
  AND existing.password_hash IS DISTINCT FROM EXCLUDED.password_hash;

INSERT INTO public.accounts AS existing
    (account_type, username, password_hash, patient_id, status)
SELECT 'PATIENT', 'patient',
       '{bcrypt}$2a$10$Ul1wCsTw6f/gx29NKy0nXuYPlHGzLKxEg9fZpUDlkQKt/29jSEcIq', patient.id, 'ACTIVE'
FROM public.patients AS patient
WHERE patient.patient_code = 'LOCAL_ROLE_PATIENT' AND patient.status = 'ACTIVE'
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash, updated_at = CURRENT_TIMESTAMP
WHERE existing.account_type = EXCLUDED.account_type
  AND existing.patient_id = EXCLUDED.patient_id
  AND existing.password_hash IS DISTINCT FROM EXCLUDED.password_hash;

WITH role_accounts(role_code, username, staff_code) AS (
    VALUES
        ('ADMINISTRATOR', 'administrator', 'LOCAL_DEMO_STAFF'),
        ('RECEPTIONIST', 'receptionist', 'LOCAL_ROLE_RECEPTIONIST'),
        ('GENERAL_PRACTITIONER', 'general_practitioner', 'LOCAL_ROLE_GP'),
        ('DIAGNOSTIC_DOCTOR', 'diagnostic_doctor', 'LOCAL_ROLE_DIAGNOSTIC_DOCTOR'),
        ('DATA_ENTRY_STAFF', 'data_entry_staff', 'LOCAL_ROLE_DATA_ENTRY_STAFF'),
        ('CLINIC_MANAGER', 'clinic_manager', 'LOCAL_ROLE_CLINIC_MANAGER')
)
INSERT INTO public.account_roles (account_id, role_id, granted_by)
SELECT account.id, role.id, account.id
FROM role_accounts
JOIN public.staff_members AS staff ON staff.staff_code = role_accounts.staff_code
JOIN public.accounts AS account ON account.username = role_accounts.username
    AND account.account_type = 'STAFF' AND account.staff_member_id = staff.id AND account.status = 'ACTIVE'
JOIN public.roles AS role ON role.code = role_accounts.role_code AND role.active
ON CONFLICT (account_id, role_id) DO NOTHING;

-- The PATIENT role is granted to the local patient account by the local administrator (granted_by must
-- be a STAFF account, see trg_validate_account_role).
INSERT INTO public.account_roles (account_id, role_id, granted_by)
SELECT account.id, role.id, grantor.id
FROM public.accounts AS account
JOIN public.patients AS patient ON patient.id = account.patient_id
JOIN public.roles AS role ON role.code = 'PATIENT' AND role.active
JOIN public.accounts AS grantor ON grantor.username = 'administrator' AND grantor.account_type = 'STAFF'
WHERE account.username = 'patient' AND account.account_type = 'PATIENT' AND account.status = 'ACTIVE'
  AND patient.patient_code = 'LOCAL_ROLE_PATIENT'
ON CONFLICT (account_id, role_id) DO NOTHING;

-- Local only: the ADMINISTRATOR holds every permission to ease manual testing of all endpoints.
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
CROSS JOIN public.permissions AS permission
WHERE role.code = 'ADMINISTRATOR'
ON CONFLICT (role_id, permission_id) DO NOTHING;
