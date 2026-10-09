-- ============================================================================
-- NKC-DX Clean-Slate Flyway Baseline (fresh database only)
-- Target: PostgreSQL 18; one database, public application schema
-- DDL source: nkc_dx_clean_slate_postgresql18.sql
-- Source SHA-256: 351DCEF7A0A84E3F476B1646EC0FEF8EBB5AACEDC11B99769774F202C892A850
-- Design reference: NKC_DX_Clean_Slate_Database_Design_Detailed_No_Reporting_Schema.md
-- Participant terminology follows the project owner request.
-- Flyway owns the transaction; no outer BEGIN/COMMIT or database creation.
-- Consolidated on 2026-10-08: final schema, including Participant personal details,
-- optional Organization phone, batch soft delete and service reconciliation import.
-- V002 seeds access-control data; V003 seeds the catalog with final CLS<id> codes.
-- Do not apply to an existing database carrying any earlier migration history.
-- This baseline does not adapt the existing Java/MyBatis contracts to the new model.
-- Source syntax fix: include the ImportJob ID placeholder in its configuration error.
-- Generated from design dated 2026-10-03.
--
-- Conventions preserved from the design:
--   * UUIDv7 primary keys via PostgreSQL 18 uuidv7()
--   * timestamptz(3)
--   * numeric(14,2) for monetary values
--   * row_version is incremented by the application, never by a DB trigger
--   * no reporting schema / generated_documents / printer tables
--   * historical FINAL/ISSUED/COMPLETED content is protected by triggers below
--
-- Notes:
--   * Where the design names a field but omits a concrete SQL type, this file
--     uses a conservative PostgreSQL type matching the described semantics.
--   * Cross-aggregate rules that need joins are enforced by validation triggers
--     where practical. Rules requiring permissions/provider calls remain an
--     application-contract responsibility and are documented inline.
-- ============================================================================



-- ============================================================================
-- 1. CATALOG
-- ============================================================================

CREATE TABLE public.departments (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    code varchar(50) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    department_type varchar(30) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_departments_type
        CHECK (department_type IN ('CLINICAL', 'DIAGNOSTIC', 'ADMINISTRATIVE'))
);

CREATE TABLE public.rooms (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    department_id uuid NOT NULL REFERENCES public.departments(id) ON DELETE RESTRICT,
    code varchar(50) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    room_type varchar(50) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.specialties (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    code varchar(50) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.services (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    code varchar(50) NOT NULL UNIQUE,
    name varchar(300) NOT NULL,
    service_type varchar(30) NOT NULL,
    performing_department_id uuid NOT NULL REFERENCES public.departments(id) ON DELETE RESTRICT,
    specialty_id uuid NULL REFERENCES public.specialties(id) ON DELETE RESTRICT,
    unit_price numeric(14,2) NOT NULL,
    preparation_instruction text NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_services_type
        CHECK (service_type IN ('CONSULTATION', 'LAB', 'ULTRASOUND', 'XRAY', 'ECG', 'OTHER')),
    CONSTRAINT ck_services_unit_price
        CHECK (unit_price <> 'NaN'::numeric
           AND unit_price <> 'Infinity'::numeric
           AND unit_price <> '-Infinity'::numeric
           AND unit_price >= 0)
);

CREATE TABLE public.medicines (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    code varchar(50) NOT NULL UNIQUE,
    generic_name varchar(300) NOT NULL,
    brand_name varchar(300) NULL,
    strength varchar(100) NOT NULL,
    dosage_form varchar(100) NOT NULL,
    unit varchar(50) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.lab_tests (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    service_id uuid NOT NULL REFERENCES public.services(id) ON DELETE RESTRICT,
    code varchar(50) NOT NULL UNIQUE,
    name varchar(300) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.lab_analytes (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    code varchar(50) NOT NULL UNIQUE,
    name varchar(300) NOT NULL,
    default_unit varchar(100) NOT NULL,
    value_type varchar(20) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT ck_lab_analytes_value_type
        CHECK (value_type IN ('NUMERIC', 'TEXT', 'BOOLEAN'))
);

CREATE TABLE public.lab_test_analytes (
    lab_test_id uuid NOT NULL REFERENCES public.lab_tests(id) ON DELETE RESTRICT,
    analyte_id uuid NOT NULL REFERENCES public.lab_analytes(id) ON DELETE RESTRICT,
    display_order integer NOT NULL,
    required boolean NOT NULL DEFAULT false,
    PRIMARY KEY (lab_test_id, analyte_id),
    CONSTRAINT uq_lab_test_analytes_display_order UNIQUE (lab_test_id, display_order),
    CONSTRAINT ck_lab_test_analytes_display_order CHECK (display_order > 0)
);

-- ============================================================================
-- 2. PATIENT MASTER (created before public.accounts to break FK cycle)
-- ============================================================================

CREATE TABLE public.patients (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    patient_code varchar(50) NOT NULL UNIQUE,
    full_name varchar(200) NOT NULL,
    date_of_birth date NOT NULL,
    sex varchar(16) NOT NULL,
    identification_number text NOT NULL UNIQUE,
    phone text NULL,
    email text NULL,
    address text NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_patients_identification_not_blank CHECK (btrim(identification_number) <> ''),
    CONSTRAINT ck_patients_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- ============================================================================
-- 3. IDENTITY
-- ============================================================================

CREATE TABLE public.staff_members (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    staff_code varchar(30) NOT NULL UNIQUE,
    full_name varchar(200) NOT NULL,
    date_of_birth date NULL,
    sex varchar(16) NULL,
    phone varchar(30) NULL,
    email varchar(255) NULL,
    primary_department_id uuid NULL REFERENCES public.departments(id) ON DELETE RESTRICT,
    room_id uuid NULL REFERENCES public.rooms(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_staff_members_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE TABLE public.roles (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    code varchar(80) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    description text NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.permissions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    code varchar(120) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    description text NOT NULL
);

CREATE TABLE public.accounts (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    account_type varchar(20) NOT NULL,
    username varchar(150) NOT NULL UNIQUE,
    password_hash text NOT NULL,
    staff_member_id uuid NULL UNIQUE REFERENCES public.staff_members(id) ON DELETE RESTRICT,
    patient_id uuid NULL UNIQUE REFERENCES public.patients(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL,
    refresh_token_hash bytea NULL,
    refresh_token_expires_at timestamptz(3) NULL,
    refresh_token_revoked_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_accounts_type CHECK (account_type IN ('STAFF', 'PATIENT')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT ck_accounts_username_trimmed CHECK (username = btrim(username) AND username <> ''),
    CONSTRAINT ck_accounts_exact_owner CHECK (
        (account_type = 'STAFF' AND staff_member_id IS NOT NULL AND patient_id IS NULL)
        OR
        (account_type = 'PATIENT' AND patient_id IS NOT NULL AND staff_member_id IS NULL)
    ),
    CONSTRAINT ck_accounts_refresh_pair CHECK (
        (refresh_token_hash IS NULL AND refresh_token_expires_at IS NULL)
        OR
        (refresh_token_hash IS NOT NULL AND refresh_token_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_accounts_refresh_hash_length CHECK (
        refresh_token_hash IS NULL OR octet_length(refresh_token_hash) = 32
    ),
    CONSTRAINT ck_accounts_refresh_revocation CHECK (
        refresh_token_revoked_at IS NULL OR refresh_token_hash IS NOT NULL
    )
);

CREATE TABLE public.account_roles (
    account_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    role_id uuid NOT NULL REFERENCES public.roles(id) ON DELETE RESTRICT,
    granted_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    granted_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, role_id)
);

CREATE TABLE public.role_permissions (
    role_id uuid NOT NULL REFERENCES public.roles(id) ON DELETE RESTRICT,
    permission_id uuid NOT NULL REFERENCES public.permissions(id) ON DELETE RESTRICT,
    PRIMARY KEY (role_id, permission_id)
);

-- Patient longitudinal tables now that public.accounts exists.
CREATE TABLE public.patient_allergies (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    patient_id uuid NOT NULL REFERENCES public.patients(id) ON DELETE RESTRICT,
    substance text NOT NULL,
    reaction text NOT NULL,
    severity varchar(30) NULL,
    status varchar(20) NOT NULL,
    recorded_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    CONSTRAINT ck_patient_allergies_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'RESOLVED'))
);

CREATE TABLE public.patient_conditions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    patient_id uuid NOT NULL REFERENCES public.patients(id) ON DELETE RESTRICT,
    diagnosis_code varchar(30) NOT NULL,
    condition_name varchar(300) NOT NULL,
    clinical_status varchar(30) NOT NULL,
    onset_date date NOT NULL,
    resolved_date date NULL,
    note varchar(1000) NOT NULL,
    recorded_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    CONSTRAINT ck_patient_conditions_dates CHECK (resolved_date IS NULL OR resolved_date >= onset_date)
);

-- ============================================================================
-- 4. ENCOUNTER
-- appointment_id FK is added after public.appointments is created.
-- ============================================================================

CREATE TABLE public.encounters (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    patient_id uuid NOT NULL REFERENCES public.patients(id) ON DELETE RESTRICT,
    encounter_type varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    appointment_id uuid NULL UNIQUE,
    assigned_room_id uuid NULL REFERENCES public.rooms(id) ON DELETE RESTRICT,
    assigned_doctor_id uuid NULL REFERENCES public.staff_members(id) ON DELETE RESTRICT,
    checked_in_at timestamptz(3) NULL,
    started_at timestamptz(3) NULL,
    completed_at timestamptz(3) NULL,
    cancelled_at timestamptz(3) NULL,
    cancel_reason text NULL,
    created_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_encounters_type CHECK (encounter_type IN ('OUTPATIENT', 'HEALTH_EXAMINATION')),
    CONSTRAINT ck_encounters_status CHECK (status IN ('PREPARED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_encounters_lifecycle CHECK (
        (status <> 'CHECKED_IN' OR checked_in_at IS NOT NULL)
        AND (status <> 'IN_PROGRESS' OR started_at IS NOT NULL)
        AND (status <> 'COMPLETED' OR completed_at IS NOT NULL)
        AND (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
    )
);

-- ============================================================================
-- 5. CLINICAL
-- ============================================================================

CREATE TABLE public.vital_signs (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    encounter_id uuid NOT NULL REFERENCES public.encounters(id) ON DELETE RESTRICT,
    measured_at timestamptz(3) NOT NULL,
    height_cm numeric(6,2) NULL,
    weight_kg numeric(7,2) NULL,
    temperature_c numeric(4,1) NULL,
    pulse_bpm integer NULL,
    respiratory_rate integer NULL,
    systolic_bp integer NULL,
    diastolic_bp integer NULL,
    spo2 numeric(5,2) NULL,
    recorded_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT
);

CREATE TABLE public.encounter_assessments (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    encounter_id uuid NOT NULL UNIQUE REFERENCES public.encounters(id) ON DELETE RESTRICT,
    created_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE public.encounter_assessment_versions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    encounter_assessment_id uuid NOT NULL REFERENCES public.encounter_assessments(id) ON DELETE RESTRICT,
    version_no integer NOT NULL,
    status varchar(20) NOT NULL,
    corrects_version_id uuid NULL,
    correction_reason text NULL,
    chief_complaint text NULL,
    history_of_present_illness text NULL,
    past_medical_history text NULL,
    physical_examination text NULL,
    clinical_note text NULL,
    vital_sign_id uuid NULL REFERENCES public.vital_signs(id) ON DELETE RESTRICT,
    authored_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalized_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    finalized_at timestamptz(3) NULL,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_assessment_versions_no UNIQUE (encounter_assessment_id, version_no),
    CONSTRAINT uq_assessment_versions_root_id UNIQUE (encounter_assessment_id, id),
    CONSTRAINT ck_assessment_versions_no CHECK (version_no > 0),
    CONSTRAINT ck_assessment_versions_status CHECK (status IN ('DRAFT', 'FINAL')),
    CONSTRAINT ck_assessment_versions_finalized CHECK (
        (status = 'FINAL' AND finalized_by IS NOT NULL AND finalized_at IS NOT NULL)
        OR
        (status = 'DRAFT' AND finalized_by IS NULL AND finalized_at IS NULL)
    ),
    CONSTRAINT ck_assessment_versions_correction CHECK (
        (corrects_version_id IS NULL AND correction_reason IS NULL)
        OR
        (corrects_version_id IS NOT NULL AND correction_reason IS NOT NULL AND btrim(correction_reason) <> '')
    ),
    CONSTRAINT fk_assessment_versions_correction
        FOREIGN KEY (encounter_assessment_id, corrects_version_id)
        REFERENCES public.encounter_assessment_versions(encounter_assessment_id, id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_assessment_versions_one_draft
    ON public.encounter_assessment_versions(encounter_assessment_id)
    WHERE status = 'DRAFT';

CREATE TABLE public.diagnoses (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    assessment_version_id uuid NOT NULL REFERENCES public.encounter_assessment_versions(id) ON DELETE RESTRICT,
    diagnosis_code text NULL,
    diagnosis_name text NOT NULL,
    diagnosis_type varchar(20) NOT NULL,
    note text NULL,
    recorded_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    recorded_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_diagnoses_type CHECK (diagnosis_type IN ('PRELIMINARY', 'FINAL', 'SECONDARY'))
);

CREATE TABLE public.order_rounds (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    encounter_id uuid NOT NULL REFERENCES public.encounters(id) ON DELETE RESTRICT,
    round_no integer NOT NULL,
    entry_source varchar(30) NOT NULL,
    ordered_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    ordered_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status varchar(20) NOT NULL,
    note text NULL,
    CONSTRAINT uq_order_rounds_no UNIQUE (encounter_id, round_no),
    CONSTRAINT ck_order_rounds_no CHECK (round_no > 0),
    CONSTRAINT ck_order_rounds_source CHECK (entry_source IN ('DOCTOR_ORDER', 'PAPER_TRANSCRIPTION')),
    CONSTRAINT ck_order_rounds_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED'))
);

CREATE TABLE public.service_requests (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    order_round_id uuid NOT NULL REFERENCES public.order_rounds(id) ON DELETE RESTRICT,
    service_id uuid NOT NULL REFERENCES public.services(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL,
    requested_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    requested_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_department_id uuid NOT NULL REFERENCES public.departments(id) ON DELETE RESTRICT,
    assigned_room_id uuid NULL REFERENCES public.rooms(id) ON DELETE RESTRICT,
    assigned_staff_id uuid NULL REFERENCES public.staff_members(id) ON DELETE RESTRICT,
    reference_price_snapshot numeric(14,2) NOT NULL,
    unit_price_snapshot numeric(14,2) NOT NULL,
    pricing_source varchar(30) NOT NULL,
    started_at timestamptz(3) NULL,
    completed_at timestamptz(3) NULL,
    cancelled_at timestamptz(3) NULL,
    cancel_reason text NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_service_requests_status CHECK (status IN ('ORDERED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_service_requests_pricing_source CHECK (pricing_source IN ('RETAIL', 'HEALTH_EXAMINATION_BATCH', 'OTHER')),
    CONSTRAINT ck_service_requests_reference_price CHECK (
        reference_price_snapshot <> 'NaN'::numeric
        AND reference_price_snapshot <> 'Infinity'::numeric
        AND reference_price_snapshot <> '-Infinity'::numeric
        AND reference_price_snapshot >= 0
    ),
    CONSTRAINT ck_service_requests_unit_price CHECK (
        unit_price_snapshot <> 'NaN'::numeric
        AND unit_price_snapshot <> 'Infinity'::numeric
        AND unit_price_snapshot <> '-Infinity'::numeric
        AND unit_price_snapshot >= 0
    ),
    CONSTRAINT ck_service_requests_lifecycle CHECK (
        (status <> 'IN_PROGRESS' OR started_at IS NOT NULL)
        AND (status <> 'COMPLETED' OR completed_at IS NOT NULL)
        AND (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
    )
);

-- ============================================================================
-- 6. DIAGNOSTICS
-- ============================================================================

CREATE TABLE public.result_series (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    service_request_id uuid NOT NULL UNIQUE REFERENCES public.service_requests(id) ON DELETE RESTRICT,
    result_type varchar(50) NOT NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE public.result_versions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    result_series_id uuid NOT NULL REFERENCES public.result_series(id) ON DELETE RESTRICT,
    version_no integer NOT NULL,
    status varchar(20) NOT NULL,
    corrects_version_id uuid NULL,
    correction_reason text NULL,
    entry_source varchar(30) NOT NULL,
    findings text NULL,
    conclusion text NULL,
    structured_payload jsonb NULL,
    authored_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    verified_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    finalized_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at timestamptz(3) NULL,
    finalized_at timestamptz(3) NULL,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_result_versions_no UNIQUE (result_series_id, version_no),
    CONSTRAINT uq_result_versions_root_id UNIQUE (result_series_id, id),
    CONSTRAINT ck_result_versions_no CHECK (version_no > 0),
    CONSTRAINT ck_result_versions_status CHECK (status IN ('DRAFT', 'VERIFIED', 'FINAL', 'CORRECTED')),
    CONSTRAINT ck_result_versions_source CHECK (entry_source IN ('MANUAL', 'PAPER_TRANSCRIPTION', 'IMPORT', 'DEVICE')),
    CONSTRAINT ck_result_versions_verified CHECK (
        status <> 'VERIFIED' OR (verified_by IS NOT NULL AND verified_at IS NOT NULL)
    ),
    CONSTRAINT ck_result_versions_finalized CHECK (
        status NOT IN ('FINAL', 'CORRECTED') OR (finalized_by IS NOT NULL AND finalized_at IS NOT NULL)
    ),
    CONSTRAINT ck_result_versions_correction_pair CHECK (
        (corrects_version_id IS NULL AND correction_reason IS NULL)
        OR
        (corrects_version_id IS NOT NULL AND correction_reason IS NOT NULL AND btrim(correction_reason) <> '')
    ),
    CONSTRAINT ck_result_versions_corrected_requires_source CHECK (
        status <> 'CORRECTED' OR corrects_version_id IS NOT NULL
    ),
    CONSTRAINT fk_result_versions_correction
        FOREIGN KEY (result_series_id, corrects_version_id)
        REFERENCES public.result_versions(result_series_id, id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_result_versions_one_open
    ON public.result_versions(result_series_id)
    WHERE status IN ('DRAFT', 'VERIFIED');

CREATE TABLE public.lab_result_items (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    result_version_id uuid NOT NULL REFERENCES public.result_versions(id) ON DELETE RESTRICT,
    analyte_id uuid NOT NULL REFERENCES public.lab_analytes(id) ON DELETE RESTRICT,
    analyte_code_snapshot text NOT NULL,
    analyte_name_snapshot text NOT NULL,
    required_snapshot boolean NOT NULL,
    value_type varchar(20) NOT NULL,
    numeric_value numeric NULL,
    text_value text NULL,
    boolean_value boolean NULL,
    unit_snapshot text NULL,
    reference_range_snapshot text NULL,
    abnormal_flag varchar(20) NULL,
    note text NULL,
    CONSTRAINT uq_lab_result_items_analyte UNIQUE (result_version_id, analyte_id),
    CONSTRAINT ck_lab_result_items_value_type CHECK (value_type IN ('NUMERIC', 'TEXT', 'BOOLEAN')),
    CONSTRAINT ck_lab_result_items_abnormal_flag CHECK (
        abnormal_flag IS NULL OR abnormal_flag IN ('LOW', 'HIGH', 'CRITICAL', 'NORMAL')
    ),
    CONSTRAINT ck_lab_result_items_value_columns CHECK (
        (value_type = 'NUMERIC' AND text_value IS NULL AND boolean_value IS NULL)
        OR
        (value_type = 'TEXT' AND numeric_value IS NULL AND boolean_value IS NULL)
        OR
        (value_type = 'BOOLEAN' AND numeric_value IS NULL AND text_value IS NULL)
    )
);

-- ============================================================================
-- 7. BILLING
-- ============================================================================

CREATE TABLE public.invoices (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    encounter_id uuid NOT NULL REFERENCES public.encounters(id) ON DELETE RESTRICT,
    invoice_type varchar(30) NOT NULL,
    status varchar(30) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'VND',
    issued_at timestamptz(3) NULL,
    created_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_invoices_type CHECK (invoice_type IN ('CONSULTATION', 'DIAGNOSTIC', 'OTHER')),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_invoices_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_invoices_issued_at CHECK (status = 'DRAFT' OR issued_at IS NOT NULL OR status = 'CANCELLED')
);

CREATE TABLE public.invoice_lines (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    invoice_id uuid NOT NULL REFERENCES public.invoices(id) ON DELETE RESTRICT,
    service_request_id uuid NULL REFERENCES public.service_requests(id) ON DELETE RESTRICT,
    service_id uuid NOT NULL REFERENCES public.services(id) ON DELETE RESTRICT,
    description_snapshot text NOT NULL,
    quantity numeric(12,2) NOT NULL,
    unit_price numeric(14,2) NOT NULL,
    line_amount numeric(14,2) NOT NULL,
    status varchar(20) NOT NULL,
    CONSTRAINT ck_invoice_lines_status CHECK (status IN ('ACTIVE', 'VOID')),
    CONSTRAINT ck_invoice_lines_quantity CHECK (
        quantity <> 'NaN'::numeric AND quantity <> 'Infinity'::numeric AND quantity <> '-Infinity'::numeric AND quantity > 0
    ),
    CONSTRAINT ck_invoice_lines_unit_price CHECK (
        unit_price <> 'NaN'::numeric AND unit_price <> 'Infinity'::numeric AND unit_price <> '-Infinity'::numeric AND unit_price >= 0
    ),
    CONSTRAINT ck_invoice_lines_line_amount CHECK (
        line_amount <> 'NaN'::numeric AND line_amount <> 'Infinity'::numeric AND line_amount <> '-Infinity'::numeric
        AND line_amount >= 0
        AND line_amount = round(quantity * unit_price, 2)
    )
);

CREATE UNIQUE INDEX ux_invoice_lines_active_request
    ON public.invoice_lines(service_request_id)
    WHERE service_request_id IS NOT NULL AND status = 'ACTIVE';

CREATE TABLE public.payments (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    invoice_id uuid NOT NULL REFERENCES public.invoices(id) ON DELETE RESTRICT,
    payment_method varchar(20) NOT NULL,
    amount numeric(14,2) NOT NULL,
    status varchar(20) NOT NULL,
    provider text NULL,
    provider_reference text NULL,
    paid_at timestamptz(3) NULL,
    recorded_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payments_id_invoice UNIQUE (id, invoice_id),
    CONSTRAINT ck_payments_method CHECK (payment_method IN ('CASH', 'POS', 'VIETQR', 'OTHER')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_payments_amount CHECK (
        amount <> 'NaN'::numeric AND amount <> 'Infinity'::numeric AND amount <> '-Infinity'::numeric AND amount > 0
    ),
    CONSTRAINT ck_payments_provider_pair CHECK (
        (provider IS NULL AND provider_reference IS NULL)
        OR
        (provider IS NOT NULL AND provider_reference IS NOT NULL)
    ),
    CONSTRAINT ck_payments_confirmed CHECK (
        status <> 'CONFIRMED' OR paid_at IS NOT NULL
    ),
    CONSTRAINT ck_payments_staff_recording CHECK (
        payment_method NOT IN ('CASH', 'POS') OR recorded_by IS NOT NULL
    )
);

CREATE UNIQUE INDEX ux_payments_provider_reference
    ON public.payments(provider, provider_reference)
    WHERE provider_reference IS NOT NULL;

CREATE TABLE public.refunds (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    payment_id uuid NOT NULL REFERENCES public.payments(id) ON DELETE RESTRICT,
    amount numeric(14,2) NOT NULL,
    reason text NOT NULL,
    status varchar(20) NOT NULL,
    requested_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    approved_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    approved_at timestamptz(3) NULL,
    provider_reference text NULL,
    refunded_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_refunds_amount CHECK (
        amount <> 'NaN'::numeric AND amount <> 'Infinity'::numeric AND amount <> '-Infinity'::numeric AND amount > 0
    ),
    CONSTRAINT ck_refunds_status CHECK (status IN ('REQUESTED', 'APPROVED', 'PROCESSING', 'REFUNDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_refunds_approved CHECK (
        status NOT IN ('APPROVED', 'PROCESSING', 'REFUNDED')
        OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_refunds_refunded CHECK (status <> 'REFUNDED' OR refunded_at IS NOT NULL),
    CONSTRAINT uq_refunds_payment_provider_reference UNIQUE (payment_id, provider_reference)
);

CREATE TABLE public.service_authorizations (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    service_request_id uuid NOT NULL UNIQUE REFERENCES public.service_requests(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL,
    authorization_source varchar(20) NULL,
    source_invoice_id uuid NULL REFERENCES public.invoices(id) ON DELETE RESTRICT,
    source_payment_id uuid NULL,
    policy_reference text NULL,
    row_version bigint NOT NULL DEFAULT 0,
    authorized_at timestamptz(3) NULL,
    revoked_at timestamptz(3) NULL,
    CONSTRAINT ck_service_authorizations_status CHECK (status IN ('PENDING', 'AUTHORIZED', 'NOT_REQUIRED', 'REVOKED')),
    CONSTRAINT ck_service_authorizations_source CHECK (
        authorization_source IS NULL OR authorization_source IN ('PAYMENT', 'PACKAGE', 'WAIVER')
    ),
    CONSTRAINT ck_service_authorizations_state CHECK (
        (status = 'PENDING'
            AND authorization_source IS NULL
            AND source_invoice_id IS NULL
            AND source_payment_id IS NULL
            AND authorized_at IS NULL
            AND revoked_at IS NULL)
        OR
        (status = 'AUTHORIZED'
            AND authorization_source = 'PAYMENT'
            AND source_invoice_id IS NOT NULL
            AND source_payment_id IS NOT NULL
            AND authorized_at IS NOT NULL
            AND revoked_at IS NULL)
        OR
        (status = 'NOT_REQUIRED'
            AND authorization_source IN ('PACKAGE', 'WAIVER')
            AND source_invoice_id IS NULL
            AND source_payment_id IS NULL
            AND policy_reference IS NOT NULL
            AND btrim(policy_reference) <> ''
            AND authorized_at IS NOT NULL
            AND revoked_at IS NULL)
        OR
        (status = 'REVOKED' AND revoked_at IS NOT NULL)
    ),
    CONSTRAINT fk_service_authorizations_payment_provenance
        FOREIGN KEY (source_payment_id, source_invoice_id)
        REFERENCES public.payments(id, invoice_id)
        MATCH FULL
        ON DELETE RESTRICT
);

-- ============================================================================
-- 8. PRESCRIPTION
-- ============================================================================

CREATE TABLE public.prescriptions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    encounter_id uuid NOT NULL REFERENCES public.encounters(id) ON DELETE RESTRICT,
    created_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.prescription_versions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    prescription_id uuid NOT NULL REFERENCES public.prescriptions(id) ON DELETE RESTRICT,
    version_no integer NOT NULL,
    status varchar(20) NOT NULL,
    corrects_version_id uuid NULL,
    correction_reason text NULL,
    authored_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    issued_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    issued_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_prescription_versions_no UNIQUE (prescription_id, version_no),
    CONSTRAINT uq_prescription_versions_root_id UNIQUE (prescription_id, id),
    CONSTRAINT ck_prescription_versions_no CHECK (version_no > 0),
    CONSTRAINT ck_prescription_versions_status CHECK (status IN ('DRAFT', 'ISSUED', 'CORRECTED')),
    CONSTRAINT ck_prescription_versions_issued CHECK (
        (status = 'DRAFT' AND issued_by IS NULL AND issued_at IS NULL)
        OR
        (status IN ('ISSUED', 'CORRECTED') AND issued_by IS NOT NULL AND issued_at IS NOT NULL)
    ),
    CONSTRAINT ck_prescription_versions_correction_pair CHECK (
        (corrects_version_id IS NULL AND correction_reason IS NULL)
        OR
        (corrects_version_id IS NOT NULL AND correction_reason IS NOT NULL AND btrim(correction_reason) <> '')
    ),
    CONSTRAINT ck_prescription_versions_corrected_requires_source CHECK (
        status <> 'CORRECTED' OR corrects_version_id IS NOT NULL
    ),
    CONSTRAINT fk_prescription_versions_correction
        FOREIGN KEY (prescription_id, corrects_version_id)
        REFERENCES public.prescription_versions(prescription_id, id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_prescription_versions_one_draft
    ON public.prescription_versions(prescription_id)
    WHERE status = 'DRAFT';

CREATE TABLE public.prescription_items (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    prescription_version_id uuid NOT NULL REFERENCES public.prescription_versions(id) ON DELETE RESTRICT,
    medicine_id uuid NULL REFERENCES public.medicines(id) ON DELETE RESTRICT,
    medicine_name_snapshot text NOT NULL,
    strength_snapshot text NOT NULL,
    dose text NOT NULL,
    route text NOT NULL,
    frequency text NOT NULL,
    duration text NOT NULL,
    quantity numeric(12,2) NOT NULL,
    instruction text NOT NULL,
    CONSTRAINT ck_prescription_items_quantity CHECK (quantity > 0)
);

-- ============================================================================
-- 9. APPOINTMENT + deferred Encounter FK
-- ============================================================================

CREATE TABLE public.appointments (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    patient_id uuid NOT NULL REFERENCES public.patients(id) ON DELETE RESTRICT,
    source_encounter_id uuid NULL REFERENCES public.encounters(id) ON DELETE RESTRICT,
    doctor_id uuid NOT NULL REFERENCES public.staff_members(id) ON DELETE RESTRICT,
    department_id uuid NOT NULL REFERENCES public.departments(id) ON DELETE RESTRICT,
    room_id uuid NULL REFERENCES public.rooms(id) ON DELETE RESTRICT,
    service_id uuid NOT NULL REFERENCES public.services(id) ON DELETE RESTRICT,
    scheduled_start timestamptz(3) NOT NULL,
    scheduled_end timestamptz(3) NOT NULL,
    status varchar(20) NOT NULL,
    created_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_appointments_status CHECK (status IN ('SCHEDULED', 'CONFIRMED', 'ARRIVED', 'COMPLETED', 'NO_SHOW', 'CANCELLED')),
    CONSTRAINT ck_appointments_range CHECK (scheduled_end > scheduled_start)
);

ALTER TABLE public.encounters
    ADD CONSTRAINT fk_encounters_appointment
    FOREIGN KEY (appointment_id)
    REFERENCES public.appointments(id)
    ON DELETE RESTRICT;

-- ============================================================================
-- 10. HEALTH EXAMINATION
-- ============================================================================

CREATE TABLE public.organizations (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    name varchar(300) NOT NULL,
    tax_code varchar(50) NULL,
    phone text NULL,
    email text NOT NULL,
    address text NOT NULL,
    contact_full_name varchar(200) NOT NULL,
    contact_phone text NOT NULL,
    contact_email text NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_organizations_tax_code UNIQUE (tax_code),
    CONSTRAINT ck_organizations_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

    CREATE TABLE public.health_examination_batches (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    organization_id uuid NOT NULL REFERENCES public.organizations(id) ON DELETE RESTRICT,
    batch_code varchar(50) NOT NULL UNIQUE,
    name varchar(300) NOT NULL,
    examination_site_type varchar(30) NOT NULL,
    examination_site_name text NOT NULL,
    examination_site_address text NOT NULL,
    status varchar(20) NOT NULL,
    created_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz(3) NULL,
    CONSTRAINT ck_health_exam_batches_site_type CHECK (examination_site_type IN ('CLINIC', 'ORGANIZATION_SITE')),
    CONSTRAINT ck_health_exam_batches_status CHECK (status IN ('DRAFT', 'READY', 'FINALIZED', 'CLOSED'))
);

CREATE TABLE public.health_examination_batch_days (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    batch_id uuid NOT NULL REFERENCES public.health_examination_batches(id) ON DELETE RESTRICT,
    examination_date date NOT NULL,
    CONSTRAINT uq_health_exam_batch_days_date UNIQUE (batch_id, examination_date),
    CONSTRAINT uq_health_exam_batch_days_scope UNIQUE (batch_id, id)
);

CREATE TABLE public.health_examination_batch_services (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    batch_id uuid NOT NULL REFERENCES public.health_examination_batches(id) ON DELETE RESTRICT,
    service_id uuid NOT NULL REFERENCES public.services(id) ON DELETE RESTRICT,
    reference_price_snapshot numeric(14,2) NOT NULL,
    negotiated_price numeric(14,2) NOT NULL,
    display_order integer NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_health_exam_batch_services_service UNIQUE (batch_id, service_id),
    CONSTRAINT uq_health_exam_batch_services_order UNIQUE (batch_id, display_order),
    CONSTRAINT uq_health_exam_batch_services_scope UNIQUE (batch_id, id),
    CONSTRAINT ck_health_exam_batch_services_reference_price CHECK (
        reference_price_snapshot <> 'NaN'::numeric
        AND reference_price_snapshot <> 'Infinity'::numeric
        AND reference_price_snapshot <> '-Infinity'::numeric
        AND reference_price_snapshot >= 0
    ),
    CONSTRAINT ck_health_exam_batch_services_negotiated_price CHECK (
        negotiated_price <> 'NaN'::numeric
        AND negotiated_price <> 'Infinity'::numeric
        AND negotiated_price <> '-Infinity'::numeric
        AND negotiated_price >= 0
    ),
    CONSTRAINT ck_health_exam_batch_services_order CHECK (display_order > 0)
);

-- import_job_id FK is added after public.import_jobs is created.
CREATE TABLE public.health_examination_batch_participants (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    batch_id uuid NOT NULL REFERENCES public.health_examination_batches(id) ON DELETE RESTRICT,
    batch_day_id uuid NOT NULL,
    participant_code text NULL,
    full_name varchar(200) NOT NULL,
    date_of_birth date NOT NULL,
    sex varchar(16) NOT NULL,
    identification_number text NOT NULL,
    identification_issue_date date NULL,
    identification_issue_place text NULL,
    ethnicity text NULL,
    phone text NULL,
    email text NULL,
    address text NULL,
    workplace text NULL,
    department_name text NOT NULL,
    position_name text NOT NULL,
    note text NULL,
    patient_id uuid NULL REFERENCES public.patients(id) ON DELETE RESTRICT,
    roster_status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    attendance_status varchar(20) NOT NULL DEFAULT 'UNCONFIRMED',
    actual_examination_date date NULL,
    attendance_recorded_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    attendance_recorded_at timestamptz(3) NULL,
    attendance_note text NULL,
    service_reconciliation_status varchar(20) NOT NULL DEFAULT 'PENDING',
    services_reconciled_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    services_reconciled_at timestamptz(3) NULL,
    import_job_id uuid NULL,
    source_row_number integer NULL,
    prepared_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_health_exam_batch_participants_identification UNIQUE (batch_id, identification_number),
    CONSTRAINT uq_health_exam_batch_participants_scope UNIQUE (batch_id, id),
    CONSTRAINT fk_health_exam_batch_participants_day
        FOREIGN KEY (batch_id, batch_day_id)
        REFERENCES public.health_examination_batch_days(batch_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_health_exam_batch_participants_identification CHECK (btrim(identification_number) <> ''),
    CONSTRAINT ck_health_exam_batch_participants_roster CHECK (roster_status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_health_exam_batch_participants_attendance CHECK (attendance_status IN ('UNCONFIRMED', 'ATTENDED', 'ABSENT')),
    CONSTRAINT ck_health_exam_batch_participants_reconciliation CHECK (service_reconciliation_status IN ('PENDING', 'RECONCILED')),
    CONSTRAINT ck_health_exam_batch_participants_attendance_date CHECK (
        (attendance_status = 'ATTENDED' AND actual_examination_date IS NOT NULL)
        OR
        (attendance_status IN ('UNCONFIRMED', 'ABSENT') AND actual_examination_date IS NULL)
    ),
    CONSTRAINT ck_health_exam_batch_participants_attendance_actor CHECK (
        (attendance_recorded_by IS NULL AND attendance_recorded_at IS NULL)
        OR
        (attendance_recorded_by IS NOT NULL AND attendance_recorded_at IS NOT NULL)
    ),
    CONSTRAINT ck_health_exam_batch_participants_reconciled_actor CHECK (
        service_reconciliation_status <> 'RECONCILED'
        OR (services_reconciled_by IS NOT NULL AND services_reconciled_at IS NOT NULL)
    ),
    CONSTRAINT ck_health_exam_batch_participants_import_source CHECK (
        (import_job_id IS NULL AND source_row_number IS NULL)
        OR
        (import_job_id IS NOT NULL AND source_row_number IS NOT NULL AND source_row_number > 0)
    ),
    CONSTRAINT ck_health_exam_batch_participants_prepared CHECK (prepared_at IS NULL OR patient_id IS NOT NULL)
);

CREATE TABLE public.health_examination_participant_services (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    batch_id uuid NOT NULL,
    batch_participant_id uuid NOT NULL,
    batch_service_id uuid NOT NULL,
    is_performed boolean NOT NULL,
    service_request_id uuid NULL REFERENCES public.service_requests(id) ON DELETE RESTRICT,
    unit_price_snapshot numeric(14,2) NOT NULL,
    recorded_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    recorded_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_health_exam_participant_services UNIQUE (batch_participant_id, batch_service_id),
    CONSTRAINT fk_health_exam_participant_services_participant
        FOREIGN KEY (batch_id, batch_participant_id)
        REFERENCES public.health_examination_batch_participants(batch_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_health_exam_participant_services_service
        FOREIGN KEY (batch_id, batch_service_id)
        REFERENCES public.health_examination_batch_services(batch_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_health_exam_participant_services_price CHECK (
        unit_price_snapshot <> 'NaN'::numeric
        AND unit_price_snapshot <> 'Infinity'::numeric
        AND unit_price_snapshot <> '-Infinity'::numeric
        AND unit_price_snapshot >= 0
    )
);

CREATE UNIQUE INDEX ux_health_exam_participant_services_request
    ON public.health_examination_participant_services(service_request_id)
    WHERE service_request_id IS NOT NULL;

CREATE TABLE public.health_examination_records (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    batch_participant_id uuid NULL REFERENCES public.health_examination_batch_participants(id) ON DELETE RESTRICT,
    encounter_id uuid NOT NULL UNIQUE REFERENCES public.encounters(id) ON DELETE RESTRICT,
    mrn varchar(100) NOT NULL UNIQUE,
    status varchar(20) NOT NULL,
    prepared_at timestamptz(3) NOT NULL,
    issued_at timestamptz(3) NULL,
    finalized_at timestamptz(3) NULL,
    cancelled_at timestamptz(3) NULL,
    cancel_reason text NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_health_exam_records_status CHECK (status IN ('PREPARED', 'IN_PROGRESS', 'COMPLETED', 'ISSUED', 'CANCELLED')),
    CONSTRAINT ck_health_exam_records_lifecycle CHECK (
        (status NOT IN ('COMPLETED', 'ISSUED') OR finalized_at IS NOT NULL)
        AND (status <> 'ISSUED' OR issued_at IS NOT NULL)
        AND (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_health_exam_records_active_participant
    ON public.health_examination_records(batch_participant_id)
    WHERE batch_participant_id IS NOT NULL AND status <> 'CANCELLED';

CREATE TABLE public.health_examination_record_snapshots (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    health_examination_record_id uuid NOT NULL REFERENCES public.health_examination_records(id) ON DELETE RESTRICT,
    version_no integer NOT NULL,
    status varchar(20) NOT NULL,
    full_name varchar(200) NOT NULL,
    date_of_birth date NOT NULL,
    sex varchar(16) NOT NULL,
    identification_number text NOT NULL,
    phone text NULL,
    address text NULL,
    organization_name text NULL,
    participant_code text NULL,
    department_name text NULL,
    position_name text NULL,
    issued_at timestamptz(3) NULL,
    issued_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    row_version bigint NOT NULL DEFAULT 0,
    created_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_health_exam_record_snapshots_no UNIQUE (health_examination_record_id, version_no),
    CONSTRAINT uq_health_exam_record_snapshots_scope UNIQUE (health_examination_record_id, id),
    CONSTRAINT ck_health_exam_record_snapshots_no CHECK (version_no > 0),
    CONSTRAINT ck_health_exam_record_snapshots_status CHECK (status IN ('DRAFT', 'ISSUED')),
    CONSTRAINT ck_health_exam_record_snapshots_issued CHECK (
        (status = 'DRAFT' AND issued_by IS NULL AND issued_at IS NULL)
        OR
        (status = 'ISSUED' AND issued_by IS NOT NULL AND issued_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_health_exam_record_snapshots_one_draft
    ON public.health_examination_record_snapshots(health_examination_record_id)
    WHERE status = 'DRAFT';

CREATE TABLE public.health_examination_record_versions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    health_examination_record_id uuid NOT NULL REFERENCES public.health_examination_records(id) ON DELETE RESTRICT,
    version_no integer NOT NULL,
    administrative_snapshot_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    conclusion_text text NULL,
    conclusion_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    conclusion_at timestamptz(3) NULL,
    corrects_version_id uuid NULL,
    correction_reason text NULL,
    completed_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    completed_at timestamptz(3) NULL,
    issued_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    issued_at timestamptz(3) NULL,
    authored_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_health_exam_record_versions_no UNIQUE (health_examination_record_id, version_no),
    CONSTRAINT uq_health_exam_record_versions_scope UNIQUE (health_examination_record_id, id),
    CONSTRAINT ck_health_exam_record_versions_no CHECK (version_no > 0),
    CONSTRAINT ck_health_exam_record_versions_status CHECK (status IN ('DRAFT', 'COMPLETED', 'ISSUED')),
    CONSTRAINT ck_health_exam_record_versions_completion CHECK (
        status = 'DRAFT'
        OR (
            conclusion_text IS NOT NULL AND btrim(conclusion_text) <> ''
            AND conclusion_by IS NOT NULL AND conclusion_at IS NOT NULL
            AND completed_by IS NOT NULL AND completed_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_health_exam_record_versions_issue CHECK (
        status <> 'ISSUED' OR (issued_by IS NOT NULL AND issued_at IS NOT NULL)
    ),
    CONSTRAINT ck_health_exam_record_versions_draft_issue_fields CHECK (
        status <> 'DRAFT' OR (completed_by IS NULL AND completed_at IS NULL AND issued_by IS NULL AND issued_at IS NULL)
    ),
    CONSTRAINT ck_health_exam_record_versions_correction CHECK (
        (corrects_version_id IS NULL AND correction_reason IS NULL)
        OR
        (corrects_version_id IS NOT NULL AND correction_reason IS NOT NULL AND btrim(correction_reason) <> '')
    ),
    CONSTRAINT fk_health_exam_record_versions_snapshot
        FOREIGN KEY (health_examination_record_id, administrative_snapshot_id)
        REFERENCES public.health_examination_record_snapshots(health_examination_record_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_health_exam_record_versions_correction
        FOREIGN KEY (health_examination_record_id, corrects_version_id)
        REFERENCES public.health_examination_record_versions(health_examination_record_id, id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_health_exam_record_versions_one_draft
    ON public.health_examination_record_versions(health_examination_record_id)
    WHERE status = 'DRAFT';

CREATE TABLE public.health_examination_record_version_items (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    record_version_id uuid NOT NULL REFERENCES public.health_examination_record_versions(id) ON DELETE RESTRICT,
    service_id uuid NOT NULL REFERENCES public.services(id) ON DELETE RESTRICT,
    participant_service_id uuid NULL REFERENCES public.health_examination_participant_services(id) ON DELETE RESTRICT,
    service_request_id uuid NULL REFERENCES public.service_requests(id) ON DELETE RESTRICT,
    assessment_version_id uuid NULL REFERENCES public.encounter_assessment_versions(id) ON DELETE RESTRICT,
    result_version_id uuid NULL REFERENCES public.result_versions(id) ON DELETE RESTRICT,
    created_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_health_exam_record_version_items_source CHECK (num_nonnulls(assessment_version_id, result_version_id) <= 1)
);

CREATE UNIQUE INDEX ux_health_exam_record_version_items_participant_service
    ON public.health_examination_record_version_items(record_version_id, participant_service_id)
    WHERE participant_service_id IS NOT NULL;

CREATE UNIQUE INDEX ux_health_exam_record_version_items_service_request
    ON public.health_examination_record_version_items(record_version_id, service_request_id)
    WHERE service_request_id IS NOT NULL;

-- ============================================================================
-- 11. DOCUMENT
-- ============================================================================

CREATE TABLE public.files (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    storage_provider varchar(50) NOT NULL,
    storage_key text NOT NULL,
    original_filename text NOT NULL,
    content_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum bytea NOT NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_files_storage UNIQUE (storage_provider, storage_key),
    CONSTRAINT ck_document_files_size CHECK (size_bytes >= 0)
);

CREATE TABLE public.templates (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    code varchar(100) NOT NULL UNIQUE,
    name varchar(300) NOT NULL,
    template_type varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.template_versions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    template_id uuid NOT NULL REFERENCES public.templates(id) ON DELETE RESTRICT,
    version_no integer NOT NULL,
    file_id uuid NOT NULL REFERENCES public.files(id) ON DELETE RESTRICT,
    paper_size varchar(20) NOT NULL,
    orientation varchar(20) NOT NULL,
    render_mode varchar(30) NOT NULL,
    active_from timestamptz(3) NOT NULL,
    retired_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_template_versions_no UNIQUE (template_id, version_no),
    CONSTRAINT ck_document_template_versions_no CHECK (version_no > 0),
    CONSTRAINT ck_document_template_versions_paper CHECK (paper_size IN ('A3', 'A4', 'A5', 'A6', 'CUSTOM')),
    CONSTRAINT ck_document_template_versions_orientation CHECK (orientation IN ('PORTRAIT', 'LANDSCAPE')),
    CONSTRAINT ck_document_template_versions_render_mode CHECK (render_mode IN ('MASTER_FORM', 'ONE_PER_SERVICE', 'MERGE_BY_TEMPLATE')),
    CONSTRAINT ck_document_template_versions_period CHECK (retired_at IS NULL OR retired_at > active_from)
);

CREATE TABLE public.service_template_mappings (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    service_id uuid NOT NULL REFERENCES public.services(id) ON DELETE RESTRICT,
    template_id uuid NOT NULL REFERENCES public.templates(id) ON DELETE RESTRICT,
    active_from timestamptz(3) NOT NULL,
    retired_at timestamptz(3) NULL,
    CONSTRAINT ck_document_service_template_mappings_period CHECK (retired_at IS NULL OR retired_at > active_from)
);

CREATE TABLE public.issued_representations (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    document_type text NOT NULL,
    health_examination_record_snapshot_id uuid NULL REFERENCES public.health_examination_record_snapshots(id) ON DELETE RESTRICT,
    health_examination_record_version_id uuid NULL REFERENCES public.health_examination_record_versions(id) ON DELETE RESTRICT,
    result_version_id uuid NULL REFERENCES public.result_versions(id) ON DELETE RESTRICT,
    prescription_version_id uuid NULL REFERENCES public.prescription_versions(id) ON DELETE RESTRICT,
    assessment_version_id uuid NULL REFERENCES public.encounter_assessment_versions(id) ON DELETE RESTRICT,
    template_version_id uuid NOT NULL REFERENCES public.template_versions(id) ON DELETE RESTRICT,
    issued_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    issued_at timestamptz(3) NOT NULL,
    CONSTRAINT ck_document_issued_representations_one_source CHECK (
        num_nonnulls(
            health_examination_record_snapshot_id,
            health_examination_record_version_id,
            result_version_id,
            prescription_version_id,
            assessment_version_id
        ) = 1
    )
);

CREATE UNIQUE INDEX ux_issued_rep_snapshot_type
    ON public.issued_representations(health_examination_record_snapshot_id, document_type)
    WHERE health_examination_record_snapshot_id IS NOT NULL;
CREATE UNIQUE INDEX ux_issued_rep_record_version_type
    ON public.issued_representations(health_examination_record_version_id, document_type)
    WHERE health_examination_record_version_id IS NOT NULL;
CREATE UNIQUE INDEX ux_issued_rep_result_version_type
    ON public.issued_representations(result_version_id, document_type)
    WHERE result_version_id IS NOT NULL;
CREATE UNIQUE INDEX ux_issued_rep_prescription_version_type
    ON public.issued_representations(prescription_version_id, document_type)
    WHERE prescription_version_id IS NOT NULL;
CREATE UNIQUE INDEX ux_issued_rep_assessment_version_type
    ON public.issued_representations(assessment_version_id, document_type)
    WHERE assessment_version_id IS NOT NULL;

-- ============================================================================
-- 12. INTEGRATION
-- ============================================================================

CREATE TABLE public.import_jobs (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    import_type varchar(50) NOT NULL,
    batch_id uuid NULL REFERENCES public.health_examination_batches(id) ON DELETE RESTRICT,
    configuration jsonb NOT NULL,
    source_file_id uuid NULL REFERENCES public.files(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL,
    created_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    confirmed_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at timestamptz(3) NULL,
    cancelled_at timestamptz(3) NULL,
    expires_at timestamptz(3) NULL,
    confirmed_result jsonb NULL,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_import_jobs_type CHECK (import_type IN ('ORGANIZATION_PARTICIPANT', 'HEALTH_EXAMINATION_RESULT', 'HEALTH_EXAMINATION_SERVICE_RECONCILIATION')),
    CONSTRAINT ck_import_jobs_status CHECK (status IN ('VALIDATED', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_import_jobs_batch CHECK (batch_id IS NOT NULL),
    CONSTRAINT ck_import_jobs_confirmed CHECK (
        (status = 'CONFIRMED' AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL AND confirmed_result IS NOT NULL)
        OR
        (status <> 'CONFIRMED' AND confirmed_by IS NULL AND confirmed_at IS NULL AND confirmed_result IS NULL)
    ),
    CONSTRAINT ck_import_jobs_cancelled CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
);

CREATE TABLE public.import_rows (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    job_id uuid NOT NULL REFERENCES public.import_jobs(id) ON DELETE CASCADE,
    row_number integer NOT NULL,
    normalized_payload jsonb NULL,
    preview_metadata jsonb NULL,
    committed_resource_type text NULL,
    committed_resource_id uuid NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_import_rows_row UNIQUE (job_id, row_number),
    CONSTRAINT ck_import_rows_number CHECK (row_number > 0),
    CONSTRAINT ck_import_rows_committed_pair CHECK (
        (committed_resource_type IS NULL AND committed_resource_id IS NULL)
        OR
        (committed_resource_type IS NOT NULL AND committed_resource_id IS NOT NULL)
    )
);

CREATE TABLE public.system_connections (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    code varchar(100) NOT NULL UNIQUE,
    name varchar(300) NOT NULL,
    integration_type varchar(100) NOT NULL,
    status varchar(30) NOT NULL,
    configuration jsonb NOT NULL,
    secret_reference text NOT NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.external_code_mappings (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    connection_id uuid NOT NULL REFERENCES public.system_connections(id) ON DELETE RESTRICT,
    mapping_type varchar(100) NOT NULL,
    internal_code text NOT NULL,
    external_code text NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_external_code_mappings_internal UNIQUE (connection_id, mapping_type, internal_code)
);

CREATE TABLE public.health_data_submissions (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    patient_id uuid NOT NULL REFERENCES public.patients(id) ON DELETE RESTRICT,
    encounter_id uuid NOT NULL REFERENCES public.encounters(id) ON DELETE RESTRICT,
    submission_type varchar(100) NOT NULL,
    status varchar(30) NOT NULL,
    payload_version varchar(50) NOT NULL,
    prepared_at timestamptz(3) NOT NULL,
    validated_at timestamptz(3) NULL,
    submitted_at timestamptz(3) NULL,
    external_reference text NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_health_data_submissions_status CHECK (status IN ('PREPARED', 'VALIDATED', 'EXPORTABLE', 'SUBMITTED', 'ACCEPTED', 'REJECTED'))
);

CREATE TABLE public.idempotency_keys (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    scope text NOT NULL,
    actor_key text NOT NULL,
    request_key text NOT NULL,
    request_hash bytea NOT NULL,
    status varchar(20) NOT NULL,
    result_resource_type text NULL,
    result_resource_id uuid NULL,
    result_summary jsonb NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamptz(3) NULL,
    expires_at timestamptz(3) NULL,
    CONSTRAINT uq_idempotency_keys UNIQUE (scope, actor_key, request_key),
    CONSTRAINT ck_idempotency_keys_hash CHECK (octet_length(request_hash) = 32),
    CONSTRAINT ck_idempotency_keys_status CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_idempotency_keys_result_pair CHECK (
        (result_resource_type IS NULL AND result_resource_id IS NULL)
        OR
        (result_resource_type IS NOT NULL AND result_resource_id IS NOT NULL)
    ),
    CONSTRAINT ck_idempotency_keys_completed CHECK (
        (status = 'PROCESSING' AND completed_at IS NULL)
        OR
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
    )
);

CREATE TABLE public.outbox_events (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    event_type text NOT NULL,
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    payload jsonb NOT NULL,
    status varchar(20) NOT NULL,
    available_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_count integer NOT NULL DEFAULT 0,
    processed_at timestamptz(3) NULL,
    last_error text NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_token uuid NULL,
    locked_until timestamptz(3) NULL,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED', 'DEAD_LETTER')),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_events_lease CHECK (
        (status = 'PROCESSING' AND lock_token IS NOT NULL AND locked_until IS NOT NULL)
        OR
        (status <> 'PROCESSING' AND lock_token IS NULL AND locked_until IS NULL)
    ),
    CONSTRAINT ck_outbox_events_processed CHECK (
        (status = 'PROCESSED' AND processed_at IS NOT NULL)
        OR
        (status <> 'PROCESSED' AND processed_at IS NULL)
    )
);

ALTER TABLE public.health_examination_batch_participants
    ADD CONSTRAINT fk_health_exam_batch_participants_import_job
    FOREIGN KEY (import_job_id)
    REFERENCES public.import_jobs(id)
    ON DELETE RESTRICT;

-- ============================================================================
-- 13. PORTAL
-- ============================================================================

CREATE TABLE public.result_releases (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    result_version_id uuid NOT NULL REFERENCES public.result_versions(id) ON DELETE RESTRICT,
    patient_id uuid NOT NULL REFERENCES public.patients(id) ON DELETE RESTRICT,
    released_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    released_at timestamptz(3) NOT NULL,
    revoked_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    revoked_at timestamptz(3) NULL,
    revocation_reason text NULL,
    CONSTRAINT ck_result_releases_revoke CHECK (
        (revoked_by IS NULL AND revoked_at IS NULL AND revocation_reason IS NULL)
        OR
        (revoked_by IS NOT NULL AND revoked_at IS NOT NULL AND revoked_at >= released_at)
    )
);

CREATE UNIQUE INDEX ux_result_releases_current
    ON public.result_releases(result_version_id)
    WHERE revoked_at IS NULL;

CREATE TABLE public.document_releases (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    patient_id uuid NOT NULL REFERENCES public.patients(id) ON DELETE RESTRICT,
    issued_representation_id uuid NOT NULL REFERENCES public.issued_representations(id) ON DELETE RESTRICT,
    released_by uuid NOT NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    released_at timestamptz(3) NOT NULL,
    revoked_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    revoked_at timestamptz(3) NULL,
    revocation_reason text NULL,
    CONSTRAINT ck_document_releases_revoke CHECK (
        (revoked_by IS NULL AND revoked_at IS NULL)
        OR
        (revoked_by IS NOT NULL AND revoked_at IS NOT NULL AND revoked_at >= released_at)
    )
);

CREATE UNIQUE INDEX ux_document_releases_current
    ON public.document_releases(issued_representation_id)
    WHERE revoked_at IS NULL;

-- ============================================================================
-- 14. NOTIFICATION
-- ============================================================================

CREATE TABLE public.notification_batches (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    event_type text NOT NULL,
    channel varchar(20) NOT NULL,
    status varchar(30) NOT NULL,
    created_by uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamptz(3) NULL,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_notification_batches_channel CHECK (channel IN ('SMS', 'EMAIL')),
    CONSTRAINT ck_notification_batches_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'PARTIALLY_FAILED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_notification_batches_completed CHECK (
        status NOT IN ('COMPLETED', 'PARTIALLY_FAILED', 'FAILED', 'CANCELLED') OR completed_at IS NOT NULL
    )
);

CREATE TABLE public.notifications (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    batch_id uuid NULL REFERENCES public.notification_batches(id) ON DELETE RESTRICT,
    channel varchar(20) NOT NULL,
    event_type text NOT NULL,
    patient_id uuid NULL REFERENCES public.patients(id) ON DELETE RESTRICT,
    organization_id uuid NULL REFERENCES public.organizations(id) ON DELETE RESTRICT,
    recipient_snapshot text NOT NULL,
    subject text NULL,
    content text NOT NULL,
    deduplication_key text NOT NULL UNIQUE,
    status varchar(20) NOT NULL,
    scheduled_at timestamptz(3) NULL,
    sent_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_token uuid NULL,
    locked_until timestamptz(3) NULL,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('SMS', 'EMAIL')),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_notifications_owner CHECK (num_nonnulls(patient_id, organization_id) <= 1),
    CONSTRAINT ck_notifications_subject CHECK (channel <> 'EMAIL' OR subject IS NOT NULL),
    CONSTRAINT ck_notifications_lease CHECK (
        (status = 'PROCESSING' AND lock_token IS NOT NULL AND locked_until IS NOT NULL)
        OR
        (status <> 'PROCESSING' AND lock_token IS NULL AND locked_until IS NULL)
    ),
    CONSTRAINT ck_notifications_sent CHECK (
        (status = 'SENT' AND sent_at IS NOT NULL)
        OR
        (status <> 'SENT' AND sent_at IS NULL)
    )
);

CREATE TABLE public.notification_attempts (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    notification_id uuid NOT NULL REFERENCES public.notifications(id) ON DELETE RESTRICT,
    attempt_no integer NOT NULL,
    provider text NOT NULL,
    provider_message_id text NULL,
    status varchar(20) NOT NULL,
    error_code text NULL,
    error_message text NULL,
    attempted_at timestamptz(3) NOT NULL,
    claim_token uuid NOT NULL,
    completed_at timestamptz(3) NULL,
    CONSTRAINT uq_notification_attempts_no UNIQUE (notification_id, attempt_no),
    CONSTRAINT ck_notification_attempts_no CHECK (attempt_no > 0),
    CONSTRAINT ck_notification_attempts_status CHECK (status IN ('STARTED', 'ACCEPTED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT ck_notification_attempts_completed CHECK (
        (status = 'STARTED' AND completed_at IS NULL)
        OR
        (status <> 'STARTED' AND completed_at IS NOT NULL)
    )
);

-- ============================================================================
-- 15. AUDIT
-- ============================================================================

CREATE TABLE public.audit_events (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    occurred_at timestamptz(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_account_id uuid NULL REFERENCES public.accounts(id) ON DELETE RESTRICT,
    action text NOT NULL,
    resource_type text NOT NULL,
    resource_id uuid NOT NULL,
    department_id uuid NULL REFERENCES public.departments(id) ON DELETE RESTRICT,
    correlation_id uuid NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

-- ============================================================================
-- 16. INDEXES
-- Avoid duplicates for PK/UNIQUE-backed prefixes.
-- ============================================================================

CREATE INDEX ix_patients_phone ON public.patients(phone);
CREATE INDEX ix_patients_full_name_lower ON public.patients(lower(full_name));

CREATE INDEX ix_patient_allergies_patient ON public.patient_allergies(patient_id, status);
CREATE INDEX ix_patient_conditions_patient ON public.patient_conditions(patient_id, clinical_status);

CREATE INDEX ix_encounters_patient_created ON public.encounters(patient_id, created_at DESC);
CREATE INDEX ix_encounters_status_type ON public.encounters(status, encounter_type);
CREATE INDEX ix_encounters_room_status ON public.encounters(assigned_room_id, status);
CREATE INDEX ix_encounters_doctor_status ON public.encounters(assigned_doctor_id, status);

CREATE INDEX ix_vital_signs_encounter_measured ON public.vital_signs(encounter_id, measured_at DESC);
CREATE INDEX ix_diagnoses_assessment_version ON public.diagnoses(assessment_version_id);
CREATE INDEX ix_service_requests_round ON public.service_requests(order_round_id);
CREATE INDEX ix_service_requests_department_status ON public.service_requests(assigned_department_id, status);
CREATE INDEX ix_service_requests_service_status ON public.service_requests(service_id, status);

CREATE INDEX ix_result_versions_series_status ON public.result_versions(result_series_id, status);
CREATE INDEX ix_lab_result_items_version ON public.lab_result_items(result_version_id);

CREATE INDEX ix_invoices_encounter_status ON public.invoices(encounter_id, status);
CREATE INDEX ix_invoice_lines_invoice ON public.invoice_lines(invoice_id);
CREATE INDEX ix_payments_invoice ON public.payments(invoice_id);
CREATE INDEX ix_refunds_payment_status ON public.refunds(payment_id, status);

CREATE INDEX ix_prescriptions_encounter ON public.prescriptions(encounter_id);
CREATE INDEX ix_prescription_items_version ON public.prescription_items(prescription_version_id);

CREATE INDEX ix_appointments_patient_time ON public.appointments(patient_id, scheduled_start DESC);
CREATE INDEX ix_appointments_doctor_time ON public.appointments(doctor_id, scheduled_start);

CREATE INDEX ix_health_exam_batches_organization ON public.health_examination_batches(organization_id, created_at DESC);
CREATE INDEX ix_health_exam_batch_participants_day ON public.health_examination_batch_participants(batch_id, batch_day_id);
CREATE INDEX ix_health_exam_batch_participants_reconciliation ON public.health_examination_batch_participants(batch_id, service_reconciliation_status);
CREATE INDEX ix_health_exam_batch_participants_attendance ON public.health_examination_batch_participants(batch_id, attendance_status);
CREATE INDEX ix_health_exam_batch_participants_patient ON public.health_examination_batch_participants(patient_id) WHERE patient_id IS NOT NULL;
CREATE INDEX ix_health_exam_participant_services_performed ON public.health_examination_participant_services(batch_id, batch_participant_id, is_performed);
CREATE INDEX ix_health_exam_record_versions_record_status ON public.health_examination_record_versions(health_examination_record_id, status);
CREATE INDEX ix_health_exam_record_version_items_version ON public.health_examination_record_version_items(record_version_id);

CREATE INDEX ix_document_template_versions_effective ON public.template_versions(template_id, active_from, retired_at);
CREATE INDEX ix_document_service_template_mappings_effective ON public.service_template_mappings(service_id, active_from, retired_at);
CREATE INDEX ix_document_issued_rep_template ON public.issued_representations(template_version_id, issued_at DESC);

CREATE INDEX ix_import_jobs_type_status ON public.import_jobs(import_type, status);
CREATE INDEX ix_import_jobs_batch_created ON public.import_jobs(batch_id, created_at DESC);
CREATE INDEX ix_import_rows_job ON public.import_rows(job_id, row_number);
CREATE INDEX ix_external_code_mappings_external ON public.external_code_mappings(connection_id, mapping_type, external_code);
CREATE INDEX ix_health_data_submissions_patient ON public.health_data_submissions(patient_id, created_at DESC);
CREATE INDEX ix_health_data_submissions_status ON public.health_data_submissions(status, created_at);
CREATE INDEX ix_outbox_events_claimable ON public.outbox_events(available_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX ix_outbox_events_processing_lease ON public.outbox_events(locked_until)
    WHERE status = 'PROCESSING';
CREATE INDEX ix_outbox_events_aggregate ON public.outbox_events(aggregate_type, aggregate_id, created_at);

CREATE INDEX ix_result_releases_patient ON public.result_releases(patient_id, released_at DESC)
    WHERE revoked_at IS NULL;
CREATE INDEX ix_document_releases_patient ON public.document_releases(patient_id, released_at DESC)
    WHERE revoked_at IS NULL;

CREATE INDEX ix_notification_batches_status ON public.notification_batches(status, created_at);
CREATE INDEX ix_notifications_claimable ON public.notifications(scheduled_at, created_at)
    WHERE status = 'PENDING';
CREATE INDEX ix_notifications_processing_lease ON public.notifications(locked_until)
    WHERE status = 'PROCESSING';
CREATE INDEX ix_notifications_batch_status ON public.notifications(batch_id, status);
CREATE INDEX ix_notification_attempts_notification_time ON public.notification_attempts(notification_id, attempted_at DESC);

CREATE INDEX ix_audit_events_resource ON public.audit_events(resource_type, resource_id, occurred_at DESC);
CREATE INDEX ix_audit_events_actor ON public.audit_events(actor_account_id, occurred_at DESC) WHERE actor_account_id IS NOT NULL;
CREATE INDEX ix_audit_events_correlation ON public.audit_events(correlation_id) WHERE correlation_id IS NOT NULL;

-- ============================================================================
-- 17. VALIDATION / IMMUTABILITY TRIGGERS
-- ============================================================================

-- 17.1 Access control: PATIENT roles require patient accounts; other roles and grantors require staff.
CREATE OR REPLACE FUNCTION public.trg_validate_account_role()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_target_type text;
    v_grantor_type text;
    v_role_code text;
BEGIN
    SELECT account_type INTO v_target_type
    FROM public.accounts
    WHERE id = NEW.account_id;

    SELECT code INTO v_role_code
    FROM public.roles
    WHERE id = NEW.role_id;

    IF v_role_code = 'PATIENT' THEN
        IF v_target_type IS DISTINCT FROM 'PATIENT' THEN
            RAISE EXCEPTION 'Staff/non-patient account % cannot be assigned the PATIENT role', NEW.account_id
                USING ERRCODE = '23514';
        END IF;
    ELSIF v_target_type IS DISTINCT FROM 'STAFF' THEN
        RAISE EXCEPTION 'Patient/non-staff account % cannot be assigned a staff role', NEW.account_id
            USING ERRCODE = '23514';
    END IF;

    SELECT account_type INTO v_grantor_type
    FROM public.accounts
    WHERE id = NEW.granted_by;

    IF v_grantor_type IS DISTINCT FROM 'STAFF' THEN
        RAISE EXCEPTION 'Role grantor % must be a STAFF account', NEW.granted_by
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_account_roles_validate
BEFORE INSERT OR UPDATE ON public.account_roles
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_account_role();

-- 17.2 Encounter <-> Appointment ownership.
CREATE OR REPLACE FUNCTION public.trg_validate_appointment_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_patient_id uuid;
BEGIN
    IF NEW.appointment_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT patient_id INTO v_patient_id
    FROM public.appointments
    WHERE id = NEW.appointment_id;

    IF v_patient_id IS NULL OR v_patient_id <> NEW.patient_id THEN
        RAISE EXCEPTION 'Appointment % does not belong to encounter patient %', NEW.appointment_id, NEW.patient_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_encounters_validate_appointment_scope
BEFORE INSERT OR UPDATE OF appointment_id, patient_id ON public.encounters
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_appointment_scope();

-- 17.3 Assessment version scope, correction lineage and immutability.
CREATE OR REPLACE FUNCTION public.trg_validate_assessment_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_assessment_encounter uuid;
    v_vital_encounter uuid;
    v_prev_status text;
    v_prev_no integer;
BEGIN
    IF NEW.vital_sign_id IS NOT NULL THEN
        SELECT a.encounter_id INTO v_assessment_encounter
        FROM public.encounter_assessments a
        WHERE a.id = NEW.encounter_assessment_id;

        SELECT v.encounter_id INTO v_vital_encounter
        FROM public.vital_signs v
        WHERE v.id = NEW.vital_sign_id;

        IF v_vital_encounter IS NULL OR v_vital_encounter <> v_assessment_encounter THEN
            RAISE EXCEPTION 'Vital sign % is not in the same Encounter as assessment %', NEW.vital_sign_id, NEW.encounter_assessment_id
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NEW.corrects_version_id IS NOT NULL THEN
        SELECT status, version_no INTO v_prev_status, v_prev_no
        FROM public.encounter_assessment_versions
        WHERE encounter_assessment_id = NEW.encounter_assessment_id
          AND id = NEW.corrects_version_id;

        IF v_prev_status IS DISTINCT FROM 'FINAL' OR v_prev_no >= NEW.version_no THEN
            RAISE EXCEPTION 'Assessment correction must reference an earlier FINAL version in the same root'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_assessment_versions_validate
BEFORE INSERT OR UPDATE ON public.encounter_assessment_versions
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_assessment_version();

CREATE OR REPLACE FUNCTION public.trg_protect_final_assessment_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'FINAL' THEN
        RAISE EXCEPTION 'FINAL assessment version % is immutable', OLD.id USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_assessment_versions_immutable
BEFORE UPDATE OR DELETE ON public.encounter_assessment_versions
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_final_assessment_version();

CREATE OR REPLACE FUNCTION public.trg_protect_final_assessment_children()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_version_id uuid;
    v_status text;
BEGIN
    v_version_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.assessment_version_id ELSE NEW.assessment_version_id END;
    SELECT status INTO v_status FROM public.encounter_assessment_versions WHERE id = v_version_id;
    IF v_status = 'FINAL' THEN
        RAISE EXCEPTION 'Children of FINAL assessment version % are immutable', v_version_id USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_diagnoses_immutable_after_final
BEFORE INSERT OR UPDATE OR DELETE ON public.diagnoses
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_final_assessment_children();

CREATE OR REPLACE FUNCTION public.trg_protect_referenced_vital_sign()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.encounter_assessment_versions
        WHERE vital_sign_id = OLD.id AND status = 'FINAL'
    ) THEN
        RAISE EXCEPTION 'Vital sign % is referenced by a FINAL assessment and is immutable', OLD.id USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_vital_signs_immutable_when_final_referenced
BEFORE UPDATE OR DELETE ON public.vital_signs
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_referenced_vital_sign();

-- 17.4 Diagnostic result version lineage, required LAB values and immutability.
CREATE OR REPLACE FUNCTION public.trg_validate_result_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_prev_status text;
    v_prev_no integer;
    v_result_type text;
BEGIN
    IF NEW.corrects_version_id IS NOT NULL THEN
        SELECT status, version_no INTO v_prev_status, v_prev_no
        FROM public.result_versions
        WHERE result_series_id = NEW.result_series_id
          AND id = NEW.corrects_version_id;

        IF v_prev_status NOT IN ('FINAL', 'CORRECTED') OR v_prev_no >= NEW.version_no THEN
            RAISE EXCEPTION 'Result correction must reference an earlier official version in the same series'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF NEW.status IN ('FINAL', 'CORRECTED') THEN
        SELECT result_type INTO v_result_type
        FROM public.result_series
        WHERE id = NEW.result_series_id;

        IF v_result_type = 'LAB' THEN
            IF NOT EXISTS (
                SELECT 1 FROM public.lab_result_items i WHERE i.result_version_id = NEW.id
            ) THEN
                RAISE EXCEPTION 'LAB result version % cannot be finalized without snapshot analyte items', NEW.id
                    USING ERRCODE = '23514';
            END IF;

            IF EXISTS (
                SELECT 1
                FROM public.lab_result_items i
                WHERE i.result_version_id = NEW.id
                  AND i.required_snapshot
                  AND (
                      (i.value_type = 'NUMERIC' AND i.numeric_value IS NULL)
                      OR (i.value_type = 'TEXT' AND (i.text_value IS NULL OR btrim(i.text_value) = ''))
                      OR (i.value_type = 'BOOLEAN' AND i.boolean_value IS NULL)
                  )
            ) THEN
                RAISE EXCEPTION 'Required LAB analyte value is missing for result version %', NEW.id
                    USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_result_versions_validate
AFTER INSERT OR UPDATE ON public.result_versions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_result_version();

CREATE OR REPLACE FUNCTION public.trg_protect_official_result_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status IN ('FINAL', 'CORRECTED') THEN
        RAISE EXCEPTION 'Official result version % is immutable', OLD.id USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_result_versions_immutable
BEFORE UPDATE OR DELETE ON public.result_versions
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_official_result_version();

CREATE OR REPLACE FUNCTION public.trg_protect_official_result_children()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_version_id uuid;
    v_status text;
BEGIN
    v_version_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.result_version_id ELSE NEW.result_version_id END;
    SELECT status INTO v_status FROM public.result_versions WHERE id = v_version_id;
    IF v_status IN ('FINAL', 'CORRECTED') THEN
        RAISE EXCEPTION 'Children of official result version % are immutable', v_version_id USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_lab_result_items_immutable_after_official
BEFORE INSERT OR UPDATE OR DELETE ON public.lab_result_items
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_official_result_children();

-- 17.5 Billing line scope and historical protection.
CREATE OR REPLACE FUNCTION public.trg_validate_invoice_line()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_invoice_encounter uuid;
    v_request_encounter uuid;
    v_request_service uuid;
    v_invoice_status text;
BEGIN
    SELECT encounter_id, status INTO v_invoice_encounter, v_invoice_status
    FROM public.invoices
    WHERE id = NEW.invoice_id;

    IF NEW.service_request_id IS NOT NULL THEN
        SELECT o.encounter_id, sr.service_id INTO v_request_encounter, v_request_service
        FROM public.service_requests sr
        JOIN public.order_rounds o ON o.id = sr.order_round_id
        WHERE sr.id = NEW.service_request_id;

        IF v_request_encounter IS NULL
           OR v_request_encounter <> v_invoice_encounter
           OR v_request_service <> NEW.service_id THEN
            RAISE EXCEPTION 'Invoice line service request does not match Invoice Encounter/service'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF TG_OP = 'INSERT' AND v_invoice_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Cannot add invoice line to non-DRAFT invoice %', NEW.invoice_id USING ERRCODE = '55000';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_invoice_lines_validate
BEFORE INSERT OR UPDATE ON public.invoice_lines
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_invoice_line();

CREATE OR REPLACE FUNCTION public.trg_protect_invoice_line_after_issue()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_status text;
BEGIN
    SELECT status INTO v_status
    FROM public.invoices
    WHERE id = OLD.invoice_id;

    IF v_status <> 'DRAFT' THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'Invoice line % cannot be deleted after invoice issue', OLD.id USING ERRCODE = '55000';
        END IF;

        IF NEW.invoice_id IS DISTINCT FROM OLD.invoice_id
           OR NEW.service_request_id IS DISTINCT FROM OLD.service_request_id
           OR NEW.service_id IS DISTINCT FROM OLD.service_id
           OR NEW.description_snapshot IS DISTINCT FROM OLD.description_snapshot
           OR NEW.quantity IS DISTINCT FROM OLD.quantity
           OR NEW.unit_price IS DISTINCT FROM OLD.unit_price
           OR NEW.line_amount IS DISTINCT FROM OLD.line_amount THEN
            RAISE EXCEPTION 'Invoice line financial/content fields are immutable after issue' USING ERRCODE = '55000';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_invoice_lines_protect_after_issue
BEFORE UPDATE OR DELETE ON public.invoice_lines
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_invoice_line_after_issue();

CREATE OR REPLACE FUNCTION public.trg_protect_confirmed_payment()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'CONFIRMED' THEN
        RAISE EXCEPTION 'CONFIRMED payment % is immutable; use Refund for reversal', OLD.id USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payments_immutable_after_confirmed
BEFORE UPDATE OR DELETE ON public.payments
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_confirmed_payment();

CREATE OR REPLACE FUNCTION public.trg_validate_refund()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_payment_amount numeric(14,2);
    v_payment_status text;
    v_reserved numeric(14,2);
BEGIN
    SELECT amount, status INTO v_payment_amount, v_payment_status
    FROM public.payments
    WHERE id = NEW.payment_id
    FOR UPDATE;

    IF v_payment_status IS DISTINCT FROM 'CONFIRMED' THEN
        RAISE EXCEPTION 'Refund requires a CONFIRMED payment' USING ERRCODE = '23514';
    END IF;

    IF NEW.status IN ('APPROVED', 'PROCESSING', 'REFUNDED') THEN
        SELECT COALESCE(sum(r.amount), 0) INTO v_reserved
        FROM public.refunds r
        WHERE r.payment_id = NEW.payment_id
          AND r.status IN ('APPROVED', 'PROCESSING', 'REFUNDED')
          AND (TG_OP = 'INSERT' OR r.id <> NEW.id);

        IF v_reserved + NEW.amount > v_payment_amount THEN
            RAISE EXCEPTION 'Reserved/refunded amount exceeds payment amount' USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_refunds_validate
BEFORE INSERT OR UPDATE ON public.refunds
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_refund();

CREATE OR REPLACE FUNCTION public.trg_protect_refunded_refund()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'REFUNDED' THEN
        RAISE EXCEPTION 'REFUNDED refund % is immutable', OLD.id USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_refunds_immutable_after_refunded
BEFORE UPDATE OR DELETE ON public.refunds
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_refunded_refund();

-- Reusable check for PAYMENT-backed authorization validity.
CREATE OR REPLACE FUNCTION public.payment_backing_is_valid(
    p_service_request_id uuid,
    p_invoice_id uuid,
    p_payment_id uuid
) RETURNS boolean
LANGUAGE plpgsql
VOLATILE
AS $$
DECLARE
    v_payment_status text;
    v_payment_invoice uuid;
    v_invoice_due numeric(14,2);
    v_net_available numeric(14,2);
BEGIN
    SELECT status, invoice_id INTO v_payment_status, v_payment_invoice
    FROM public.payments WHERE id = p_payment_id;

    IF v_payment_status IS DISTINCT FROM 'CONFIRMED' OR v_payment_invoice IS DISTINCT FROM p_invoice_id THEN
        RETURN false;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM public.invoice_lines l
        WHERE l.invoice_id = p_invoice_id
          AND l.service_request_id = p_service_request_id
          AND l.status = 'ACTIVE'
    ) THEN
        RETURN false;
    END IF;

    SELECT COALESCE(sum(line_amount), 0) INTO v_invoice_due
    FROM public.invoice_lines
    WHERE invoice_id = p_invoice_id AND status = 'ACTIVE';

    SELECT
        COALESCE((SELECT sum(amount) FROM public.payments WHERE invoice_id = p_invoice_id AND status = 'CONFIRMED'), 0)
        - COALESCE((
            SELECT sum(r.amount)
            FROM public.refunds r
            JOIN public.payments p ON p.id = r.payment_id
            WHERE p.invoice_id = p_invoice_id AND r.status IN ('REFUNDED', 'APPROVED', 'PROCESSING')
          ), 0)
    INTO v_net_available;

    RETURN v_net_available >= v_invoice_due;
END;
$$;

CREATE OR REPLACE FUNCTION public.trg_validate_service_authorization()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = 'AUTHORIZED' THEN
        IF NOT public.payment_backing_is_valid(NEW.service_request_id, NEW.source_invoice_id, NEW.source_payment_id) THEN
            RAISE EXCEPTION 'PAYMENT authorization provenance/coverage is invalid for ServiceRequest %', NEW.service_request_id
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_service_authorizations_validate
BEFORE INSERT OR UPDATE ON public.service_authorizations
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_service_authorization();

CREATE OR REPLACE FUNCTION public.trg_validate_service_start_gate()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_status text;
    v_source text;
    v_invoice_id uuid;
    v_payment_id uuid;
BEGIN
    IF NEW.status = 'IN_PROGRESS' AND (TG_OP = 'INSERT' OR OLD.status IS DISTINCT FROM 'IN_PROGRESS') THEN
        SELECT status, authorization_source, source_invoice_id, source_payment_id
        INTO v_status, v_source, v_invoice_id, v_payment_id
        FROM public.service_authorizations
        WHERE service_request_id = NEW.id;

        IF v_status IS NULL OR v_status NOT IN ('AUTHORIZED', 'NOT_REQUIRED') THEN
            RAISE EXCEPTION 'ServiceRequest % cannot start without authorization/payment policy', NEW.id
                USING ERRCODE = '23514';
        END IF;

        IF v_status = 'AUTHORIZED'
           AND NOT public.payment_backing_is_valid(NEW.id, v_invoice_id, v_payment_id) THEN
            RAISE EXCEPTION 'ServiceRequest % payment authorization is no longer backed', NEW.id
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_service_requests_payment_gate
BEFORE INSERT OR UPDATE OF status ON public.service_requests
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_service_start_gate();

-- 17.6 Prescription official-version protection.
CREATE OR REPLACE FUNCTION public.trg_validate_prescription_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_prev_status text;
    v_prev_no integer;
BEGIN
    IF NEW.corrects_version_id IS NOT NULL THEN
        SELECT status, version_no INTO v_prev_status, v_prev_no
        FROM public.prescription_versions
        WHERE prescription_id = NEW.prescription_id
          AND id = NEW.corrects_version_id;

        IF v_prev_status NOT IN ('ISSUED', 'CORRECTED') OR v_prev_no >= NEW.version_no THEN
            RAISE EXCEPTION 'Prescription correction must reference an earlier official version in the same root'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_prescription_versions_validate
BEFORE INSERT OR UPDATE ON public.prescription_versions
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_prescription_version();

CREATE OR REPLACE FUNCTION public.trg_protect_official_prescription_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status IN ('ISSUED', 'CORRECTED') THEN
        RAISE EXCEPTION 'Official prescription version % is immutable', OLD.id USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_prescription_versions_immutable
BEFORE UPDATE OR DELETE ON public.prescription_versions
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_official_prescription_version();

CREATE OR REPLACE FUNCTION public.trg_protect_official_prescription_children()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_version_id uuid;
    v_status text;
BEGIN
    v_version_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.prescription_version_id ELSE NEW.prescription_version_id END;
    SELECT status INTO v_status FROM public.prescription_versions WHERE id = v_version_id;
    IF v_status IN ('ISSUED', 'CORRECTED') THEN
        RAISE EXCEPTION 'Items of official prescription version % are immutable', v_version_id USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_prescription_items_immutable_after_official
BEFORE INSERT OR UPDATE OR DELETE ON public.prescription_items
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_official_prescription_children();

-- 17.7 Health Examination import/source/scope validation.
CREATE OR REPLACE FUNCTION public.trg_validate_batch_participant()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_job_type text;
    v_job_batch uuid;
    v_job_status text;
BEGIN
    IF NEW.import_job_id IS NOT NULL THEN
        SELECT import_type, batch_id, status INTO v_job_type, v_job_batch, v_job_status
        FROM public.import_jobs
        WHERE id = NEW.import_job_id;

        IF v_job_type IS DISTINCT FROM 'ORGANIZATION_PARTICIPANT'
           OR v_job_batch IS DISTINCT FROM NEW.batch_id
           OR v_job_status NOT IN ('VALIDATED', 'CONFIRMED') THEN
            RAISE EXCEPTION 'BatchParticipant import_job_id must reference ORGANIZATION_PARTICIPANT job in the same Batch'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    -- If a record already exists, changing patient_id must preserve record Encounter ownership.
    IF NEW.patient_id IS NOT NULL AND EXISTS (
        SELECT 1
        FROM public.health_examination_records r
        JOIN public.encounters e ON e.id = r.encounter_id
        WHERE r.batch_participant_id = NEW.id
          AND r.status <> 'CANCELLED'
          AND e.patient_id <> NEW.patient_id
    ) THEN
        RAISE EXCEPTION 'BatchParticipant patient_id would break existing HealthExaminationRecord ownership'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_health_exam_batch_participants_validate
BEFORE INSERT OR UPDATE ON public.health_examination_batch_participants
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_batch_participant();

CREATE OR REPLACE FUNCTION public.trg_validate_health_exam_record_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_participant_patient uuid;
    v_encounter_patient uuid;
BEGIN
    IF NEW.batch_participant_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT patient_id INTO v_participant_patient
    FROM public.health_examination_batch_participants
    WHERE id = NEW.batch_participant_id;

    SELECT patient_id INTO v_encounter_patient
    FROM public.encounters
    WHERE id = NEW.encounter_id;

    IF v_participant_patient IS NULL OR v_encounter_patient IS NULL OR v_participant_patient <> v_encounter_patient THEN
        RAISE EXCEPTION 'Corporate HealthExaminationRecord must use the BatchParticipant Patient/Encounter'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_health_exam_records_validate_scope
BEFORE INSERT OR UPDATE OF batch_participant_id, encounter_id ON public.health_examination_records
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_health_exam_record_scope();

CREATE OR REPLACE FUNCTION public.trg_validate_participant_service_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_batch_service_id uuid;
    v_catalog_service_id uuid;
    v_participant_patient uuid;
    v_request_service uuid;
    v_request_encounter uuid;
    v_request_patient uuid;
BEGIN
    SELECT bs.service_id INTO v_catalog_service_id
    FROM public.health_examination_batch_services bs
    WHERE bs.batch_id = NEW.batch_id AND bs.id = NEW.batch_service_id;

    IF v_catalog_service_id IS NULL THEN
        RAISE EXCEPTION 'BatchService does not belong to ParticipantService batch' USING ERRCODE = '23514';
    END IF;

    IF NEW.service_request_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT be.patient_id INTO v_participant_patient
    FROM public.health_examination_batch_participants be
    WHERE be.batch_id = NEW.batch_id AND be.id = NEW.batch_participant_id;

    IF v_participant_patient IS NULL THEN
        RAISE EXCEPTION 'Cannot link ServiceRequest before BatchParticipant has a prepared Patient'
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
        WHERE r.batch_participant_id = NEW.batch_participant_id
          AND r.encounter_id = v_request_encounter
          AND r.status <> 'CANCELLED'
    ) THEN
        RAISE EXCEPTION 'ParticipantService ServiceRequest is not in the active HealthExaminationRecord Encounter'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_health_exam_participant_services_validate_scope
BEFORE INSERT OR UPDATE ON public.health_examination_participant_services
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_participant_service_scope();

CREATE OR REPLACE FUNCTION public.trg_protect_service_request_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF (NEW.order_round_id IS DISTINCT FROM OLD.order_round_id OR NEW.service_id IS DISTINCT FROM OLD.service_id)
       AND (
           EXISTS (SELECT 1 FROM public.result_series rs WHERE rs.service_request_id = OLD.id)
           OR EXISTS (SELECT 1 FROM public.invoice_lines l WHERE l.service_request_id = OLD.id AND l.status = 'ACTIVE')
           OR EXISTS (SELECT 1 FROM public.health_examination_participant_services es WHERE es.service_request_id = OLD.id)
       ) THEN
        RAISE EXCEPTION 'ServiceRequest identity cannot change after results, billing, or Health Examination linkage exists'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_service_requests_protect_identity
BEFORE UPDATE OF order_round_id, service_id ON public.service_requests
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_service_request_identity();

-- 17.8 Health Examination snapshot/version item immutability and completion checks.
CREATE OR REPLACE FUNCTION public.trg_protect_issued_snapshot()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'ISSUED' THEN
        RAISE EXCEPTION 'ISSUED Health Examination snapshot % is immutable', OLD.id USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_health_exam_record_snapshots_immutable
BEFORE UPDATE OR DELETE ON public.health_examination_record_snapshots
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_issued_snapshot();

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
    SELECT rv.health_examination_record_id, r.encounter_id, r.batch_participant_id
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
        JOIN public.health_examination_batch_services bs ON bs.id = es.batch_service_id
        WHERE es.id = NEW.participant_service_id
          AND es.batch_participant_id = v_record_participant
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

CREATE TRIGGER trg_health_exam_record_version_items_validate
BEFORE INSERT OR UPDATE ON public.health_examination_record_version_items
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_record_version_item();

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

        SELECT batch_participant_id INTO v_participant_id
        FROM public.health_examination_records
        WHERE id = NEW.health_examination_record_id;

        IF v_participant_id IS NOT NULL THEN
            SELECT service_reconciliation_status INTO v_reconciliation
            FROM public.health_examination_batch_participants
            WHERE id = v_participant_id;

            IF v_reconciliation IS DISTINCT FROM 'RECONCILED' THEN
                RAISE EXCEPTION 'Corporate RecordVersion cannot complete before Participant services are RECONCILED'
                    USING ERRCODE = '23514';
            END IF;

            IF EXISTS (
                SELECT 1
                FROM public.health_examination_participant_services es
                WHERE es.batch_participant_id = v_participant_id
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

CREATE CONSTRAINT TRIGGER trg_health_exam_record_versions_validate_official
AFTER INSERT OR UPDATE ON public.health_examination_record_versions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_record_version_official();

CREATE OR REPLACE FUNCTION public.trg_protect_official_record_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'ISSUED' THEN
        RAISE EXCEPTION 'ISSUED Health Examination RecordVersion % is immutable', OLD.id USING ERRCODE = '55000';
    END IF;

    IF OLD.status = 'COMPLETED' THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'COMPLETED Health Examination RecordVersion % cannot be deleted', OLD.id USING ERRCODE = '55000';
        END IF;

        IF NEW.status <> 'ISSUED'
           OR NEW.health_examination_record_id IS DISTINCT FROM OLD.health_examination_record_id
           OR NEW.version_no IS DISTINCT FROM OLD.version_no
           OR NEW.administrative_snapshot_id IS DISTINCT FROM OLD.administrative_snapshot_id
           OR NEW.conclusion_text IS DISTINCT FROM OLD.conclusion_text
           OR NEW.conclusion_by IS DISTINCT FROM OLD.conclusion_by
           OR NEW.conclusion_at IS DISTINCT FROM OLD.conclusion_at
           OR NEW.corrects_version_id IS DISTINCT FROM OLD.corrects_version_id
           OR NEW.correction_reason IS DISTINCT FROM OLD.correction_reason
           OR NEW.completed_by IS DISTINCT FROM OLD.completed_by
           OR NEW.completed_at IS DISTINCT FROM OLD.completed_at
           OR NEW.authored_by IS DISTINCT FROM OLD.authored_by
           OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
            RAISE EXCEPTION 'COMPLETED RecordVersion may only transition to ISSUED by adding issue metadata'
                USING ERRCODE = '55000';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_health_exam_record_versions_immutable
BEFORE UPDATE OR DELETE ON public.health_examination_record_versions
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_official_record_version();

CREATE OR REPLACE FUNCTION public.trg_protect_official_record_version_items()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_version_id uuid;
    v_status text;
BEGIN
    v_version_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.record_version_id ELSE NEW.record_version_id END;
    SELECT status INTO v_status
    FROM public.health_examination_record_versions
    WHERE id = v_version_id;

    IF v_status IN ('COMPLETED', 'ISSUED') THEN
        RAISE EXCEPTION 'Items of official Health Examination RecordVersion % are immutable', v_version_id
            USING ERRCODE = '55000';
    END IF;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_health_exam_record_version_items_immutable
BEFORE INSERT OR UPDATE OR DELETE ON public.health_examination_record_version_items
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_official_record_version_items();

-- 17.9 Document effective-period validation and issued metadata immutability.
CREATE OR REPLACE FUNCTION public.trg_validate_template_version_period()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- Serialize edits per logical template to prevent concurrent overlapping ranges.
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.template_id::text, 0));

    IF EXISTS (
        SELECT 1
        FROM public.template_versions tv
        WHERE tv.template_id = NEW.template_id
          AND tv.id <> NEW.id
          AND tstzrange(tv.active_from, COALESCE(tv.retired_at, 'infinity'::timestamptz), '[)')
              && tstzrange(NEW.active_from, COALESCE(NEW.retired_at, 'infinity'::timestamptz), '[)')
    ) THEN
        RAISE EXCEPTION 'TemplateVersion effective periods overlap for template %', NEW.template_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_document_template_versions_period
BEFORE INSERT OR UPDATE OF template_id, active_from, retired_at ON public.template_versions
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_template_version_period();

CREATE OR REPLACE FUNCTION public.trg_validate_service_template_mapping_period()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.service_id::text, 0));

    IF EXISTS (
        SELECT 1
        FROM public.service_template_mappings m
        WHERE m.service_id = NEW.service_id
          AND m.id <> NEW.id
          AND tstzrange(m.active_from, COALESCE(m.retired_at, 'infinity'::timestamptz), '[)')
              && tstzrange(NEW.active_from, COALESCE(NEW.retired_at, 'infinity'::timestamptz), '[)')
    ) THEN
        RAISE EXCEPTION 'Service->Template mapping effective periods overlap for service %', NEW.service_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_document_service_template_mappings_period
BEFORE INSERT OR UPDATE OF service_id, active_from, retired_at ON public.service_template_mappings
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_service_template_mapping_period();

CREATE OR REPLACE FUNCTION public.trg_protect_referenced_file()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.template_versions tv WHERE tv.file_id = OLD.id) THEN
        RAISE EXCEPTION 'Document source file % is referenced by a TemplateVersion and cannot be changed/deleted', OLD.id
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_document_files_immutable_when_referenced
BEFORE UPDATE OR DELETE ON public.files
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_referenced_file();

CREATE OR REPLACE FUNCTION public.trg_protect_issued_template_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.issued_representations ir WHERE ir.template_version_id = OLD.id
    ) THEN
        RAISE EXCEPTION 'TemplateVersion % has been used for an issued representation and is immutable', OLD.id
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_document_template_versions_immutable_when_issued
BEFORE UPDATE OR DELETE ON public.template_versions
FOR EACH ROW EXECUTE FUNCTION public.trg_protect_issued_template_version();

CREATE OR REPLACE FUNCTION public.trg_validate_issued_representation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_status text;
BEGIN
    IF NEW.health_examination_record_snapshot_id IS NOT NULL THEN
        SELECT status INTO v_status
        FROM public.health_examination_record_snapshots
        WHERE id = NEW.health_examination_record_snapshot_id;
        IF v_status IS DISTINCT FROM 'ISSUED' THEN
            RAISE EXCEPTION 'IssuedRepresentation snapshot source must be ISSUED' USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.health_examination_record_version_id IS NOT NULL THEN
        SELECT status INTO v_status
        FROM public.health_examination_record_versions
        WHERE id = NEW.health_examination_record_version_id;
        IF v_status NOT IN ('COMPLETED', 'ISSUED') THEN
            RAISE EXCEPTION 'IssuedRepresentation record version source must be COMPLETED/ISSUED' USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.result_version_id IS NOT NULL THEN
        SELECT status INTO v_status FROM public.result_versions WHERE id = NEW.result_version_id;
        IF v_status NOT IN ('FINAL', 'CORRECTED') THEN
            RAISE EXCEPTION 'IssuedRepresentation result source must be FINAL/CORRECTED' USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.prescription_version_id IS NOT NULL THEN
        SELECT status INTO v_status FROM public.prescription_versions WHERE id = NEW.prescription_version_id;
        IF v_status NOT IN ('ISSUED', 'CORRECTED') THEN
            RAISE EXCEPTION 'IssuedRepresentation prescription source must be ISSUED/CORRECTED' USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.assessment_version_id IS NOT NULL THEN
        SELECT status INTO v_status FROM public.encounter_assessment_versions WHERE id = NEW.assessment_version_id;
        IF v_status IS DISTINCT FROM 'FINAL' THEN
            RAISE EXCEPTION 'IssuedRepresentation assessment source must be FINAL' USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_document_issued_representations_validate
BEFORE INSERT ON public.issued_representations
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_issued_representation();

CREATE OR REPLACE FUNCTION public.trg_reject_issued_representation_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'IssuedRepresentation % is immutable', OLD.id USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_document_issued_representations_immutable
BEFORE UPDATE OR DELETE ON public.issued_representations
FOR EACH ROW EXECUTE FUNCTION public.trg_reject_issued_representation_mutation();

-- Resolve the Patient owner of an issued representation for Portal scope checks.
CREATE OR REPLACE FUNCTION public.issued_representation_patient_id(p_representation_id uuid)
RETURNS uuid
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_patient_id uuid;
BEGIN
    SELECT e.patient_id INTO v_patient_id
    FROM public.issued_representations ir
    JOIN public.health_examination_record_snapshots s
      ON s.id = ir.health_examination_record_snapshot_id
    JOIN public.health_examination_records r
      ON r.id = s.health_examination_record_id
    JOIN public.encounters e ON e.id = r.encounter_id
    WHERE ir.id = p_representation_id
      AND ir.health_examination_record_snapshot_id IS NOT NULL;
    IF v_patient_id IS NOT NULL THEN RETURN v_patient_id; END IF;

    SELECT e.patient_id INTO v_patient_id
    FROM public.issued_representations ir
    JOIN public.health_examination_record_versions rv
      ON rv.id = ir.health_examination_record_version_id
    JOIN public.health_examination_records r
      ON r.id = rv.health_examination_record_id
    JOIN public.encounters e ON e.id = r.encounter_id
    WHERE ir.id = p_representation_id
      AND ir.health_examination_record_version_id IS NOT NULL;
    IF v_patient_id IS NOT NULL THEN RETURN v_patient_id; END IF;

    SELECT e.patient_id INTO v_patient_id
    FROM public.issued_representations ir
    JOIN public.result_versions rv ON rv.id = ir.result_version_id
    JOIN public.result_series rs ON rs.id = rv.result_series_id
    JOIN public.service_requests sr ON sr.id = rs.service_request_id
    JOIN public.order_rounds o ON o.id = sr.order_round_id
    JOIN public.encounters e ON e.id = o.encounter_id
    WHERE ir.id = p_representation_id
      AND ir.result_version_id IS NOT NULL;
    IF v_patient_id IS NOT NULL THEN RETURN v_patient_id; END IF;

    SELECT e.patient_id INTO v_patient_id
    FROM public.issued_representations ir
    JOIN public.prescription_versions pv ON pv.id = ir.prescription_version_id
    JOIN public.prescriptions p ON p.id = pv.prescription_id
    JOIN public.encounters e ON e.id = p.encounter_id
    WHERE ir.id = p_representation_id
      AND ir.prescription_version_id IS NOT NULL;
    IF v_patient_id IS NOT NULL THEN RETURN v_patient_id; END IF;

    SELECT e.patient_id INTO v_patient_id
    FROM public.issued_representations ir
    JOIN public.encounter_assessment_versions av ON av.id = ir.assessment_version_id
    JOIN public.encounter_assessments a ON a.id = av.encounter_assessment_id
    JOIN public.encounters e ON e.id = a.encounter_id
    WHERE ir.id = p_representation_id
      AND ir.assessment_version_id IS NOT NULL;

    RETURN v_patient_id;
END;
$$;

-- 17.10 Integration staging lifecycle and source scope.
CREATE OR REPLACE FUNCTION public.trg_validate_import_job_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.status IN ('CONFIRMED', 'CANCELLED', 'EXPIRED')
           AND NEW.status IS DISTINCT FROM OLD.status THEN
            RAISE EXCEPTION 'Terminal ImportJob % cannot change status from %', OLD.id, OLD.status
                USING ERRCODE = '55000';
        END IF;

        IF NEW.status = 'CONFIRMED'
           AND OLD.status <> 'CONFIRMED'
           AND NEW.expires_at IS NOT NULL
           AND NEW.expires_at <= CURRENT_TIMESTAMP THEN
            RAISE EXCEPTION 'Expired ImportJob % cannot be confirmed', NEW.id USING ERRCODE = '23514';
        END IF;

        IF OLD.status <> 'VALIDATED'
           AND NEW.configuration IS DISTINCT FROM OLD.configuration THEN
            RAISE EXCEPTION 'ImportJob % configuration can only change while VALIDATED', OLD.id USING ERRCODE = '55000';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_import_jobs_validate_transition
BEFORE UPDATE ON public.import_jobs
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_import_job_transition();

CREATE OR REPLACE FUNCTION public.trg_validate_import_row()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_job_status text;
BEGIN
    SELECT status INTO v_job_status FROM public.import_jobs WHERE id = NEW.job_id;

    IF TG_OP = 'INSERT' AND (v_job_status <> 'VALIDATED' OR NEW.normalized_payload IS NULL) THEN
        RAISE EXCEPTION 'ImportRow can only be staged with normalized_payload for a VALIDATED job'
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF OLD.committed_resource_id IS NOT NULL
           AND (NEW.committed_resource_id IS DISTINCT FROM OLD.committed_resource_id
                OR NEW.committed_resource_type IS DISTINCT FROM OLD.committed_resource_type) THEN
            RAISE EXCEPTION 'Committed ImportRow resource link is immutable' USING ERRCODE = '55000';
        END IF;

        IF NEW.normalized_payload IS NULL AND OLD.normalized_payload IS NOT NULL
           AND v_job_status NOT IN ('CONFIRMED', 'CANCELLED', 'EXPIRED') THEN
            RAISE EXCEPTION 'ImportRow payload can be purged only after terminal job state'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_import_rows_validate
BEFORE INSERT OR UPDATE ON public.import_rows
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_import_row();

CREATE OR REPLACE FUNCTION public.trg_validate_health_data_submission_scope()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_encounter_patient uuid;
BEGIN
    SELECT patient_id INTO v_encounter_patient
    FROM public.encounters
    WHERE id = NEW.encounter_id;

    IF v_encounter_patient IS NULL OR v_encounter_patient <> NEW.patient_id THEN
        RAISE EXCEPTION 'HealthDataSubmission patient_id must match Encounter patient'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_health_data_submissions_validate_scope
BEFORE INSERT OR UPDATE OF patient_id, encounter_id ON public.health_data_submissions
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_health_data_submission_scope();

-- 17.11 Portal release ownership and official-source checks.
CREATE OR REPLACE FUNCTION public.trg_validate_result_release()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_status text;
    v_patient_id uuid;
BEGIN
    SELECT rv.status, e.patient_id
    INTO v_status, v_patient_id
    FROM public.result_versions rv
    JOIN public.result_series rs ON rs.id = rv.result_series_id
    JOIN public.service_requests sr ON sr.id = rs.service_request_id
    JOIN public.order_rounds o ON o.id = sr.order_round_id
    JOIN public.encounters e ON e.id = o.encounter_id
    WHERE rv.id = NEW.result_version_id;

    IF v_status NOT IN ('FINAL', 'CORRECTED') OR v_patient_id IS DISTINCT FROM NEW.patient_id THEN
        RAISE EXCEPTION 'ResultRelease requires an official result owned by the specified Patient'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_result_releases_validate
BEFORE INSERT OR UPDATE OF result_version_id, patient_id ON public.result_releases
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_result_release();

CREATE OR REPLACE FUNCTION public.trg_validate_document_release()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_patient_id uuid;
BEGIN
    v_patient_id := public.issued_representation_patient_id(NEW.issued_representation_id);
    IF v_patient_id IS NULL OR v_patient_id <> NEW.patient_id THEN
        RAISE EXCEPTION 'DocumentRelease Patient does not own the issued representation source'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_document_releases_validate
BEFORE INSERT OR UPDATE OF issued_representation_id, patient_id ON public.document_releases
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_document_release();

-- 17.12 Notification batch/channel consistency.
CREATE OR REPLACE FUNCTION public.trg_validate_notification_batch_channel()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_batch_channel text;
BEGIN
    IF NEW.batch_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT channel INTO v_batch_channel
    FROM public.notification_batches
    WHERE id = NEW.batch_id;

    IF v_batch_channel IS NULL OR v_batch_channel <> NEW.channel THEN
        RAISE EXCEPTION 'Notification channel must match NotificationBatch channel'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notifications_validate_batch_channel
BEFORE INSERT OR UPDATE OF batch_id, channel ON public.notifications
FOR EACH ROW EXECUTE FUNCTION public.trg_validate_notification_batch_channel();

-- 17.13 Audit is append-only.
CREATE OR REPLACE FUNCTION public.trg_reject_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'public.audit_events is append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_audit_events_append_only
BEFORE UPDATE OR DELETE ON public.audit_events
FOR EACH ROW EXECUTE FUNCTION public.trg_reject_audit_mutation();

-- ============================================================================
-- 18. APPLICATION-CONTRACT NOTES THAT CANNOT BE SAFELY REDUCED TO A CHECK
-- ============================================================================
-- 1) Permissions/roles for each use case are enforced by Spring Security and the
--    application layer; database FKs cannot prove that ordered_by is a DOCTOR.
-- 2) Batch creation must insert at least one BatchDay in the same transaction.
-- 3) Optimistic locking must always update row_version = row_version + 1 with an
--    expectedVersion predicate. No trigger in this file increments row_version.
-- 4) Import validation is all-or-nothing before ImportJob/ImportRow staging.
-- 5) Reconciliation must lock BatchParticipant, update ParticipantService rows,
--    transition reconciliation state, and write audit atomically.
-- 6) Refund/provider/outbox/notification UNKNOWN outcomes require provider-side
--    idempotency/reconciliation; the DB only stores the durable state/fencing.
-- 7) Health Examination complete/issue must also validate form/service-specific
--    required clinical content not expressible from this generic catalog alone.
-- 8) document_type <-> template_type compatibility is an application/document
--    contract because the design does not define a fixed enum mapping.
-- 9) System-generated audit/outbox work may legitimately have no actor account;
--    actor_account_id is nullable rather than inventing a fake user.
