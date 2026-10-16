-- Accounts by role (docs/api/roles-and-permissions.md): the Role List counts the accounts holding each
-- role, and every patient portal account holds PATIENT, so the count must not scan the whole table. The
-- index also serves the foreign key check when a role row changes.
CREATE INDEX ix_accounts_role_id ON public.accounts (role_id);
