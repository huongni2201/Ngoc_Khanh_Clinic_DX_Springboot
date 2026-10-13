-- Renames the DATA_ENTRY_STAFF role to NURSE so the role matches the SRS actor "Nurse".
-- Grants are kept: role_permissions and account_roles reference the role id, not its code.
-- Sessions created before this migration keep the old role code until the user signs in again.

UPDATE public.roles
SET code = 'NURSE',
    name = 'Nurse',
    description = 'Prepares participant examination records, enters batch examination data and issues completed records.'
WHERE code = 'DATA_ENTRY_STAFF';

-- Permission descriptions name the role in their SRS footnote reference.
UPDATE public.permissions
SET description = replace(description, 'Data Entry Staff', 'Nurse')
WHERE description LIKE '%Data Entry Staff%';
