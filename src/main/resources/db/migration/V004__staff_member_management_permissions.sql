-- Staff member management (docs/api/staff-members.md; ADR-0015). An Administrator lists staff members,
-- reads the assignable roles and creates a staff member together with its STAFF account.
-- A permission protects exactly one endpoint. USER_ACCOUNT_CREATE (UC-005) is seeded by V002 without an
-- endpoint and already granted to ADMINISTRATOR; the two read permissions are new.
-- Accounts must sign in again to receive the permissions.

INSERT INTO public.permissions (code, name, description)
VALUES
    ('STAFF_MEMBER_VIEW', 'View staff members', 'User Account. Lists staff members with their accounts and roles. Not in the SRS Permission Matrix 4.4 yet.'),
    ('ROLE_VIEW', 'View assignable roles', 'Roles and Permissions. Lists the active roles that may be granted to a staff account. Not in the SRS Permission Matrix 4.4 yet.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission ON permission.code IN ('STAFF_MEMBER_VIEW', 'ROLE_VIEW')
WHERE role.code = 'ADMINISTRATOR'
ON CONFLICT (role_id, permission_id) DO NOTHING;

UPDATE public.permissions AS permission
SET http_method = endpoint_map.http_method, endpoint = endpoint_map.endpoint
FROM (
    VALUES
        ('STAFF_MEMBER_VIEW', 'GET', '/api/v1/staff-members'),
        ('USER_ACCOUNT_CREATE', 'POST', '/api/v1/staff-members'),
        ('ROLE_VIEW', 'GET', '/api/v1/roles')
) AS endpoint_map(code, http_method, endpoint)
WHERE permission.code = endpoint_map.code
  AND permission.http_method IS NULL
  AND permission.endpoint IS NULL;

DO $$
BEGIN
    IF (SELECT count(*) FROM public.permissions
        WHERE (code, http_method, endpoint) IN (
            ('STAFF_MEMBER_VIEW', 'GET', '/api/v1/staff-members'),
            ('USER_ACCOUNT_CREATE', 'POST', '/api/v1/staff-members'),
            ('ROLE_VIEW', 'GET', '/api/v1/roles'))) <> 3 THEN
        RAISE EXCEPTION 'Staff member permissions must exist without another endpoint before V004';
    END IF;
END
$$;
