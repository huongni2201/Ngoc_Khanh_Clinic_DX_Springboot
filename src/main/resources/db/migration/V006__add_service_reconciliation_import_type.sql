-- Import type of the examination detail (service reconciliation) Excel import.
-- It only widens the allowed import types; existing import jobs and their rows are untouched.
--   HEALTH_EXAMINATION_SERVICE_RECONCILIATION  staff marks the performed services of Participants
-- HEALTH_EXAMINATION_RESULT stays reserved for clinical results and is not reused.

ALTER TABLE public.import_jobs DROP CONSTRAINT ck_import_jobs_type;

ALTER TABLE public.import_jobs ADD CONSTRAINT ck_import_jobs_type CHECK (
    import_type IN (
        'ORGANIZATION_PARTICIPANT',
        'HEALTH_EXAMINATION_RESULT',
        'HEALTH_EXAMINATION_SERVICE_RECONCILIATION'
    )
);
