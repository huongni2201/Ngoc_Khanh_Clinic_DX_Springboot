-- Local-only Flyway callback (db/local is loaded only by application-local.yaml): runs after every
-- migrate, so the TEST role also receives permissions that a later versioned migration adds to an
-- existing local database. The repeatable seed would not re-run for them. Every other role keeps
-- only its V002 grants. Idempotent; accounts must sign in again to use new grants.
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
CROSS JOIN public.permissions AS permission
WHERE role.code IN ('TEST')
ON CONFLICT (role_id, permission_id) DO NOTHING;
