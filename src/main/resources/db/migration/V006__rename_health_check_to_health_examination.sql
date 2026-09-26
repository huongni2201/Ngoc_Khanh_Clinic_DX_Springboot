/*
   PostgreSQL 17 terminology cut-over for the Health Examination context.
   This is part of the fresh-install migration chain and keeps existing rows.
*/

ALTER TABLE public.health_check_batches RENAME TO health_examination_batches;
ALTER TABLE public.health_check_batch_services RENAME TO health_examination_batch_services;
ALTER TABLE public.health_check_batch_employees RENAME TO health_examination_batch_employees;
ALTER TABLE public.health_check_batch_employee_services RENAME TO health_examination_batch_employee_services;
ALTER TABLE public.health_check_records RENAME TO health_examination_records;
ALTER TABLE public.health_check_import_jobs RENAME TO health_examination_import_jobs;
ALTER TABLE public.health_check_import_rows RENAME TO health_examination_import_rows;

ALTER TABLE public.services RENAME COLUMN health_check_eligible TO health_examination_eligible;
ALTER TABLE public.document_templates RENAME COLUMN is_master_health_check_form TO is_master_health_examination_form;
ALTER TABLE public.appointments RENAME COLUMN doctor_staff_id TO physician_staff_id;
ALTER TABLE public.encounter_assignments RENAME COLUMN doctor_staff_id TO physician_staff_id;
ALTER TABLE public.order_rounds RENAME COLUMN health_check_record_id TO health_examination_record_id;
ALTER TABLE public.audit_logs RENAME COLUMN health_check_record_id TO health_examination_record_id;
ALTER TABLE public.health_examination_batch_services RENAME COLUMN health_check_batch_id TO health_examination_batch_id;
ALTER TABLE public.health_examination_batch_employees RENAME COLUMN health_check_batch_id TO health_examination_batch_id;
ALTER TABLE public.health_examination_records RENAME COLUMN health_check_batch_employee_id TO health_examination_batch_employee_id;
ALTER TABLE public.health_examination_records RENAME COLUMN health_check_reason_snapshot TO health_examination_reason_snapshot;
ALTER TABLE public.health_examination_records RENAME COLUMN replaces_health_check_record_id TO replaces_health_examination_record_id;
ALTER TABLE public.health_examination_batch_employee_services RENAME COLUMN health_check_batch_employee_id TO health_examination_batch_employee_id;
ALTER TABLE public.health_examination_batch_employee_services RENAME COLUMN health_check_batch_service_id TO health_examination_batch_service_id;
ALTER TABLE public.health_examination_import_jobs RENAME COLUMN health_check_batch_id TO health_examination_batch_id;
ALTER TABLE public.health_examination_import_rows RENAME COLUMN health_check_import_job_id TO health_examination_import_job_id;
ALTER TRIGGER trg_health_check_records_row_version ON public.health_examination_records
    RENAME TO trg_health_examination_records_row_version;

-- Rename constraints and their backing indexes without rebuilding them.
DO $$
DECLARE
    item record;
    renamed_name text;
BEGIN
    FOR item IN
        SELECT conrelid AS table_oid, conname
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND conname LIKE '%health_check%'
    LOOP
        renamed_name := replace(item.conname, 'health_check', 'health_examination');
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
        SELECT idx.indexrelid, tbl.relname AS table_name, idxrel.relname AS index_name
        FROM pg_index idx
        JOIN pg_class tbl ON tbl.oid = idx.indrelid
        JOIN pg_class idxrel ON idxrel.oid = idx.indexrelid
        JOIN pg_namespace ns ON ns.oid = idxrel.relnamespace
        WHERE ns.nspname = 'public'
          AND idxrel.relname LIKE '%health_check%'
    LOOP
        renamed_name := replace(item.index_name, 'health_check', 'health_examination');
        IF length(renamed_name) > 63 THEN
            renamed_name := left(renamed_name, 54) || '_' || substr(md5(renamed_name), 1, 8);
        END IF;
        IF to_regclass(format('public.%I', renamed_name)) IS NULL THEN
            EXECUTE format('ALTER INDEX public.%I RENAME TO %I', item.index_name, renamed_name);
        END IF;
    END LOOP;
END;
$$;

ALTER INDEX public.ix_hcbes_employee
    RENAME TO ix_health_examination_batch_employee_services_employee;
ALTER INDEX public.ix_hcbes_service
    RENAME TO ix_health_examination_batch_employee_services_service;
ALTER INDEX public.ux_hcbes_service_request
    RENAME TO ux_health_examination_batch_employee_services_request;
