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

-- $1 new account UUIDv7; $2 existing staff member ID; $3 trimmed username;
-- $4 encoded hash. The staff member has at most one account.
PREPARE create_staff_account(uuid, uuid, varchar, text) AS
INSERT INTO public.accounts (id, account_type, staff_member_id, status, username, password_hash)
SELECT $1, 'STAFF', s.id, 'ACTIVE', $3, $4
FROM public.staff_members s WHERE s.id = $2 AND s.status = 'ACTIVE';

-- Roles and their permissions are seeded by V002 (ADR-0015); look up $2 with
-- SELECT id FROM public.roles WHERE code = 'CLINIC_MANAGER'.
-- $1 staff account; $2 existing active role; $3 granting account.
-- Grants use the (account_id, role_id) primary key, with no location/time scopes.
PREPARE grant_existing_staff_role(uuid, uuid, uuid) AS
INSERT INTO public.account_roles (account_id, role_id, granted_by)
SELECT a.id, r.id, grantor.id
FROM public.accounts a JOIN public.roles r ON r.id = $2 AND r.active
JOIN public.accounts grantor ON grantor.id = $3 AND grantor.status = 'ACTIVE'
WHERE a.id = $1 AND a.account_type = 'STAFF' AND a.status = 'ACTIVE'
  AND EXISTS (SELECT 1 FROM public.staff_members s
              WHERE s.id = a.staff_member_id AND s.status = 'ACTIVE');
