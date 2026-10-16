-- Corporate examination: Agreement, Session and Assignment replace the Batch
-- (docs/plans/20261016-corporate-examination-registration-scheduling-and-results.md, ADR-0019).
--
-- * A Batch becomes an Agreement: services, negotiated prices and validity. The examination site and
--   the examination days leave the header.
-- * An examination day becomes a Session (date, site, activity). A Participant belongs to an Agreement
--   and is scheduled through any number of session assignments; attendance moves from the Participant
--   to the assignment.
-- * Tables are renamed, not copied, so identifiers, foreign keys and audit history survive. Existing
--   rows are converted: every batch day becomes a session (days of the same organization, date and
--   site share one session) and every Participant gets one assignment for its former day.
--
-- Applied V001-V006 are not edited. Every trigger function that named a batch table or column is
-- replaced at the end of the schema part.

-- ============================================================================
-- 1. Rename tables and columns
-- ============================================================================

ALTER TABLE public.health_examination_batches RENAME TO health_examination_agreements;
ALTER TABLE public.health_examination_batch_services RENAME TO health_examination_agreement_services;
ALTER TABLE public.health_examination_batch_participants RENAME TO health_examination_participants;

ALTER TABLE public.health_examination_agreements RENAME COLUMN batch_code TO agreement_code;
ALTER TABLE public.health_examination_agreement_services RENAME COLUMN batch_id TO agreement_id;
ALTER TABLE public.health_examination_participants RENAME COLUMN batch_id TO agreement_id;
ALTER TABLE public.health_examination_participant_services RENAME COLUMN batch_id TO agreement_id;
ALTER TABLE public.health_examination_participant_services RENAME COLUMN batch_participant_id TO participant_id;
ALTER TABLE public.health_examination_participant_services RENAME COLUMN batch_service_id TO agreement_service_id;
ALTER TABLE public.health_examination_records RENAME COLUMN batch_participant_id TO participant_id;
ALTER TABLE public.import_jobs RENAME COLUMN batch_id TO agreement_id;

-- The old validation trigger names batch columns; it is replaced at the end of this migration.
DROP TRIGGER trg_health_exam_batch_participants_validate ON public.health_examination_participants;

-- ============================================================================
-- 2. Agreement header
-- ============================================================================

ALTER TABLE public.health_examination_agreements
    ADD COLUMN valid_from date NULL,
    ADD COLUMN valid_to date NULL;

UPDATE public.health_examination_agreements AS a
SET valid_from = d.first_date, valid_to = d.last_date
FROM (
    SELECT batch_id, min(examination_date) AS first_date, max(examination_date) AS last_date
    FROM public.health_examination_batch_days
    GROUP BY batch_id
) AS d
WHERE a.id = d.batch_id;

ALTER TABLE public.health_examination_agreements DROP CONSTRAINT ck_health_exam_batches_status;

UPDATE public.health_examination_agreements
SET status = CASE status
        WHEN 'READY' THEN 'ACTIVE'
        WHEN 'FINALIZED' THEN 'ENDED'
        WHEN 'CLOSED' THEN 'ENDED'
        ELSE status
    END;

ALTER TABLE public.health_examination_agreements
    ADD CONSTRAINT ck_health_exam_agreements_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ENDED')),
    ADD CONSTRAINT ck_health_exam_agreements_validity CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_from <= valid_to),
    ADD CONSTRAINT uq_health_exam_agreements_org_scope UNIQUE (organization_id, id);

-- ============================================================================
-- 3. Sessions
-- ============================================================================

CREATE TABLE public.health_examination_sessions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    organization_id uuid NOT NULL REFERENCES public.organizations(id) ON DELETE RESTRICT,
    agreement_id uuid NULL,
    session_date date NOT NULL,
    activity varchar(20) NOT NULL,
    site_type varchar(30) NOT NULL,
    site_address text NOT NULL DEFAULT '',
    status varchar(20) NOT NULL DEFAULT 'OPEN',
    created_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_health_exam_sessions_org_scope UNIQUE (organization_id, id),
    CONSTRAINT fk_health_exam_sessions_agreement
        FOREIGN KEY (organization_id, agreement_id)
        REFERENCES public.health_examination_agreements(organization_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_health_exam_sessions_activity CHECK (activity IN ('ALL', 'SAMPLING', 'EXAMINATION')),
    CONSTRAINT ck_health_exam_sessions_site_type CHECK (site_type IN ('CLINIC', 'ORGANIZATION_SITE')),
    CONSTRAINT ck_health_exam_sessions_status CHECK (status IN ('OPEN', 'CANCELLED')),
    CONSTRAINT ck_health_exam_sessions_site_address CHECK (site_type <> 'ORGANIZATION_SITE' OR site_address ~ '\S')
);

-- One open session per organization, date, site and activity; the crew decides how many people go in it.
CREATE UNIQUE INDEX ux_health_exam_sessions_open_slot
    ON public.health_examination_sessions(organization_id, session_date, site_type, site_address, activity)
    WHERE status = 'OPEN';
CREATE INDEX ix_health_exam_sessions_organization_date
    ON public.health_examination_sessions(organization_id, session_date DESC, id);
CREATE INDEX ix_health_exam_sessions_agreement
    ON public.health_examination_sessions(agreement_id) WHERE agreement_id IS NOT NULL;

-- Every batch day of a live batch becomes a session. Days of the same organization, date and site share
-- the session with the lowest day id; the session belongs to an agreement only when one agreement uses it.
CREATE TEMPORARY TABLE tmp_session_slot ON COMMIT DROP AS
SELECT a.organization_id,
       d.examination_date AS session_date,
       a.examination_site_type AS site_type,
       a.examination_site_address AS site_address,
       (array_agg(d.id ORDER BY d.id))[1] AS session_id,
       CASE WHEN count(DISTINCT a.id) = 1 THEN (array_agg(a.id))[1] END AS agreement_id,
       (array_agg(a.created_by ORDER BY d.id))[1] AS created_by,
       min(a.created_at) AS created_at
FROM public.health_examination_batch_days d
JOIN public.health_examination_agreements a ON a.id = d.batch_id
WHERE a.deleted_at IS NULL
GROUP BY a.organization_id, d.examination_date, a.examination_site_type, a.examination_site_address;

INSERT INTO public.health_examination_sessions
    (id, organization_id, agreement_id, session_date, activity, site_type, site_address, status, created_by, created_at, updated_at)
SELECT session_id, organization_id, agreement_id, session_date, 'ALL', site_type, site_address, 'OPEN', created_by, created_at, created_at
FROM tmp_session_slot;

CREATE TEMPORARY TABLE tmp_day_session ON COMMIT DROP AS
SELECT d.id AS day_id, s.session_id
FROM public.health_examination_batch_days d
JOIN public.health_examination_agreements a ON a.id = d.batch_id
JOIN tmp_session_slot s
    ON s.organization_id = a.organization_id
   AND s.session_date = d.examination_date
   AND s.site_type = a.examination_site_type
   AND s.site_address = a.examination_site_address;

-- ============================================================================
-- 4. Participants belong to an agreement; attendance leaves the Participant
-- ============================================================================

ALTER TABLE public.health_examination_participants ADD COLUMN organization_id uuid NULL;

UPDATE public.health_examination_participants AS p
SET organization_id = a.organization_id
FROM public.health_examination_agreements a
WHERE a.id = p.agreement_id;

ALTER TABLE public.health_examination_participants
    ALTER COLUMN organization_id SET NOT NULL,
    ADD CONSTRAINT uq_health_exam_participants_org_scope UNIQUE (organization_id, id),
    ADD CONSTRAINT fk_health_exam_participants_agreement_org
        FOREIGN KEY (organization_id, agreement_id)
        REFERENCES public.health_examination_agreements(organization_id, id)
        ON DELETE RESTRICT;

-- ============================================================================
-- 5. Session assignments
-- ============================================================================

CREATE TABLE public.health_examination_session_assignments (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    organization_id uuid NOT NULL,
    participant_id uuid NOT NULL,
    session_id uuid NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ASSIGNED',
    replaced_by_assignment_id uuid NULL REFERENCES public.health_examination_session_assignments(id) ON DELETE RESTRICT,
    attended_at timestamptz(3) NULL,
    note text NULL,
    recorded_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    recorded_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_health_exam_assignments_participant
        FOREIGN KEY (organization_id, participant_id)
        REFERENCES public.health_examination_participants(organization_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_health_exam_assignments_session
        FOREIGN KEY (organization_id, session_id)
        REFERENCES public.health_examination_sessions(organization_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_health_exam_assignments_status CHECK (status IN ('ASSIGNED', 'ATTENDED', 'NO_SHOW', 'MOVED', 'CANCELLED')),
    CONSTRAINT ck_health_exam_assignments_attended CHECK ((status = 'ATTENDED') = (attended_at IS NOT NULL)),
    CONSTRAINT ck_health_exam_assignments_moved CHECK ((status = 'MOVED') = (replaced_by_assignment_id IS NOT NULL))
);

-- A Participant is in a session at most once while the assignment is live.
CREATE UNIQUE INDEX ux_health_exam_assignments_live
    ON public.health_examination_session_assignments(participant_id, session_id)
    WHERE status IN ('ASSIGNED', 'ATTENDED');
CREATE INDEX ix_health_exam_assignments_session_status
    ON public.health_examination_session_assignments(session_id, status);
CREATE INDEX ix_health_exam_assignments_participant_status
    ON public.health_examination_session_assignments(participant_id, status);

-- Every Participant gets one assignment for the day it was planned on. Attendance keeps its meaning:
-- ATTENDED stays attended on the recorded actual date, ABSENT becomes NO_SHOW, a cancelled roster
-- entry becomes a CANCELLED assignment.
INSERT INTO public.health_examination_session_assignments
    (id, organization_id, participant_id, session_id, status, attended_at, note, recorded_by, recorded_at, created_at, updated_at)
SELECT uuidv7(),
       p.organization_id,
       p.id,
       m.session_id,
       CASE
           WHEN p.roster_status = 'CANCELLED' THEN 'CANCELLED'
           WHEN p.attendance_status = 'ATTENDED' THEN 'ATTENDED'
           WHEN p.attendance_status = 'ABSENT' THEN 'NO_SHOW'
           ELSE 'ASSIGNED'
       END,
       CASE
           WHEN p.roster_status = 'ACTIVE' AND p.attendance_status = 'ATTENDED'
               THEN p.actual_examination_date::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
       END,
       p.attendance_note,
       COALESCE(p.attendance_recorded_by, a.created_by),
       COALESCE(p.attendance_recorded_at, p.created_at),
       p.created_at,
       p.updated_at
FROM public.health_examination_participants p
JOIN public.health_examination_agreements a ON a.id = p.agreement_id
JOIN tmp_day_session m ON m.day_id = p.batch_day_id;

-- A session that belongs to one agreement only schedules Participants of that agreement.
CREATE OR REPLACE FUNCTION public.trg_validate_session_assignment_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_session_agreement uuid;
    v_participant_agreement uuid;
BEGIN
    SELECT agreement_id INTO v_session_agreement
    FROM public.health_examination_sessions
    WHERE id = NEW.session_id;

    IF v_session_agreement IS NOT NULL THEN
        SELECT agreement_id INTO v_participant_agreement
        FROM public.health_examination_participants
        WHERE id = NEW.participant_id;

        IF v_participant_agreement IS DISTINCT FROM v_session_agreement THEN
            RAISE EXCEPTION 'Session belongs to another Agreement than the Participant'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_health_exam_assignments_validate_scope
BEFORE INSERT OR UPDATE OF participant_id, session_id ON public.health_examination_session_assignments
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_session_assignment_scope();

-- ============================================================================
-- 6. Remove the batch day, site and attendance columns
-- ============================================================================

ALTER TABLE public.health_examination_participants
    DROP CONSTRAINT fk_health_exam_batch_participants_day,
    DROP CONSTRAINT ck_health_exam_batch_participants_attendance,
    DROP CONSTRAINT ck_health_exam_batch_participants_attendance_date,
    DROP CONSTRAINT ck_health_exam_batch_participants_attendance_actor;

DROP INDEX public.ix_health_exam_batch_participants_day;
DROP INDEX public.ix_health_exam_batch_participants_attendance;

ALTER TABLE public.health_examination_participants
    DROP COLUMN batch_day_id,
    DROP COLUMN attendance_status,
    DROP COLUMN actual_examination_date,
    DROP COLUMN attendance_recorded_by,
    DROP COLUMN attendance_recorded_at,
    DROP COLUMN attendance_note;

DROP TABLE public.health_examination_batch_days;

ALTER TABLE public.health_examination_agreements
    DROP CONSTRAINT ck_health_exam_batches_site_type,
    DROP CONSTRAINT health_examination_batches_org_site_address_chk,
    DROP COLUMN examination_site_type,
    DROP COLUMN examination_site_address;

-- ============================================================================
-- 7. Agreement services: a price change retires the line and adds a new one
-- ============================================================================

ALTER TABLE public.health_examination_agreement_services
    DROP CONSTRAINT uq_health_exam_batch_services_service;

CREATE UNIQUE INDEX ux_health_exam_agreement_services_active
    ON public.health_examination_agreement_services(agreement_id, service_id)
    WHERE active;

-- ============================================================================
-- 8. Import jobs and constraint names
-- ============================================================================

ALTER TABLE public.import_jobs RENAME CONSTRAINT ck_import_jobs_batch TO ck_import_jobs_agreement;
ALTER INDEX public.ix_import_jobs_batch_created RENAME TO ix_import_jobs_agreement_created;

ALTER INDEX public.health_examination_batches_pkey RENAME TO health_examination_agreements_pkey;
ALTER INDEX public.health_examination_batch_services_pkey RENAME TO health_examination_agreement_services_pkey;
ALTER INDEX public.health_examination_batch_participants_pkey RENAME TO health_examination_participants_pkey;
ALTER INDEX public.health_examination_batches_batch_code_key RENAME TO uq_health_exam_agreements_code;
ALTER TABLE public.health_examination_agreements
    RENAME CONSTRAINT health_examination_batches_name_present_chk TO ck_health_exam_agreements_name;
ALTER INDEX public.ix_health_exam_batches_organization RENAME TO ix_health_exam_agreements_organization;

ALTER TABLE public.health_examination_agreement_services
    RENAME CONSTRAINT uq_health_exam_batch_services_order TO uq_health_exam_agreement_services_order;
ALTER TABLE public.health_examination_agreement_services
    RENAME CONSTRAINT uq_health_exam_batch_services_scope TO uq_health_exam_agreement_services_scope;
ALTER TABLE public.health_examination_agreement_services
    RENAME CONSTRAINT ck_health_exam_batch_services_reference_price TO ck_health_exam_agreement_services_reference_price;
ALTER TABLE public.health_examination_agreement_services
    RENAME CONSTRAINT ck_health_exam_batch_services_negotiated_price TO ck_health_exam_agreement_services_negotiated_price;
ALTER TABLE public.health_examination_agreement_services
    RENAME CONSTRAINT ck_health_exam_batch_services_order TO ck_health_exam_agreement_services_order;

ALTER TABLE public.health_examination_participants
    RENAME CONSTRAINT uq_health_exam_batch_participants_identification TO uq_health_exam_participants_identification;
ALTER TABLE public.health_examination_participants
    RENAME CONSTRAINT uq_health_exam_batch_participants_scope TO uq_health_exam_participants_scope;
ALTER TABLE public.health_examination_participants
    RENAME CONSTRAINT ck_health_exam_batch_participants_identification TO ck_health_exam_participants_identification;
ALTER TABLE public.health_examination_participants
    RENAME CONSTRAINT ck_health_exam_batch_participants_roster TO ck_health_exam_participants_roster;
ALTER TABLE public.health_examination_participants
    RENAME CONSTRAINT ck_health_exam_batch_participants_reconciliation TO ck_health_exam_participants_reconciliation;
ALTER TABLE public.health_examination_participants
    RENAME CONSTRAINT ck_health_exam_batch_participants_reconciled_actor TO ck_health_exam_participants_reconciled_actor;
ALTER TABLE public.health_examination_participants
    RENAME CONSTRAINT ck_health_exam_batch_participants_import_source TO ck_health_exam_participants_import_source;
ALTER TABLE public.health_examination_participants
    RENAME CONSTRAINT ck_health_exam_batch_participants_prepared TO ck_health_exam_participants_prepared;
ALTER TABLE public.health_examination_participants
    RENAME CONSTRAINT fk_health_exam_batch_participants_import_job TO fk_health_exam_participants_import_job;
ALTER INDEX public.ix_health_exam_batch_participants_reconciliation RENAME TO ix_health_exam_participants_reconciliation;
ALTER INDEX public.ix_health_exam_batch_participants_patient RENAME TO ix_health_exam_participants_patient;

-- ============================================================================
-- 9. Trigger functions
-- ============================================================================


CREATE OR REPLACE FUNCTION public.trg_validate_health_exam_participant()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_job_type text;
    v_job_agreement uuid;
    v_job_status text;
BEGIN
    IF NEW.import_job_id IS NOT NULL THEN
        SELECT import_type, agreement_id, status INTO v_job_type, v_job_agreement, v_job_status
        FROM public.import_jobs
        WHERE id = NEW.import_job_id;

        IF v_job_type IS DISTINCT FROM 'ORGANIZATION_PARTICIPANT'
           OR v_job_agreement IS DISTINCT FROM NEW.agreement_id
           OR v_job_status NOT IN ('VALIDATED', 'CONFIRMED') THEN
            RAISE EXCEPTION 'Participant import_job_id must reference ORGANIZATION_PARTICIPANT job in the same Agreement'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    -- If a record already exists, changing patient_id must preserve record Encounter ownership.
    IF NEW.patient_id IS NOT NULL AND EXISTS (
        SELECT 1
        FROM public.health_examination_records r
        JOIN public.encounters e ON e.id = r.encounter_id
        WHERE r.participant_id = NEW.id
          AND r.status <> 'CANCELLED'
          AND e.patient_id <> NEW.patient_id
    ) THEN
        RAISE EXCEPTION 'Participant patient_id would break existing HealthExaminationRecord ownership'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;


CREATE OR REPLACE FUNCTION public.trg_validate_health_exam_record_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_participant_patient uuid;
    v_encounter_patient uuid;
BEGIN
    IF NEW.participant_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT patient_id INTO v_participant_patient
    FROM public.health_examination_participants
    WHERE id = NEW.participant_id;

    SELECT patient_id INTO v_encounter_patient
    FROM public.encounters
    WHERE id = NEW.encounter_id;

    IF v_participant_patient IS NULL OR v_encounter_patient IS NULL OR v_participant_patient <> v_encounter_patient THEN
        RAISE EXCEPTION 'Corporate HealthExaminationRecord must use the Participant Patient/Encounter'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;


CREATE OR REPLACE FUNCTION public.trg_validate_participant_service_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_agreement_service_id uuid;
    v_catalog_service_id uuid;
    v_participant_patient uuid;
    v_request_service uuid;
    v_request_encounter uuid;
    v_request_patient uuid;
BEGIN
    SELECT bs.service_id INTO v_catalog_service_id
    FROM public.health_examination_agreement_services bs
    WHERE bs.agreement_id = NEW.agreement_id AND bs.id = NEW.agreement_service_id;

    IF v_catalog_service_id IS NULL THEN
        RAISE EXCEPTION 'AgreementService does not belong to ParticipantService agreement' USING ERRCODE = '23514';
    END IF;

    IF NEW.service_request_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT be.patient_id INTO v_participant_patient
    FROM public.health_examination_participants be
    WHERE be.agreement_id = NEW.agreement_id AND be.id = NEW.participant_id;

    IF v_participant_patient IS NULL THEN
        RAISE EXCEPTION 'Cannot link ServiceRequest before Participant has a prepared Patient'
            USING ERRCODE = '23514';
    END IF;

    SELECT sr.service_id, o.encounter_id, e.patient_id
    INTO v_request_service, v_request_encounter, v_request_patient
    FROM public.service_requests sr
    JOIN public.order_rounds o ON o.id = sr.order_round_id
    JOIN public.encounters e ON e.id = o.encounter_id
    WHERE sr.id = NEW.service_request_id;

    IF v_request_service IS NULL
       OR v_request_service <> v_catalog_service_id
       OR v_request_patient <> v_participant_patient THEN
        RAISE EXCEPTION 'ParticipantService ServiceRequest belongs to another service/Patient'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM public.health_examination_records r
        WHERE r.participant_id = NEW.participant_id
          AND r.encounter_id = v_request_encounter
          AND r.status <> 'CANCELLED'
    ) THEN
        RAISE EXCEPTION 'ParticipantService ServiceRequest is not in the active HealthExaminationRecord Encounter'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;


CREATE OR REPLACE FUNCTION public.trg_validate_record_version_item()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_record_id uuid;
    v_record_encounter uuid;
    v_record_participant uuid;
    v_item_service uuid;
    v_request_encounter uuid;
    v_request_service uuid;
    v_assessment_encounter uuid;
    v_assessment_status text;
    v_result_encounter uuid;
    v_result_service uuid;
    v_result_status text;
BEGIN
    SELECT rv.health_examination_record_id, r.encounter_id, r.participant_id
    INTO v_record_id, v_record_encounter, v_record_participant
    FROM public.health_examination_record_versions rv
    JOIN public.health_examination_records r ON r.id = rv.health_examination_record_id
    WHERE rv.id = NEW.record_version_id;

    IF NEW.participant_service_id IS NOT NULL THEN
        IF v_record_participant IS NULL THEN
            RAISE EXCEPTION 'Individual Health Examination Record cannot reference ParticipantService' USING ERRCODE = '23514';
        END IF;

        SELECT bs.service_id INTO v_item_service
        FROM public.health_examination_participant_services es
        JOIN public.health_examination_agreement_services bs ON bs.id = es.agreement_service_id
        WHERE es.id = NEW.participant_service_id
          AND es.participant_id = v_record_participant
          AND es.is_performed = true;

        IF v_item_service IS NULL OR v_item_service <> NEW.service_id THEN
            RAISE EXCEPTION 'RecordVersionItem ParticipantService is not performed or service does not match'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NEW.service_request_id IS NOT NULL THEN
        SELECT o.encounter_id, sr.service_id INTO v_request_encounter, v_request_service
        FROM public.service_requests sr
        JOIN public.order_rounds o ON o.id = sr.order_round_id
        WHERE sr.id = NEW.service_request_id;

        IF v_request_encounter <> v_record_encounter OR v_request_service <> NEW.service_id THEN
            RAISE EXCEPTION 'RecordVersionItem ServiceRequest must match record Encounter/service'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NEW.assessment_version_id IS NOT NULL THEN
        SELECT a.encounter_id, av.status INTO v_assessment_encounter, v_assessment_status
        FROM public.encounter_assessment_versions av
        JOIN public.encounter_assessments a ON a.id = av.encounter_assessment_id
        WHERE av.id = NEW.assessment_version_id;

        IF v_assessment_encounter <> v_record_encounter THEN
            RAISE EXCEPTION 'AssessmentVersion belongs to another Encounter' USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NEW.result_version_id IS NOT NULL THEN
        SELECT o.encounter_id, sr.service_id, rv.status
        INTO v_result_encounter, v_result_service, v_result_status
        FROM public.result_versions rv
        JOIN public.result_series rs ON rs.id = rv.result_series_id
        JOIN public.service_requests sr ON sr.id = rs.service_request_id
        JOIN public.order_rounds o ON o.id = sr.order_round_id
        WHERE rv.id = NEW.result_version_id;

        IF v_result_encounter <> v_record_encounter OR v_result_service <> NEW.service_id THEN
            RAISE EXCEPTION 'ResultVersion belongs to another Encounter/service' USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;


CREATE OR REPLACE FUNCTION public.trg_validate_record_version_official()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_snapshot_status text;
    v_participant_id uuid;
    v_reconciliation text;
BEGIN
    IF NEW.corrects_version_id IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1
            FROM public.health_examination_record_versions prev
            WHERE prev.health_examination_record_id = NEW.health_examination_record_id
              AND prev.id = NEW.corrects_version_id
              AND prev.status IN ('COMPLETED', 'ISSUED')
              AND prev.version_no < NEW.version_no
        ) THEN
            RAISE EXCEPTION 'Record amendment must reference an earlier official version in the same Record'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NEW.status IN ('COMPLETED', 'ISSUED') THEN
        SELECT status INTO v_snapshot_status
        FROM public.health_examination_record_snapshots
        WHERE id = NEW.administrative_snapshot_id
          AND health_examination_record_id = NEW.health_examination_record_id;

        IF v_snapshot_status IS DISTINCT FROM 'ISSUED' THEN
            RAISE EXCEPTION 'Official RecordVersion requires an ISSUED administrative snapshot'
                USING ERRCODE = '23514';
        END IF;

        SELECT participant_id INTO v_participant_id
        FROM public.health_examination_records
        WHERE id = NEW.health_examination_record_id;

        IF v_participant_id IS NOT NULL THEN
            SELECT service_reconciliation_status INTO v_reconciliation
            FROM public.health_examination_participants
            WHERE id = v_participant_id;

            IF v_reconciliation IS DISTINCT FROM 'RECONCILED' THEN
                RAISE EXCEPTION 'Corporate RecordVersion cannot complete before Participant services are RECONCILED'
                    USING ERRCODE = '23514';
            END IF;

            IF EXISTS (
                SELECT 1
                FROM public.health_examination_participant_services es
                WHERE es.participant_id = v_participant_id
                  AND es.is_performed = true
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public.health_examination_record_version_items i
                      WHERE i.record_version_id = NEW.id
                        AND i.participant_service_id = es.id
                  )
            ) THEN
                RAISE EXCEPTION 'Official RecordVersion is missing a performed ParticipantService item'
                    USING ERRCODE = '23514';
            END IF;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.health_examination_record_version_items i
            LEFT JOIN public.encounter_assessment_versions av ON av.id = i.assessment_version_id
            LEFT JOIN public.result_versions rv ON rv.id = i.result_version_id
            WHERE i.record_version_id = NEW.id
              AND (
                  (i.assessment_version_id IS NOT NULL AND av.status <> 'FINAL')
                  OR (i.result_version_id IS NOT NULL AND rv.status NOT IN ('FINAL', 'CORRECTED'))
              )
        ) THEN
            RAISE EXCEPTION 'Official RecordVersion pins a non-official clinical source'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;



CREATE TRIGGER trg_health_exam_participants_validate
BEFORE INSERT OR UPDATE ON public.health_examination_participants
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_health_exam_participant();

DROP FUNCTION public.trg_validate_batch_participant();

-- ============================================================================
-- 10. Permissions and endpoints
-- ============================================================================

UPDATE public.permissions
SET code = 'HEALTH_EXAMINATION_AGREEMENT_' || substr(code, char_length('HEALTH_EXAMINATION_BATCH_') + 1)
WHERE starts_with(code, 'HEALTH_EXAMINATION_BATCH_');

UPDATE public.permissions SET code = 'HEALTH_EXAMINATION_AGREEMENT_ACTIVATE' WHERE code = 'HEALTH_EXAMINATION_AGREEMENT_START';
UPDATE public.permissions SET code = 'HEALTH_EXAMINATION_AGREEMENT_END' WHERE code = 'HEALTH_EXAMINATION_AGREEMENT_CLOSE';

UPDATE public.permissions
SET endpoint = replace(replace(endpoint, '/health-examination-batches', '/agreements'), '{batchId}', '{agreementId}')
WHERE endpoint LIKE '%/health-examination-batches%';

UPDATE public.permissions
SET name = replace(replace(name, 'batches', 'agreements'), 'batch', 'agreement'),
    description = replace(replace(replace(description, 'Health Examination Batch', 'Health Examination Agreement'), 'batches', 'agreements'), 'batch', 'agreement'),
    module = CASE WHEN module = 'Health Examination Batch' THEN 'Health Examination Agreement' ELSE module END
WHERE code LIKE 'HEALTH\_EXAMINATION\_%' OR code LIKE 'PARTICIPANT\_%';

UPDATE public.permissions
SET name = 'Activate examination agreement',
    description = 'Health Examination Agreement. UC-071. Moves a DRAFT agreement to ACTIVE. Restricted: Clinic Manager footnote 7.',
    http_method = 'POST',
    endpoint = '/api/v1/organizations/{organizationId}/agreements/{agreementId}/activate'
WHERE code = 'HEALTH_EXAMINATION_AGREEMENT_ACTIVATE';

-- UC-072 (close) and UC-074 (configure services) had no endpoint; they are bound now.
UPDATE public.permissions
SET name = 'End examination agreement',
    description = 'Health Examination Agreement. UC-072. Moves an ACTIVE agreement to ENDED. Restricted: Clinic Manager footnote 7.',
    http_method = 'POST',
    endpoint = '/api/v1/organizations/{organizationId}/agreements/{agreementId}/end'
WHERE code = 'HEALTH_EXAMINATION_AGREEMENT_END' AND http_method IS NULL AND endpoint IS NULL;

UPDATE public.permissions
SET name = 'Add services to an examination agreement',
    description = 'Health Examination Agreement. UC-074. Adds services with negotiated prices to a DRAFT or ACTIVE agreement. Restricted: Clinic Manager footnote 7.',
    http_method = 'POST',
    endpoint = '/api/v1/organizations/{organizationId}/agreements/{agreementId}/services'
WHERE code = 'HEALTH_EXAMINATION_AGREEMENT_SERVICE_CONFIGURE' AND http_method IS NULL AND endpoint IS NULL;

INSERT INTO public.permissions (code, name, description, http_method, endpoint, module, use_cases, sensitive)
VALUES
    ('HEALTH_EXAMINATION_AGREEMENT_SERVICE_DEACTIVATE', 'Retire an agreement service',
     'Health Examination Agreement. Stops offering a service of an agreement; recorded prices are kept. Not in the SRS Permission Matrix 4.4 yet.',
     'POST', '/api/v1/organizations/{organizationId}/agreements/{agreementId}/services/{agreementServiceId}/deactivate',
     'Health Examination Agreement', NULL, false),
    ('HEALTH_EXAMINATION_SESSION_VIEW', 'List examination sessions',
     'Health Examination Session. Lists the sessions of an organization with the number of Participants assigned. Not in the SRS Permission Matrix 4.4 yet.',
     'GET', '/api/v1/organizations/{organizationId}/sessions', 'Health Examination Session', NULL, false),
    ('HEALTH_EXAMINATION_SESSION_CREATE', 'Create an examination session',
     'Health Examination Session. Creates a session (date, site and activity). Not in the SRS Permission Matrix 4.4 yet.',
     'POST', '/api/v1/organizations/{organizationId}/sessions', 'Health Examination Session', NULL, false),
    ('HEALTH_EXAMINATION_SESSION_DETAIL_VIEW', 'View an examination session',
     'Health Examination Session. Not in the SRS Permission Matrix 4.4 yet.',
     'GET', '/api/v1/organizations/{organizationId}/sessions/{sessionId}', 'Health Examination Session', NULL, false),
    ('HEALTH_EXAMINATION_SESSION_UPDATE', 'Update an examination session',
     'Health Examination Session. Changes the date, site or activity of an open session. Not in the SRS Permission Matrix 4.4 yet.',
     'PUT', '/api/v1/organizations/{organizationId}/sessions/{sessionId}', 'Health Examination Session', NULL, false),
    ('HEALTH_EXAMINATION_SESSION_CANCEL', 'Cancel an examination session',
     'Health Examination Session. Cancels a session nobody is assigned to. Not in the SRS Permission Matrix 4.4 yet.',
     'POST', '/api/v1/organizations/{organizationId}/sessions/{sessionId}/cancel', 'Health Examination Session', NULL, false),
    ('HEALTH_EXAMINATION_ASSIGNMENT_VIEW', 'List the Participants of a session',
     'Health Examination Assignment. Not in the SRS Permission Matrix 4.4 yet.',
     'GET', '/api/v1/organizations/{organizationId}/sessions/{sessionId}/assignments', 'Health Examination Assignment', NULL, false),
    ('HEALTH_EXAMINATION_ASSIGNMENT_CREATE', 'Assign Participants to a session',
     'Health Examination Assignment. Schedules Participants into a session. Not in the SRS Permission Matrix 4.4 yet.',
     'POST', '/api/v1/organizations/{organizationId}/sessions/{sessionId}/assignments', 'Health Examination Assignment', NULL, false),
    ('HEALTH_EXAMINATION_ASSIGNMENT_MOVE_ALL', 'Move every Participant of a session to another session',
     'Health Examination Assignment. Reschedules all assigned Participants of a session; attended ones stay. Not in the SRS Permission Matrix 4.4 yet.',
     'POST', '/api/v1/organizations/{organizationId}/sessions/{sessionId}/assignments/move', 'Health Examination Assignment', NULL, false),
    ('HEALTH_EXAMINATION_ASSIGNMENT_MOVE', 'Move a Participant to another session',
     'Health Examination Assignment. Not in the SRS Permission Matrix 4.4 yet.',
     'POST', '/api/v1/organizations/{organizationId}/sessions/{sessionId}/assignments/{assignmentId}/move', 'Health Examination Assignment', NULL, false),
    ('HEALTH_EXAMINATION_ASSIGNMENT_CANCEL', 'Cancel the assignment of a Participant',
     'Health Examination Assignment. Not in the SRS Permission Matrix 4.4 yet.',
     'POST', '/api/v1/organizations/{organizationId}/sessions/{sessionId}/assignments/{assignmentId}/cancel', 'Health Examination Assignment', NULL, false),
    ('HEALTH_EXAMINATION_ASSIGNMENT_ATTENDANCE_RECORD', 'Record attendance of an assignment',
     'Health Examination Assignment. Marks a Participant attended or not shown for one session. Not in the SRS Permission Matrix 4.4 yet.',
     'POST', '/api/v1/organizations/{organizationId}/sessions/{sessionId}/assignments/{assignmentId}/attendance', 'Health Examination Assignment', NULL, false);

INSERT INTO public.role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM public.roles AS role
JOIN public.permissions AS permission
    ON permission.code IN (
        'HEALTH_EXAMINATION_AGREEMENT_SERVICE_DEACTIVATE',
        'HEALTH_EXAMINATION_SESSION_VIEW',
        'HEALTH_EXAMINATION_SESSION_CREATE',
        'HEALTH_EXAMINATION_SESSION_DETAIL_VIEW',
        'HEALTH_EXAMINATION_SESSION_UPDATE',
        'HEALTH_EXAMINATION_SESSION_CANCEL',
        'HEALTH_EXAMINATION_ASSIGNMENT_VIEW',
        'HEALTH_EXAMINATION_ASSIGNMENT_CREATE',
        'HEALTH_EXAMINATION_ASSIGNMENT_MOVE_ALL',
        'HEALTH_EXAMINATION_ASSIGNMENT_MOVE',
        'HEALTH_EXAMINATION_ASSIGNMENT_CANCEL',
        'HEALTH_EXAMINATION_ASSIGNMENT_ATTENDANCE_RECORD')
WHERE role.code = 'CLINIC_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.permissions WHERE endpoint LIKE '%health-examination-batches%' OR endpoint LIKE '%{batchId}%') THEN
        RAISE EXCEPTION 'A permission still names the removed batch routes';
    END IF;
    IF (SELECT count(*) FROM public.permissions
        WHERE code IN (
            'HEALTH_EXAMINATION_AGREEMENT_VIEW', 'HEALTH_EXAMINATION_AGREEMENT_CREATE',
            'HEALTH_EXAMINATION_AGREEMENT_UPDATE', 'HEALTH_EXAMINATION_AGREEMENT_ACTIVATE',
            'HEALTH_EXAMINATION_AGREEMENT_END', 'HEALTH_EXAMINATION_AGREEMENT_SERVICE_CONFIGURE',
            'HEALTH_EXAMINATION_AGREEMENT_DETAIL_VIEW', 'HEALTH_EXAMINATION_AGREEMENT_DELETE')
          AND endpoint IS NOT NULL) <> 8 THEN
        RAISE EXCEPTION 'Agreement permissions must each be bound to an endpoint';
    END IF;
END
$$;
