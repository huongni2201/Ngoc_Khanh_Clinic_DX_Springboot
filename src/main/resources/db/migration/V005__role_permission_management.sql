-- Role and role permission administration (SRS UC-008 Manage Roles, UC-009 Manage Role Permissions;
-- docs/api/roles-and-permissions.md; ADR-0015). An Administrator lists, creates, updates, activates and
-- deactivates roles, and grants or removes the predefined permissions of a role. Permissions are never
-- created at runtime: each one protects exactly one endpoint and is seeded by a migration.
-- Accounts must sign in again to receive the permissions.

-- Roles: optimistic locking for concurrent edits (UC-008/UC-009 5-EX-02), optional description (SRS 1.3.2),
-- case-insensitive unique name (BR-11) and the role code format of SRS 1.3.2.
ALTER TABLE public.roles
    ADD COLUMN row_version bigint NOT NULL DEFAULT 0;

ALTER TABLE public.roles ALTER COLUMN description DROP NOT NULL;

ALTER TABLE public.roles
    ADD CONSTRAINT ck_roles_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$');

CREATE UNIQUE INDEX uq_roles_name_lower ON public.roles (lower(name));

-- Permission catalogue shown on the Role Permissions screen (SRS 1.3.4): module group, related use cases
-- and the "(sensitive)" label.
ALTER TABLE public.permissions
    ADD COLUMN module varchar(80) NULL,
    ADD COLUMN use_cases varchar(200) NULL,
    ADD COLUMN sensitive boolean NOT NULL DEFAULT false;

-- Seeded descriptions start with "<Module>. "; the permissions added for endpoints without a matrix row
-- are mapped explicitly.
UPDATE public.permissions
SET module = CASE
        WHEN code IN ('PARTICIPANT_CREATE', 'PARTICIPANT_REACTIVATE') THEN 'Participant'
        WHEN code IN (
            'HEALTH_EXAMINATION_SERVICE_READ',
            'HEALTH_EXAMINATION_SERVICE_RECONCILE',
            'HEALTH_EXAMINATION_SERVICE_TEMPLATE_DOWNLOAD',
            'HEALTH_EXAMINATION_SERVICE_RECORD',
            'HEALTH_EXAMINATION_SERVICE_SUMMARY_READ',
            'HEALTH_EXAMINATION_SERVICE_EXPORT',
            'HEALTH_EXAMINATION_REPORT_READ',
            'HEALTH_EXAMINATION_REPORT_EXPORT') THEN 'Batch Examination Data'
        WHEN code IN ('DOCUMENT_RENDER_JOB_READ', 'DOCUMENT_RENDER_JOB_DOWNLOAD') THEN 'Participant Examination Form'
        ELSE split_part(description, '.', 1)
    END,
    use_cases = NULLIF(
        array_to_string(ARRAY(SELECT (regexp_matches(description, 'UC-[0-9]{3}', 'g'))[1]), ', '),
        '');

-- Sensitive permissions (stakeholder confirmation pending, SRS UC-009 assumption): account and role
-- administration, refunds, amendments of completed records and national data submission.
UPDATE public.permissions
SET sensitive = true
WHERE code IN (
        'USER_ACCOUNT_CREATE',
        'USER_ACCOUNT_STATUS_UPDATE',
        'USER_ROLE_ASSIGN',
        'ROLE_MANAGE',
        'ROLE_PERMISSION_ASSIGN',
        'PAYMENT_REFUND',
        'ENCOUNTER_AMEND',
        'DIAGNOSTIC_RESULT_AMEND',
        'NATIONAL_SUBMISSION_SUBMIT',
        'NATIONAL_SUBMISSION_RETRY');

INSERT INTO public.permissions (code, name, description, module, use_cases, sensitive)
VALUES
    ('ROLE_CREATE', 'Create a role', 'Roles and Permissions. UC-008. Not in the SRS Permission Matrix 4.4 yet.', 'Roles and Permissions', 'UC-008', true),
    ('ROLE_UPDATE', 'Update, activate or deactivate a role', 'Roles and Permissions. UC-008. Not in the SRS Permission Matrix 4.4 yet.', 'Roles and Permissions', 'UC-008', true),
    ('PERMISSION_VIEW', 'View the permission catalogue', 'Roles and Permissions. UC-009. Not in the SRS Permission Matrix 4.4 yet.', 'Roles and Permissions', 'UC-009', false),
    ('ROLE_PERMISSION_VIEW', 'View the permissions of a role', 'Roles and Permissions. UC-009. Not in the SRS Permission Matrix 4.4 yet.', 'Roles and Permissions', 'UC-009', false)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE public.permissions ALTER COLUMN module SET NOT NULL;

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission
    ON permission.code IN ('ROLE_CREATE', 'ROLE_UPDATE', 'PERMISSION_VIEW', 'ROLE_PERMISSION_VIEW')
WHERE role.code = 'ADMINISTRATOR'
ON CONFLICT (role_id, permission_id) DO NOTHING;

UPDATE public.permissions AS permission
SET http_method = endpoint_map.http_method, endpoint = endpoint_map.endpoint
FROM (
    VALUES
        ('ROLE_MANAGE', 'GET', '/api/v1/admin/roles'),
        ('ROLE_CREATE', 'POST', '/api/v1/admin/roles'),
        ('ROLE_UPDATE', 'PUT', '/api/v1/admin/roles/{roleId}'),
        ('PERMISSION_VIEW', 'GET', '/api/v1/admin/permissions'),
        ('ROLE_PERMISSION_VIEW', 'GET', '/api/v1/admin/roles/{roleId}/permissions'),
        ('ROLE_PERMISSION_ASSIGN', 'PUT', '/api/v1/admin/roles/{roleId}/permissions')
) AS endpoint_map(code, http_method, endpoint)
WHERE permission.code = endpoint_map.code
  AND permission.http_method IS NULL
  AND permission.endpoint IS NULL;

DO $$
BEGIN
    IF (SELECT count(*) FROM public.permissions
        WHERE (code, http_method, endpoint) IN (
            ('ROLE_MANAGE', 'GET', '/api/v1/admin/roles'),
            ('ROLE_CREATE', 'POST', '/api/v1/admin/roles'),
            ('ROLE_UPDATE', 'PUT', '/api/v1/admin/roles/{roleId}'),
            ('PERMISSION_VIEW', 'GET', '/api/v1/admin/permissions'),
            ('ROLE_PERMISSION_VIEW', 'GET', '/api/v1/admin/roles/{roleId}/permissions'),
            ('ROLE_PERMISSION_ASSIGN', 'PUT', '/api/v1/admin/roles/{roleId}/permissions'))) <> 6 THEN
        RAISE EXCEPTION 'Role administration permissions must exist without another endpoint before V005';
    END IF;
END
$$;
