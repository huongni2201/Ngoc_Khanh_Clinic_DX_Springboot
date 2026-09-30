ALTER TABLE public.health_examination_batches DROP CONSTRAINT ck_health_examination_batches_status;
ALTER TABLE public.health_examination_batches ADD CONSTRAINT ck_health_examination_batches_status
CHECK (status IN ('DRAFT','READY','IN_PROGRESS','RESULT_PROCESSING','FINALIZED','CLOSED','CANCELED','DELETED'));
ALTER TABLE public.health_examination_batch_services
DROP CONSTRAINT ck_health_examination_batch_services_base_price_nonnegative;
ALTER TABLE public.health_examination_batch_services DROP COLUMN base_price_snapshot;
