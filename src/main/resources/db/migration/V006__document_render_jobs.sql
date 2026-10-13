-- Bulk printing (plan 20261012-examination-form-printing, D11-D14). A render job renders many
-- documents in the background and keeps the merged PDFs as temporary files that expire. Jobs hold
-- identifiers only, never the personal data of the people printed.

CREATE TABLE public.document_render_jobs (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    document_type varchar(100) NOT NULL,
    requested_by uuid NOT NULL REFERENCES public.accounts (id) ON DELETE RESTRICT,
    idempotency_key uuid NOT NULL,
    -- Free-form identifiers the owning module needs to load the records (organization, batch ...).
    context jsonb NOT NULL,
    source_ids uuid[] NOT NULL,
    status varchar(20) NOT NULL,
    total_count integer NOT NULL,
    processed_count integer NOT NULL DEFAULT 0,
    -- [{"id": "...", "reason": "CANCELLED"}]: records left out of the output, with the reason.
    skipped jsonb NOT NULL DEFAULT '[]'::jsonb,
    error_code varchar(50) NULL,
    template_version_id uuid NULL REFERENCES public.template_versions (id) ON DELETE RESTRICT,
    attempt_count integer NOT NULL DEFAULT 0,
    lock_token uuid NULL,
    locked_until timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at timestamptz(3) NULL,
    finished_at timestamptz(3) NULL,
    expires_at timestamptz(3) NULL,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_render_jobs_idempotency UNIQUE (requested_by, idempotency_key),
    CONSTRAINT ck_document_render_jobs_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_document_render_jobs_total CHECK (total_count BETWEEN 1 AND 5000),
    CONSTRAINT ck_document_render_jobs_ids CHECK (cardinality(source_ids) = total_count),
    CONSTRAINT ck_document_render_jobs_progress CHECK (processed_count BETWEEN 0 AND total_count),
    CONSTRAINT ck_document_render_jobs_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_document_render_jobs_lease CHECK (
        (status = 'RUNNING' AND lock_token IS NOT NULL AND locked_until IS NOT NULL)
        OR
        (status <> 'RUNNING' AND lock_token IS NULL AND locked_until IS NULL)
    ),
    CONSTRAINT ck_document_render_jobs_finished CHECK (
        (status IN ('SUCCEEDED', 'FAILED', 'EXPIRED')) = (finished_at IS NOT NULL AND expires_at IS NOT NULL)
    )
);

-- Claiming looks for queued jobs and for running jobs whose lease ran out.
CREATE INDEX idx_document_render_jobs_claim
    ON public.document_render_jobs (status, locked_until, created_at);
CREATE INDEX idx_document_render_jobs_expiry
    ON public.document_render_jobs (expires_at) WHERE status IN ('SUCCEEDED', 'FAILED');
CREATE INDEX idx_document_render_jobs_requester
    ON public.document_render_jobs (requested_by, status);

-- One merged PDF per part of a job (at most a fixed number of records each).
CREATE TABLE public.document_render_job_outputs (
    job_id uuid NOT NULL REFERENCES public.document_render_jobs (id) ON DELETE CASCADE,
    part_no integer NOT NULL,
    file_id uuid NOT NULL UNIQUE REFERENCES public.files (id) ON DELETE RESTRICT,
    record_count integer NOT NULL,
    PRIMARY KEY (job_id, part_no),
    CONSTRAINT ck_document_render_job_outputs_part CHECK (part_no > 0),
    CONSTRAINT ck_document_render_job_outputs_count CHECK (record_count > 0)
);

-- Three endpoints, three permissions (ADR-0015): create the job, read it, download a part.
INSERT INTO public.permissions (code, name, description, http_method, endpoint)
VALUES
    ('PARTICIPANT_EXAMINATION_FORM_BULK_PRINT', 'Print many examination forms',
     'Participant Examination Form. UC-086. Creates a background print job for the chosen Participants of a batch.',
     'POST',
     '/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/examination-form-print-jobs'),
    ('DOCUMENT_RENDER_JOB_READ', 'View a print job',
     'Document render job. Progress, skipped records and parts of a job the caller created.',
     'GET',
     '/api/v1/document-render-jobs/{jobId}'),
    ('DOCUMENT_RENDER_JOB_DOWNLOAD', 'Download a print job part',
     'Document render job. Downloads one merged PDF part of a job the caller created.',
     'GET',
     '/api/v1/document-render-jobs/{jobId}/parts/{partNo}')
ON CONFLICT (code) DO NOTHING;

-- Same roles as single printing (open question Q5 of the plan).
INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission
    ON permission.code IN ('PARTICIPANT_EXAMINATION_FORM_BULK_PRINT', 'DOCUMENT_RENDER_JOB_READ', 'DOCUMENT_RENDER_JOB_DOWNLOAD')
WHERE role.code IN ('CLINIC_MANAGER', 'DATA_ENTRY_STAFF', 'RECEPTIONIST')
ON CONFLICT (role_id, permission_id) DO NOTHING;
