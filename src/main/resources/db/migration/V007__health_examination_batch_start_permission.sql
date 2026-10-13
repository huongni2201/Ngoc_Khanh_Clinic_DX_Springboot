-- Starting a health examination batch (UC-071, DRAFT to READY). The permission is seeded by V002
-- without an endpoint; a permission protects exactly one endpoint (ADR-0015). CLINIC_MANAGER
-- already holds it (V002). Examination forms are printed only for READY/FINALIZED batches, so this
-- endpoint completes the printing workflow (plan 20261012-examination-form-printing).

UPDATE public.permissions
SET http_method = 'POST',
    endpoint = '/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/start',
    name = 'Start examination batch',
    description = 'Health Examination Batch. UC-071. Moves a DRAFT batch to READY. Restricted: Clinic Manager footnote 7.'
WHERE code = 'HEALTH_EXAMINATION_BATCH_START'
  AND http_method IS NULL
  AND endpoint IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.permissions
        WHERE code = 'HEALTH_EXAMINATION_BATCH_START'
          AND http_method = 'POST'
          AND endpoint = '/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/start'
    ) THEN
        RAISE EXCEPTION 'HEALTH_EXAMINATION_BATCH_START must exist and have no endpoint before V007';
    END IF;
END
$$;
