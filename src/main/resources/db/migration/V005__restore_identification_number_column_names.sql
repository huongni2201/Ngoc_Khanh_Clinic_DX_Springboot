ALTER TABLE public.patients RENAME COLUMN cccd TO identification_number;
ALTER TABLE public.company_employees RENAME COLUMN cccd TO identification_number;
ALTER TABLE public.health_check_import_rows RENAME COLUMN cccd_snapshot TO identification_number_snapshot;
ALTER TABLE public.health_check_records RENAME COLUMN cccd_snapshot TO identification_number_snapshot;
ALTER TABLE public.health_check_records RENAME COLUMN cccd_issue_date_snapshot TO identification_number_issue_date_snapshot;
ALTER TABLE public.health_check_records RENAME COLUMN cccd_issue_place_snapshot TO identification_number_issue_place_snapshot;

ALTER INDEX public.ux_patients_cccd RENAME TO ux_patients_identification_number;
ALTER TABLE public.company_employees
    RENAME CONSTRAINT uq_company_employees_company_id_cccd
    TO uq_company_employees_company_id_identification_number;
