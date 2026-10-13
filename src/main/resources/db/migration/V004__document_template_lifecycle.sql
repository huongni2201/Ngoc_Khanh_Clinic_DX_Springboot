-- Document template lifecycle (plan 20261012-examination-form-printing, D5, D19, D17).
-- A template version becomes DRAFT -> PUBLISHED | DISCARDED. The clinic manager uploads a .docx, the
-- system validates it, and the manager publishes it (now or from a future date). A published
-- version never changes its file or layout; a newer version supersedes it. Starter versions are
-- seeded by the application, not by this migration.

-- Fail fast: this migration assumes no document data exists yet (nothing writes these tables
-- before this change). If data exists, it must be reviewed instead of silently backfilled.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.template_versions)
       OR EXISTS (SELECT 1 FROM public.issued_representations)
       OR EXISTS (SELECT 1 FROM public.files)
       OR EXISTS (SELECT 1 FROM public.templates) THEN
        RAISE EXCEPTION 'V004 expects empty document tables; review existing document data first';
    END IF;
END
$$;

ALTER TABLE public.templates
    ADD COLUMN is_default boolean NOT NULL DEFAULT false,
    ADD COLUMN row_version bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_document_templates_default_active CHECK (NOT is_default OR active);

-- Exactly one default template per template type; the other templates of a type are variants.
CREATE UNIQUE INDEX ux_document_templates_default
    ON public.templates (template_type) WHERE is_default;

ALTER TABLE public.files
    ADD CONSTRAINT ck_document_files_provider CHECK (storage_provider IN ('CLASSPATH', 'LOCAL', 'S3'));

ALTER TABLE public.template_versions
    ADD COLUMN status varchar(20) NOT NULL,
    ADD COLUMN contract_version integer NOT NULL,
    -- Layout read from the .docx at upload time (D19); one paper size and orientation per version.
    ADD COLUMN page_width_mm numeric(6,1) NULL,
    ADD COLUMN page_height_mm numeric(6,1) NULL,
    -- Pages one record occupies (a folded booklet form has 2); NULL when it varies.
    ADD COLUMN pages_per_record smallint NULL,
    ADD COLUMN validation_report jsonb NULL,
    ADD COLUMN preview_file_id uuid NULL REFERENCES public.files (id) ON DELETE RESTRICT,
    ADD COLUMN created_by uuid NULL REFERENCES public.accounts (id) ON DELETE RESTRICT,
    ADD COLUMN published_by uuid NULL REFERENCES public.accounts (id) ON DELETE RESTRICT,
    ADD COLUMN published_at timestamptz(3) NULL,
    ADD COLUMN note text NULL,
    ADD COLUMN row_version bigint NOT NULL DEFAULT 0,
    ALTER COLUMN active_from DROP NOT NULL,
    ADD CONSTRAINT ck_document_template_versions_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISCARDED')),
    ADD CONSTRAINT ck_document_template_versions_contract CHECK (contract_version > 0),
    -- created_by is NULL only for a version seeded by the system (starter template).
    ADD CONSTRAINT ck_document_template_versions_published
        CHECK (status <> 'PUBLISHED' OR (active_from IS NOT NULL AND published_at IS NOT NULL)),
    -- A draft has no period or publisher. A scheduled version cancelled before it took effect is
    -- DISCARDED and keeps its period and publisher as a record of what was cancelled.
    ADD CONSTRAINT ck_document_template_versions_draft_clean
        CHECK (status <> 'DRAFT'
               OR (active_from IS NULL AND retired_at IS NULL AND published_by IS NULL AND published_at IS NULL)),
    ADD CONSTRAINT ck_document_template_versions_custom_size
        CHECK (paper_size <> 'CUSTOM' OR (page_width_mm IS NOT NULL AND page_height_mm IS NOT NULL)),
    ADD CONSTRAINT ck_document_template_versions_size_positive
        CHECK ((page_width_mm IS NULL OR page_width_mm > 0) AND (page_height_mm IS NULL OR page_height_mm > 0)),
    ADD CONSTRAINT ck_document_template_versions_pages_per_record
        CHECK (pages_per_record IS NULL OR pages_per_record > 0);

-- The period check only concerns published versions; drafts have no period.
CREATE OR REPLACE FUNCTION public.trg_validate_template_version_period()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status <> 'PUBLISHED' THEN
        RETURN NEW;
    END IF;

    -- Serialize edits per logical template to prevent concurrent overlapping ranges.
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.template_id::text, 0));

    IF EXISTS (
        SELECT 1
        FROM public.template_versions tv
        WHERE tv.template_id = NEW.template_id
          AND tv.id <> NEW.id
          AND tv.status = 'PUBLISHED'
          AND tstzrange(tv.active_from, COALESCE(tv.retired_at, 'infinity'::timestamptz), '[)')
              && tstzrange(NEW.active_from, COALESCE(NEW.retired_at, 'infinity'::timestamptz), '[)')
    ) THEN
        RAISE EXCEPTION 'TemplateVersion effective periods overlap for template %', NEW.template_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER trg_document_template_versions_period ON public.template_versions;
CREATE TRIGGER trg_document_template_versions_period
BEFORE INSERT OR UPDATE OF template_id, active_from, retired_at, status ON public.template_versions
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_template_version_period();

-- A source file or preview of a template version cannot be changed or deleted while referenced.
CREATE OR REPLACE FUNCTION public.trg_protect_referenced_file()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.template_versions tv
        WHERE tv.file_id = OLD.id OR tv.preview_file_id = OLD.id
    ) THEN
        RAISE EXCEPTION 'Document file % is referenced by a TemplateVersion and cannot be changed/deleted', OLD.id
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

-- Replaces trg_protect_issued_template_version, which froze every column once a version was issued.
-- Content and layout are immutable from the first insert; only the lifecycle may move forward.
DROP TRIGGER trg_document_template_versions_immutable_when_issued ON public.template_versions;
DROP FUNCTION public.trg_protect_issued_template_version();

CREATE OR REPLACE FUNCTION public.trg_guard_template_version_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'TemplateVersion % cannot be deleted', OLD.id USING ERRCODE = '55000';
    END IF;

    IF NEW.template_id IS DISTINCT FROM OLD.template_id
       OR NEW.version_no IS DISTINCT FROM OLD.version_no
       OR NEW.file_id IS DISTINCT FROM OLD.file_id
       OR NEW.contract_version IS DISTINCT FROM OLD.contract_version
       OR NEW.paper_size IS DISTINCT FROM OLD.paper_size
       OR NEW.orientation IS DISTINCT FROM OLD.orientation
       OR NEW.render_mode IS DISTINCT FROM OLD.render_mode
       OR NEW.page_width_mm IS DISTINCT FROM OLD.page_width_mm
       OR NEW.page_height_mm IS DISTINCT FROM OLD.page_height_mm
       OR NEW.pages_per_record IS DISTINCT FROM OLD.pages_per_record
       OR NEW.created_by IS DISTINCT FROM OLD.created_by
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'TemplateVersion % content and layout are immutable', OLD.id
            USING ERRCODE = '55000';
    END IF;

    IF OLD.status = 'DISCARDED' THEN
        RAISE EXCEPTION 'TemplateVersion % is discarded and cannot change', OLD.id
            USING ERRCODE = '55000';
    END IF;

    IF OLD.status = 'PUBLISHED' THEN
        -- A version scheduled for the future can be cancelled; one already in effect is retired.
        IF NEW.status = 'DISCARDED' THEN
            IF OLD.active_from <= CURRENT_TIMESTAMP
               OR NEW.active_from IS DISTINCT FROM OLD.active_from
               OR NEW.retired_at IS DISTINCT FROM OLD.retired_at
               OR NEW.published_by IS DISTINCT FROM OLD.published_by
               OR NEW.published_at IS DISTINCT FROM OLD.published_at
               OR NEW.validation_report IS DISTINCT FROM OLD.validation_report
               OR NEW.preview_file_id IS DISTINCT FROM OLD.preview_file_id
               OR NEW.note IS DISTINCT FROM OLD.note THEN
                RAISE EXCEPTION 'Only a not yet effective TemplateVersion % can be cancelled', OLD.id
                    USING ERRCODE = '55000';
            END IF;
            RETURN NEW;
        END IF;
        IF NEW.status <> 'PUBLISHED'
           OR NEW.active_from IS DISTINCT FROM OLD.active_from
           OR NEW.published_by IS DISTINCT FROM OLD.published_by
           OR NEW.published_at IS DISTINCT FROM OLD.published_at
           OR NEW.validation_report IS DISTINCT FROM OLD.validation_report
           OR NEW.preview_file_id IS DISTINCT FROM OLD.preview_file_id
           OR NEW.note IS DISTINCT FROM OLD.note THEN
            RAISE EXCEPTION 'Published TemplateVersion % can only be retired or have its retirement moved', OLD.id
                USING ERRCODE = '55000';
        END IF;
        -- A retirement that already took effect is history.
        IF OLD.retired_at IS NOT NULL AND OLD.retired_at <= CURRENT_TIMESTAMP
           AND NEW.retired_at IS DISTINCT FROM OLD.retired_at THEN
            RAISE EXCEPTION 'TemplateVersion % was already retired and cannot change', OLD.id
                USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
    END IF;

    -- OLD.status = 'DRAFT': it may be edited, then published or discarded. The table CHECK
    -- constraints already keep a draft or discarded version free of any period or publisher.
    IF NEW.status = 'PUBLISHED' AND NEW.retired_at IS NOT NULL THEN
        RAISE EXCEPTION 'TemplateVersion % cannot be published with a retirement', OLD.id
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_document_template_versions_guard
BEFORE UPDATE OR DELETE ON public.template_versions
FOR EACH ROW EXECUTE FUNCTION public.trg_guard_template_version_mutation();
