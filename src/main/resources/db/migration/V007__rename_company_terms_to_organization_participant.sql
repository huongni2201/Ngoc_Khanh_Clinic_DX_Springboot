/*
   PostgreSQL 17 cut-over to canonical organization/participant terminology.
   This is part of the fresh-install chain and preserves rows and references.
*/

ALTER TABLE public.companies RENAME TO organizations;
ALTER TABLE public.company_employees RENAME TO health_examination_participants;
ALTER TABLE public.health_examination_batch_employees RENAME TO health_examination_batch_participants;
ALTER TABLE public.health_examination_batch_employee_services RENAME TO health_examination_batch_participant_services;

ALTER TABLE public.organizations RENAME COLUMN company_code TO organization_code;
ALTER TABLE public.organizations RENAME COLUMN company_name TO organization_name;
ALTER TABLE public.health_examination_participants RENAME COLUMN company_id TO organization_id;
ALTER TABLE public.health_examination_participants RENAME COLUMN employee_code TO participant_code;
ALTER TABLE public.health_examination_batches RENAME COLUMN company_id TO organization_id;
ALTER TABLE public.health_examination_batch_participants RENAME COLUMN company_employee_id TO health_examination_participant_id;
ALTER TABLE public.health_examination_batch_participants RENAME COLUMN employee_code_snapshot TO participant_code_snapshot;
ALTER TABLE public.health_examination_batch_participant_services RENAME COLUMN health_examination_batch_employee_id TO health_examination_batch_participant_id;
ALTER TABLE public.health_examination_records RENAME COLUMN health_examination_batch_employee_id TO health_examination_batch_participant_id;
ALTER TABLE public.health_examination_import_rows RENAME COLUMN employee_code_snapshot TO participant_code_snapshot;
ALTER TABLE public.health_examination_import_rows RENAME COLUMN resolved_company_employee_id TO resolved_health_examination_participant_id;
ALTER TABLE public.health_examination_import_rows RENAME COLUMN resolved_batch_employee_id TO resolved_batch_participant_id;
ALTER TRIGGER trg_companies_row_version ON public.organizations RENAME TO trg_organizations_row_version;

-- Keep database object names aligned with the renamed tables and columns.
DO $$
DECLARE
    item record;
    renamed_name text;
BEGIN
    FOR item IN
        SELECT conrelid AS table_oid, conname
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND (conname LIKE '%company%' OR conname LIKE '%employee%')
    LOOP
        renamed_name := replace(
            replace(
                replace(
                    replace(
                        replace(
                            replace(item.conname,
                                'health_examination_batch_employee_services', 'health_examination_batch_participant_services'),
                            'health_examination_batch_employees', 'health_examination_batch_participants'),
                        'company_employees', 'health_examination_participants'),
                    'company_employee', 'health_examination_participant'),
                'company', 'organization'),
            'employee', 'participant');
        IF length(renamed_name) > 63 THEN
            renamed_name := left(renamed_name, 54) || '_' || substr(md5(renamed_name), 1, 8);
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = item.table_oid AND conname = renamed_name
        ) THEN
            EXECUTE format(
                'ALTER TABLE %s RENAME CONSTRAINT %I TO %I',
                item.table_oid::regclass, item.conname, renamed_name);
        END IF;
    END LOOP;

    FOR item IN
        SELECT idx.indexrelid, idxrel.relname AS index_name
        FROM pg_index idx
        JOIN pg_class idxrel ON idxrel.oid = idx.indexrelid
        JOIN pg_namespace ns ON ns.oid = idxrel.relnamespace
        WHERE ns.nspname = 'public'
          AND (idxrel.relname LIKE '%company%' OR idxrel.relname LIKE '%employee%')
    LOOP
        renamed_name := replace(
            replace(
                replace(
                    replace(
                        replace(
                            replace(item.index_name,
                                'health_examination_batch_employee_services', 'health_examination_batch_participant_services'),
                            'health_examination_batch_employees', 'health_examination_batch_participants'),
                        'company_employees', 'health_examination_participants'),
                    'company_employee', 'health_examination_participant'),
                'company', 'organization'),
            'employee', 'participant');
        IF length(renamed_name) > 63 THEN
            renamed_name := left(renamed_name, 54) || '_' || substr(md5(renamed_name), 1, 8);
        END IF;
        IF to_regclass(format('public.%I', renamed_name)) IS NULL THEN
            EXECUTE format('ALTER INDEX public.%I RENAME TO %I', item.index_name, renamed_name);
        END IF;
    END LOOP;
END;
$$;
