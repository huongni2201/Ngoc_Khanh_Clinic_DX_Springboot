-- Operator template only. PREPARE does not provision any account by itself.
-- Use bound parameters in an approved client; never bind plaintext passwords.
-- Allocate IDs using the application's UUIDv7 generator.
-- Use an explicit transaction, check affected rows, and commit only intended changes.
-- Sessions keep the roles captured at sign-in; end existing sessions of a changed
-- account through the application's SessionStore.revokeAll after COMMIT.
-- Password hashes must come from UserPasswordEncoder (NFKC, bcrypt, {bcrypt} prefix).

PREPARE set_staff_credentials(uuid, varchar, text) AS
UPDATE public.accounts
SET username = $2, password_hash = $3, updated_at = CURRENT_TIMESTAMP
WHERE id = $1 AND account_type = 'STAFF' AND status = 'ACTIVE'
  AND EXISTS (SELECT 1 FROM public.staff_members s
              WHERE s.id = accounts.staff_member_id AND s.status = 'ACTIVE');

-- Roles and their permissions are seeded by V002 (ADR-0015); look up $3 with
-- SELECT id FROM public.roles WHERE code = 'CLINIC_MANAGER'.
-- $1 new account UUIDv7; $2 existing staff member ID; $3 existing active staff role;
-- $4 trimmed username; $5 encoded hash. The staff member has at most one account and an account
-- holds exactly one role, with no location/time scopes.
PREPARE create_staff_account(uuid, uuid, uuid, varchar, text) AS
INSERT INTO public.accounts (id, account_type, role_id, staff_member_id, status, username, password_hash)
SELECT $1, 'STAFF', r.id, s.id, 'ACTIVE', $4, $5
FROM public.staff_members s JOIN public.roles r ON r.id = $3 AND r.active
WHERE s.id = $2 AND s.status = 'ACTIVE';

-- $1 staff account; $2 existing active staff role that replaces the current one.
PREPARE change_staff_role(uuid, uuid) AS
UPDATE public.accounts a
SET role_id = r.id, updated_at = CURRENT_TIMESTAMP
FROM public.roles r
WHERE r.id = $2 AND r.active AND a.id = $1 AND a.account_type = 'STAFF' AND a.status = 'ACTIVE'
  AND EXISTS (SELECT 1 FROM public.staff_members s
              WHERE s.id = a.staff_member_id AND s.status = 'ACTIVE');
