-- Accounts by role (docs/api/roles-and-permissions.md): the Role List counts the accounts holding each
-- role, and every patient portal account holds PATIENT, so the count must not scan the whole table. The
-- index also serves the foreign key check when a role row changes.
-- Not CONCURRENTLY: Flyway holds a transactional advisory lock on PostgreSQL, and a concurrent build waits
-- for that transaction forever. Where accounts is already large, build the same index CONCURRENTLY before
-- deploying; IF NOT EXISTS then makes this migration a no-op.
CREATE INDEX IF NOT EXISTS ix_accounts_role_id ON public.accounts (role_id);
