-- Examination form printing permissions (plan 20261012-examination-form-printing, D15; ADR-0015).
-- A permission protects at most one endpoint, so PARTICIPANT_EXAMINATION_FORM_PRINT is bound to the
-- endpoint that prints one Participant's form. Bulk printing gets its own permission when it is built.

UPDATE public.permissions
SET http_method = 'GET',
    endpoint = '/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/{participantId}/examination-form',
    name = 'Print one examination form',
    description = 'Participant Examination Form. UC-085. Restricted: Data Entry Staff footnote 6. Bulk printing (UC-086) has its own permission.'
WHERE code = 'PARTICIPANT_EXAMINATION_FORM_PRINT'
  AND http_method IS NULL
  AND endpoint IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.permissions
        WHERE code = 'PARTICIPANT_EXAMINATION_FORM_PRINT' AND http_method = 'GET'
    ) THEN
        RAISE EXCEPTION 'PARTICIPANT_EXAMINATION_FORM_PRINT must exist and have no endpoint before V005';
    END IF;
END
$$;

-- DATA_ENTRY_STAFF already holds the permission (V002). Open question Q5 of the plan proposes the
-- clinic manager and the receptionist as well.
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission ON permission.code = 'PARTICIPANT_EXAMINATION_FORM_PRINT'
WHERE role.code IN ('CLINIC_MANAGER', 'RECEPTIONIST')
ON CONFLICT (role_id, permission_id) DO NOTHING;
