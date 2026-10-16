-- Change the role of a staff account (SRS UC-010 Change User Role; docs/api/staff-members.md; ADR-0015).
-- An account holds exactly one role, so the role is replaced, never added or removed. USER_ROLE_ASSIGN
-- had no endpoint of its own (POST /api/v1/staff-members also requires it); it now protects the role
-- change route. Accounts receive the new role at their next sign-in.
UPDATE public.permissions
SET name = 'Change the role of a user account',
    http_method = 'PUT',
    endpoint = '/api/v1/accounts/{accountId}/role'
WHERE code = 'USER_ROLE_ASSIGN'
  AND http_method IS NULL
  AND endpoint IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.permissions
        WHERE code = 'USER_ROLE_ASSIGN'
          AND http_method = 'PUT'
          AND endpoint = '/api/v1/accounts/{accountId}/role') THEN
        RAISE EXCEPTION 'USER_ROLE_ASSIGN must exist without another endpoint before V007';
    END IF;
END
$$;
