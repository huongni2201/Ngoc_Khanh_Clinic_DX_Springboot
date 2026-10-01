-- The current fresh-install baseline may already contain these lookup indexes.
CREATE INDEX IF NOT EXISTS ix_health_examination_records_batch_participant_history
    ON public.health_examination_records (health_examination_batch_participant_id)
    WHERE health_examination_batch_participant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_health_examination_import_rows_batch_service
    ON public.health_examination_import_rows (resolved_batch_service_id)
    WHERE resolved_batch_service_id IS NOT NULL;
