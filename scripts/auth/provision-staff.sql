-- Operator template only. These PREPARE statements do not provision any account by themselves.
-- Execute using bound parameters in an approved client; never substitute plaintext passwords.
-- Allocate new row IDs using the application's UUIDv7 generator.
-- Use an explicit DB transaction, check affected row counts, and commit only the intended changes.
-- For existing users, revoke sessions through SessionRevocation after COMMIT.

PREPARE set_staff_credentials(uuid, varchar, text) AS
UPDATE public.users
SET username = $2, password = $3
WHERE id = $1 AND principal_type = 'STAFF' AND status = 'ACTIVE'
  AND EXISTS (SELECT 1 FROM public.staff s WHERE s.id = users.staff_id AND s.is_active);

-- $1 new user UUIDv7; $2 existing staff ID; $3 trimmed username; $4 encoded hash.
-- Prefer updating the existing staff account instead of creating a second identity.
PREPARE create_staff_user(uuid, uuid, varchar, text) AS
INSERT INTO public.users (id, principal_type, staff_id, status, created_at, username, password)
SELECT $1, 'STAFF', s.id, 'ACTIVE', CURRENT_TIMESTAMP, $3, $4
FROM public.staff s WHERE s.id = $2 AND s.is_active;

-- $1 assignment UUIDv7; $2 user; $3 existing role; $4 department or NULL; $5 room or NULL;
-- $6 valid_from; $7 valid_to or NULL. Validate the intended department/room scope before binding.
PREPARE assign_existing_staff_role(uuid, uuid, uuid, uuid, uuid, timestamptz, timestamptz) AS
INSERT INTO public.user_roles (id, user_id, role_id, department_id, room_id, valid_from, valid_to)
SELECT $1, u.id, r.id, $4, $5, $6, $7
FROM public.users u JOIN public.roles r ON r.id = $3 AND r.is_active
WHERE u.id = $2 AND u.principal_type = 'STAFF' AND u.status = 'ACTIVE'
  AND ($7 IS NULL OR $7 > $6);
