-- V001 is used only to initialize a new, not-yet-deployed PostgreSQL database.
-- Keep the guard so this cut-over never discards Journey history if seed data is
-- introduced before this migration is applied.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.journey_events) OR EXISTS (SELECT 1 FROM public.journeys) THEN
        RAISE EXCEPTION 'Legacy Journey data must be reviewed before the v2.11 cut-over';
    END IF;
END;
$$;

DROP TABLE public.journey_events;
DROP TABLE public.journeys;

ALTER TABLE public.patients RENAME COLUMN identification_number TO cccd;
ALTER TABLE public.company_employees RENAME COLUMN identification_number TO cccd;
ALTER TABLE public.health_check_import_rows RENAME COLUMN identification_number_snapshot TO cccd_snapshot;
ALTER TABLE public.health_check_records RENAME COLUMN identification_number_snapshot TO cccd_snapshot;
ALTER TABLE public.health_check_records RENAME COLUMN identification_number_issue_date_snapshot TO cccd_issue_date_snapshot;
ALTER TABLE public.health_check_records RENAME COLUMN identification_number_issue_place_snapshot TO cccd_issue_place_snapshot;

ALTER INDEX public.ux_patients_identification_number RENAME TO ux_patients_cccd;
ALTER TABLE public.company_employees
    RENAME CONSTRAINT uq_company_employees_company_id_identification_number
    TO uq_company_employees_company_id_cccd;
