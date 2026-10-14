-- Local-only repeatable access-control seed (loaded from application-local.yaml).
-- Creates one local-only account per seeded application role plus an all-permission TEST account;
-- an account holds exactly one role. These synthetic accounts all use the local development
-- password 123456.

INSERT INTO public.roles (code, name, description)
VALUES ('TEST', 'Local API Test', 'Local-only staff role with every permission for API testing.')
ON CONFLICT (code) DO NOTHING;

-- Local only: the TEST and ADMINISTRATOR roles hold every permission to ease manual testing of all
-- endpoints.
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
CROSS JOIN public.permissions AS permission
WHERE role.code IN ('TEST', 'ADMINISTRATOR')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO public.staff_members (staff_code, full_name, status)
VALUES
    ('LOCAL_DEMO_STAFF', 'Local Administrator', 'ACTIVE'),
    ('LOCAL_ROLE_RECEPTIONIST', 'Local Receptionist', 'ACTIVE'),
    ('LOCAL_ROLE_GP', 'Local General Practitioner', 'ACTIVE'),
    ('LOCAL_ROLE_DIAGNOSTIC_DOCTOR', 'Local Diagnostic Doctor', 'ACTIVE'),
    ('LOCAL_ROLE_DATA_ENTRY_STAFF', 'Local Data Entry Staff', 'ACTIVE'),
    ('LOCAL_ROLE_CLINIC_MANAGER', 'Local Clinic Manager', 'ACTIVE'),
    ('LOCAL_ROLE_TEST', 'Local API Test Staff', 'ACTIVE')
ON CONFLICT (staff_code) DO NOTHING;

INSERT INTO public.patients (patient_code, full_name, date_of_birth, sex, identification_number, status)
VALUES ('LOCAL_ROLE_PATIENT', 'Local Patient', DATE '2000-01-01', 'OTHER', 'LOCAL-ROLE-PATIENT', 'ACTIVE')
ON CONFLICT (patient_code) DO NOTHING;

WITH role_accounts(role_code, username, staff_code) AS (
    VALUES
        ('ADMINISTRATOR', 'administrator', 'LOCAL_DEMO_STAFF'),
        ('RECEPTIONIST', 'receptionist', 'LOCAL_ROLE_RECEPTIONIST'),
        ('GENERAL_PRACTITIONER', 'general_practitioner', 'LOCAL_ROLE_GP'),
        ('DIAGNOSTIC_DOCTOR', 'diagnostic_doctor', 'LOCAL_ROLE_DIAGNOSTIC_DOCTOR'),
        ('DATA_ENTRY_STAFF', 'data_entry_staff', 'LOCAL_ROLE_DATA_ENTRY_STAFF'),
        ('CLINIC_MANAGER', 'clinic_manager', 'LOCAL_ROLE_CLINIC_MANAGER'),
        ('TEST', 'test', 'LOCAL_ROLE_TEST')
)
INSERT INTO public.accounts AS existing
    (account_type, role_id, username, password_hash, staff_member_id, status)
SELECT 'STAFF', role.id, role_accounts.username,
       '{bcrypt}$2a$10$Ul1wCsTw6f/gx29NKy0nXuYPlHGzLKxEg9fZpUDlkQKt/29jSEcIq', staff.id, 'ACTIVE'
FROM role_accounts
JOIN public.roles AS role ON role.code = role_accounts.role_code AND role.active
JOIN public.staff_members AS staff ON staff.staff_code = role_accounts.staff_code AND staff.status = 'ACTIVE'
ON CONFLICT (username) DO UPDATE
SET role_id = EXCLUDED.role_id, password_hash = EXCLUDED.password_hash, updated_at = CURRENT_TIMESTAMP
WHERE existing.account_type = EXCLUDED.account_type
  AND existing.staff_member_id = EXCLUDED.staff_member_id
  AND (existing.role_id IS DISTINCT FROM EXCLUDED.role_id
       OR existing.password_hash IS DISTINCT FROM EXCLUDED.password_hash);

INSERT INTO public.accounts AS existing
    (account_type, role_id, username, password_hash, patient_id, status)
SELECT 'PATIENT', role.id, 'patient',
       '{bcrypt}$2a$10$Ul1wCsTw6f/gx29NKy0nXuYPlHGzLKxEg9fZpUDlkQKt/29jSEcIq', patient.id, 'ACTIVE'
FROM public.patients AS patient
JOIN public.roles AS role ON role.code = 'PATIENT' AND role.active
WHERE patient.patient_code = 'LOCAL_ROLE_PATIENT' AND patient.status = 'ACTIVE'
ON CONFLICT (username) DO UPDATE
SET role_id = EXCLUDED.role_id, password_hash = EXCLUDED.password_hash, updated_at = CURRENT_TIMESTAMP
WHERE existing.account_type = EXCLUDED.account_type
  AND existing.patient_id = EXCLUDED.patient_id
  AND (existing.role_id IS DISTINCT FROM EXCLUDED.role_id
       OR existing.password_hash IS DISTINCT FROM EXCLUDED.password_hash);
