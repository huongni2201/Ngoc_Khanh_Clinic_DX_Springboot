-- UC-005 Create User Account (ADR-0015 amendment). Maps USER_ACCOUNT_CREATE to its endpoint and adds
-- one permission per supporting read endpoint of the Create User Account screen, granted to
-- ADMINISTRATOR only. Administrators receive them at their next sign-in.

INSERT INTO public.permissions (code, name, description)
VALUES
    ('USER_ACCOUNT_OWNER_SEARCH',
     'Find account owner candidates',
     'User Account. UC-005. Active staff members or patients without a user account. Not in the SRS Permission Matrix 4.4 yet.'),
    ('USER_ACCOUNT_USERNAME_CHECK',
     'Check username availability',
     'User Account. UC-005. Not in the SRS Permission Matrix 4.4 yet.'),
    ('USER_ACCOUNT_ASSIGNABLE_ROLE_VIEW',
     'View assignable staff roles',
     'User Account. UC-005. Active staff roles offered as initial roles. Not in the SRS Permission Matrix 4.4 yet.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission
  ON permission.code IN (
      'USER_ACCOUNT_OWNER_SEARCH',
      'USER_ACCOUNT_USERNAME_CHECK',
      'USER_ACCOUNT_ASSIGNABLE_ROLE_VIEW')
WHERE role.code = 'ADMINISTRATOR'
ON CONFLICT (role_id, permission_id) DO NOTHING;

UPDATE public.permissions AS permission
SET http_method = endpoint_map.http_method, endpoint = endpoint_map.endpoint
FROM (
    VALUES
        ('USER_ACCOUNT_CREATE', 'POST', '/api/v1/user-accounts'),
        ('USER_ACCOUNT_OWNER_SEARCH', 'GET', '/api/v1/user-accounts/owner-candidates'),
        ('USER_ACCOUNT_USERNAME_CHECK', 'GET', '/api/v1/user-accounts/username-availability'),
        ('USER_ACCOUNT_ASSIGNABLE_ROLE_VIEW', 'GET', '/api/v1/user-accounts/assignable-roles')
) AS endpoint_map(code, http_method, endpoint)
WHERE permission.code = endpoint_map.code;
