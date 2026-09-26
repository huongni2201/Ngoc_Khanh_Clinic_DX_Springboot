-- PostgreSQL 18 fresh-install schema: final table-design v2.11 shape.
-- Result-release fields follow ADR-0002; PostgreSQL types follow ADR-0004.

CREATE TABLE public.patients (
    id uuid NOT NULL CONSTRAINT pk_patients PRIMARY KEY,
    patient_code varchar(24) NOT NULL,
    identification_number varchar(20) NOT NULL,
    full_name varchar(200) NOT NULL,
    full_name_normalized varchar(200) NOT NULL,
    date_of_birth date NOT NULL,
    sex varchar(16) NOT NULL,
    phone varchar(30) NULL,
    email varchar(200) NULL,
    address varchar(500) NULL,
    ward_code varchar(20) NULL,
    province_code varchar(20) NULL,
    occupation varchar(200) NULL,
    note varchar(1000) NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE public.departments (
    id uuid NOT NULL CONSTRAINT pk_departments PRIMARY KEY,
    department_code varchar(30) NOT NULL,
    department_name varchar(200) NOT NULL,
    department_type varchar(32) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT uq_departments_department_code UNIQUE (department_code)
);

CREATE TABLE public.staff (
    id uuid NOT NULL CONSTRAINT pk_staff PRIMARY KEY,
    staff_code varchar(30) NOT NULL,
    full_name varchar(200) NOT NULL,
    staff_type varchar(32) NOT NULL,
    license_number varchar(100) NULL,
    phone varchar(30) NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_staff_staff_code UNIQUE (staff_code)
);

CREATE TABLE public.diagnosis_catalog (
    id uuid NOT NULL CONSTRAINT pk_diagnosis_catalog PRIMARY KEY,
    diagnosis_code varchar(20) NOT NULL,
    name_vi varchar(500) NOT NULL,
    name_en varchar(500) NULL,
    parent_code varchar(20) NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_diagnosis_catalog_diagnosis_code UNIQUE (diagnosis_code)
);

CREATE TABLE public.medications (
    id uuid NOT NULL CONSTRAINT pk_medications PRIMARY KEY,
    medication_code varchar(40) NOT NULL,
    medication_name varchar(300) NOT NULL,
    generic_name varchar(300) NULL,
    strength varchar(100) NULL,
    dosage_form varchar(100) NULL,
    default_route varchar(100) NULL,
    unit varchar(50) NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_medications_medication_code UNIQUE (medication_code)
);

CREATE TABLE public.document_templates (
    id uuid NOT NULL CONSTRAINT pk_document_templates PRIMARY KEY,
    template_code varchar(50) NOT NULL,
    template_name varchar(250) NOT NULL,
    template_type varchar(40) NOT NULL,
    barcode_policy varchar(24) NOT NULL DEFAULT 'NONE',
    is_master_health_examination_form boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT uq_document_templates_template_code UNIQUE (template_code)
);

CREATE TABLE public.organizations (
    id uuid NOT NULL CONSTRAINT pk_organizations PRIMARY KEY,
    organization_code varchar(40) NOT NULL,
    organization_name varchar(300) NOT NULL,
    tax_code varchar(40) NULL,
    address varchar(500) NULL,
    contact_name varchar(200) NOT NULL,
    contact_phone varchar(30) NOT NULL,
    contact_job_title varchar(150) NULL,
    note varchar(1000) NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_organizations_organization_code UNIQUE (organization_code)
);

CREATE TABLE public.lab_panels (
    id uuid NOT NULL CONSTRAINT pk_lab_panels PRIMARY KEY,
    panel_code varchar(40) NOT NULL,
    panel_name varchar(250) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_lab_panels_panel_code UNIQUE (panel_code)
);

CREATE TABLE public.analytes (
    id uuid NOT NULL CONSTRAINT pk_analytes PRIMARY KEY,
    analyte_code varchar(40) NOT NULL,
    analyte_name varchar(250) NOT NULL,
    default_unit varchar(40) NULL,
    value_type varchar(20) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_analytes_analyte_code UNIQUE (analyte_code)
);

CREATE TABLE public.roles (
    id uuid NOT NULL CONSTRAINT pk_roles PRIMARY KEY,
    role_code varchar(40) NOT NULL,
    role_name varchar(150) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_roles_role_code UNIQUE (role_code)
);

CREATE TABLE public.permissions (
    id uuid NOT NULL CONSTRAINT pk_permissions PRIMARY KEY,
    permission_code varchar(80) NOT NULL,
    module varchar(40) NOT NULL,
    description varchar(300) NULL,
    CONSTRAINT uq_permissions_permission_code UNIQUE (permission_code)
);

CREATE TABLE public.integration_endpoints (
    id uuid NOT NULL CONSTRAINT pk_integration_endpoints PRIMARY KEY,
    endpoint_code varchar(40) NOT NULL,
    integration_type varchar(32) NOT NULL,
    endpoint_name varchar(200) NOT NULL,
    base_url varchar(500) NULL,
    credential_reference varchar(300) NULL,
    config_json text NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT uq_integration_endpoints_endpoint_code UNIQUE (endpoint_code)
);

CREATE TABLE public.idempotency_keys (
    id uuid NOT NULL CONSTRAINT pk_idempotency_keys PRIMARY KEY,
    scope varchar(50) NOT NULL,
    idempotency_key varchar(120) NOT NULL,
    request_hash char(64) NULL,
    result_entity_type varchar(50) NULL,
    result_entity_id varchar(100) NULL,
    status varchar(16) NOT NULL,
    created_at timestamptz(3) NOT NULL,
    expires_at timestamptz(3) NULL,
    CONSTRAINT uq_idempotency_keys_scope_idempotency_key UNIQUE (scope, idempotency_key)
);

CREATE TABLE public.outbox_events (
    id uuid NOT NULL CONSTRAINT pk_outbox_events PRIMARY KEY,
    aggregate_type varchar(50) NOT NULL,
    aggregate_id varchar(100) NOT NULL,
    event_type varchar(80) NOT NULL,
    payload_json text NOT NULL,
    correlation_id uuid NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    created_at timestamptz(3) NOT NULL,
    published_at timestamptz(3) NULL,
    retry_count int NOT NULL DEFAULT 0
);

CREATE TABLE public.patient_conditions (
    id uuid NOT NULL CONSTRAINT pk_patient_conditions PRIMARY KEY,
    patient_id uuid NOT NULL,
    diagnosis_code varchar(20) NULL,
    condition_name varchar(300) NOT NULL,
    clinical_status varchar(16) NOT NULL,
    onset_date date NULL,
    resolved_date date NULL,
    note varchar(1000) NULL,
    recorded_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_patient_conditions_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id)
);

CREATE TABLE public.rooms (
    id uuid NOT NULL CONSTRAINT pk_rooms PRIMARY KEY,
    department_id uuid NOT NULL,
    room_code varchar(30) NOT NULL,
    room_name varchar(200) NOT NULL,
    floor varchar(50) NULL,
    location_note varchar(300) NULL,
    room_type varchar(32) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT fk_rooms_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id),
    CONSTRAINT uq_rooms_department_id_room_code UNIQUE (department_id, room_code)
);

CREATE TABLE public.staff_department_assignments (
    id uuid NOT NULL CONSTRAINT pk_staff_department_assignments PRIMARY KEY,
    staff_id uuid NOT NULL,
    department_id uuid NOT NULL,
    is_primary boolean NOT NULL DEFAULT false,
    valid_from date NOT NULL,
    valid_to date NULL,
    CONSTRAINT fk_staff_department_assignments_staff_id FOREIGN KEY (staff_id) REFERENCES public.staff(id),
    CONSTRAINT fk_staff_department_assignments_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id)
);

CREATE TABLE public.health_examination_participants (
    id uuid NOT NULL CONSTRAINT pk_health_examination_participants PRIMARY KEY,
    organization_id uuid NOT NULL,
    patient_id uuid NULL,
    participant_code varchar(60) NOT NULL,
    identification_number varchar(20) NOT NULL,
    full_name varchar(200) NOT NULL,
    date_of_birth date NOT NULL,
    sex varchar(16) NOT NULL,
    department_name varchar(200) NULL,
    job_title varchar(200) NULL,
    occupation varchar(200) NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_health_examination_participants_organization_id FOREIGN KEY (organization_id) REFERENCES public.organizations(id),
    CONSTRAINT fk_health_examination_participants_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT uq_health_examination_participants_organization_id_par_01022f71 UNIQUE (organization_id, participant_code),
    CONSTRAINT uq_health_examination_participants_organization_id_ide_9e2a14f3 UNIQUE (organization_id, identification_number)
);

CREATE TABLE public.lab_panel_items (
    id uuid NOT NULL CONSTRAINT pk_lab_panel_items PRIMARY KEY,
    lab_panel_id uuid NOT NULL,
    analyte_id uuid NOT NULL,
    display_order int NOT NULL,
    CONSTRAINT fk_lab_panel_items_lab_panel_id FOREIGN KEY (lab_panel_id) REFERENCES public.lab_panels(id),
    CONSTRAINT fk_lab_panel_items_analyte_id FOREIGN KEY (analyte_id) REFERENCES public.analytes(id),
    CONSTRAINT uq_lab_panel_items_lab_panel_id_analyte_id UNIQUE (lab_panel_id, analyte_id)
);

CREATE TABLE public.analyte_reference_ranges (
    id uuid NOT NULL CONSTRAINT pk_analyte_reference_ranges PRIMARY KEY,
    analyte_id uuid NOT NULL,
    sex varchar(16) NULL,
    age_min_days int NULL,
    age_max_days int NULL,
    lower_bound decimal(18,6) NULL,
    upper_bound decimal(18,6) NULL,
    text_range varchar(200) NULL,
    warning_lower decimal(18,6) NULL,
    warning_upper decimal(18,6) NULL,
    effective_from timestamptz(3) NOT NULL,
    effective_to timestamptz(3) NULL,
    CONSTRAINT fk_analyte_reference_ranges_analyte_id FOREIGN KEY (analyte_id) REFERENCES public.analytes(id)
);

CREATE TABLE public.users (
    id uuid NOT NULL CONSTRAINT pk_users PRIMARY KEY,
    principal_type varchar(16) NOT NULL,
    staff_id uuid NULL,
    patient_id uuid NULL,
    auth_provider varchar(32) NOT NULL,
    auth_subject varchar(200) NOT NULL,
    status varchar(16) NOT NULL,
    last_login_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL,
    CONSTRAINT ck_users_principal_type CHECK ((principal_type = 'STAFF' AND staff_id IS NOT NULL AND patient_id IS NULL) OR (principal_type = 'PATIENT' AND patient_id IS NOT NULL AND staff_id IS NULL)),
    CONSTRAINT fk_users_staff_id FOREIGN KEY (staff_id) REFERENCES public.staff(id),
    CONSTRAINT fk_users_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT uq_users_auth_provider_auth_subject UNIQUE (auth_provider, auth_subject)
);

CREATE TABLE public.role_permissions (
    id uuid NOT NULL CONSTRAINT pk_role_permissions PRIMARY KEY,
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    CONSTRAINT fk_role_permissions_role_id FOREIGN KEY (role_id) REFERENCES public.roles(id),
    CONSTRAINT fk_role_permissions_permission_id FOREIGN KEY (permission_id) REFERENCES public.permissions(id),
    CONSTRAINT uq_role_permissions_role_id_permission_id UNIQUE (role_id, permission_id)
);

CREATE TABLE public.external_code_mappings (
    id uuid NOT NULL CONSTRAINT pk_external_code_mappings PRIMARY KEY,
    integration_endpoint_id uuid NOT NULL,
    mapping_type varchar(32) NOT NULL,
    external_code varchar(100) NOT NULL,
    internal_entity_type varchar(32) NOT NULL,
    internal_entity_id uuid NOT NULL,
    valid_from timestamptz(3) NOT NULL,
    valid_to timestamptz(3) NULL,
    CONSTRAINT fk_external_code_mappings_integration_endpoint_id FOREIGN KEY (integration_endpoint_id) REFERENCES public.integration_endpoints(id)
);

CREATE TABLE public.integration_messages (
    id uuid NOT NULL CONSTRAINT pk_integration_messages PRIMARY KEY,
    integration_endpoint_id uuid NOT NULL,
    direction varchar(8) NOT NULL,
    message_type varchar(40) NOT NULL,
    external_message_id varchar(150) NULL,
    correlation_id uuid NOT NULL,
    status varchar(16) NOT NULL,
    payload_reference varchar(500) NULL,
    payload_hash char(64) NULL,
    error_message varchar(2000) NULL,
    received_at timestamptz(3) NULL,
    processed_at timestamptz(3) NULL,
    retry_count int NOT NULL DEFAULT 0,
    CONSTRAINT fk_integration_messages_integration_endpoint_id FOREIGN KEY (integration_endpoint_id) REFERENCES public.integration_endpoints(id)
);

CREATE TABLE public.patient_allergies (
    id uuid NOT NULL CONSTRAINT pk_patient_allergies PRIMARY KEY,
    patient_id uuid NOT NULL,
    substance_code varchar(50) NULL,
    substance_name varchar(200) NOT NULL,
    reaction varchar(500) NULL,
    severity varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    verification_status varchar(16) NOT NULL DEFAULT 'CONFIRMED',
    recorded_by_user_id uuid NULL,
    recorded_at timestamptz(3) NOT NULL,
    ended_at timestamptz(3) NULL,
    CONSTRAINT fk_patient_allergies_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_patient_allergies_recorded_by_user_id FOREIGN KEY (recorded_by_user_id) REFERENCES public.users(id)
);

CREATE TABLE public.services (
    id uuid NOT NULL CONSTRAINT pk_services PRIMARY KEY,
    service_code varchar(40) NOT NULL,
    service_name varchar(300) NOT NULL,
    service_type varchar(32) NOT NULL,
    performing_department_id uuid NULL,
    default_room_id uuid NULL,
    requires_payment boolean NOT NULL DEFAULT true,
    requires_specimen boolean NOT NULL DEFAULT false,
    health_examination_eligible boolean NOT NULL DEFAULT false,
    result_type varchar(24) NOT NULL DEFAULT 'NONE',
    lab_panel_id uuid NULL,
    preparation_instructions varchar(1000) NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_services_performing_department_id FOREIGN KEY (performing_department_id) REFERENCES public.departments(id),
    CONSTRAINT fk_services_default_room_id FOREIGN KEY (default_room_id) REFERENCES public.rooms(id),
    CONSTRAINT fk_services_lab_panel_id FOREIGN KEY (lab_panel_id) REFERENCES public.lab_panels(id),
    CONSTRAINT uq_services_service_code UNIQUE (service_code)
);

CREATE TABLE public.encounters (
    id uuid NOT NULL CONSTRAINT pk_encounters PRIMARY KEY,
    encounter_code varchar(30) NOT NULL,
    patient_id uuid NOT NULL,
    encounter_type varchar(24) NOT NULL,
    reason varchar(500) NULL,
    priority varchar(16) NOT NULL DEFAULT 'ROUTINE',
    status varchar(16) NOT NULL,
    started_at timestamptz(3) NULL,
    completed_at timestamptz(3) NULL,
    canceled_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    row_version bigint NOT NULL DEFAULT 0,
    prepared_at timestamptz(3) NULL,
    checked_in_at timestamptz(3) NULL,
    checked_in_by_user_id uuid NULL,
    CONSTRAINT ck_encounters_status CHECK (status IN ('PREPARED', 'IN_PROGRESS', 'COMPLETED', 'CANCELED')),
    CONSTRAINT fk_encounters_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_encounters_checked_in_by_user_id FOREIGN KEY (checked_in_by_user_id) REFERENCES public.users(id),
    CONSTRAINT uq_encounters_encounter_code UNIQUE (encounter_code)
);

CREATE TABLE public.user_roles (
    id uuid NOT NULL CONSTRAINT pk_user_roles PRIMARY KEY,
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    department_id uuid NULL,
    room_id uuid NULL,
    valid_from timestamptz(3) NOT NULL,
    valid_to timestamptz(3) NULL,
    CONSTRAINT fk_user_roles_user_id FOREIGN KEY (user_id) REFERENCES public.users(id),
    CONSTRAINT fk_user_roles_role_id FOREIGN KEY (role_id) REFERENCES public.roles(id),
    CONSTRAINT fk_user_roles_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id),
    CONSTRAINT fk_user_roles_room_id FOREIGN KEY (room_id) REFERENCES public.rooms(id)
);

CREATE TABLE public.service_prices (
    id uuid NOT NULL CONSTRAINT pk_service_prices PRIMARY KEY,
    service_id uuid NOT NULL,
    price_type varchar(32) NOT NULL,
    payer_reference varchar(100) NULL,
    amount decimal(18,2) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'VND',
    effective_from timestamptz(3) NOT NULL,
    effective_to timestamptz(3) NULL,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT fk_service_prices_service_id FOREIGN KEY (service_id) REFERENCES public.services(id)
);

CREATE TABLE public.service_template_mappings (
    id uuid NOT NULL CONSTRAINT pk_service_template_mappings PRIMARY KEY,
    service_id uuid NOT NULL,
    document_template_id uuid NOT NULL,
    display_order int NOT NULL DEFAULT 1,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_service_template_mappings_service_id FOREIGN KEY (service_id) REFERENCES public.services(id),
    CONSTRAINT fk_service_template_mappings_document_template_id FOREIGN KEY (document_template_id) REFERENCES public.document_templates(id),
    CONSTRAINT uq_service_template_mappings_service_id_document_template_id UNIQUE (service_id, document_template_id)
);

CREATE TABLE public.appointments (
    id uuid NOT NULL CONSTRAINT pk_appointments PRIMARY KEY,
    patient_id uuid NOT NULL,
    source_encounter_id uuid NULL,
    department_id uuid NULL,
    physician_staff_id uuid NULL,
    scheduled_start timestamptz(3) NOT NULL,
    scheduled_end timestamptz(3) NULL,
    status varchar(16) NOT NULL,
    reason varchar(500) NULL,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_appointments_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_appointments_source_encounter_id FOREIGN KEY (source_encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_appointments_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id),
    CONSTRAINT fk_appointments_doctor_staff_id FOREIGN KEY (physician_staff_id) REFERENCES public.staff(id)
);

CREATE TABLE public.encounter_assignments (
    id uuid NOT NULL CONSTRAINT pk_encounter_assignments PRIMARY KEY,
    encounter_id uuid NOT NULL,
    department_id uuid NOT NULL,
    room_id uuid NULL,
    physician_staff_id uuid NULL,
    assigned_at timestamptz(3) NOT NULL,
    ended_at timestamptz(3) NULL,
    assigned_by_user_id uuid NULL,
    destination_label varchar(200) NULL,
    CONSTRAINT fk_encounter_assignments_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_encounter_assignments_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id),
    CONSTRAINT fk_encounter_assignments_room_id FOREIGN KEY (room_id) REFERENCES public.rooms(id),
    CONSTRAINT fk_encounter_assignments_doctor_staff_id FOREIGN KEY (physician_staff_id) REFERENCES public.staff(id),
    CONSTRAINT fk_encounter_assignments_assigned_by_user_id FOREIGN KEY (assigned_by_user_id) REFERENCES public.users(id)
);

CREATE TABLE public.vital_signs (
    id uuid NOT NULL CONSTRAINT pk_vital_signs PRIMARY KEY,
    encounter_id uuid NOT NULL,
    height_cm decimal(6,2) NULL,
    weight_kg decimal(6,2) NULL,
    bmi decimal(6,2) NULL,
    pulse_bpm int NULL,
    systolic_bp int NULL,
    diastolic_bp int NULL,
    physical_classification varchar(100) NULL,
    measured_at timestamptz(3) NOT NULL,
    recorded_by_staff_id uuid NULL,
    CONSTRAINT fk_vital_signs_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_vital_signs_recorded_by_staff_id FOREIGN KEY (recorded_by_staff_id) REFERENCES public.staff(id)
);

CREATE TABLE public.clinical_notes (
    id uuid NOT NULL CONSTRAINT pk_clinical_notes PRIMARY KEY,
    encounter_id uuid NOT NULL,
    note_type varchar(32) NOT NULL,
    content_json text NOT NULL,
    author_staff_id uuid NOT NULL,
    status varchar(16) NOT NULL,
    created_at timestamptz(3) NOT NULL,
    finalized_at timestamptz(3) NULL,
    CONSTRAINT fk_clinical_notes_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_clinical_notes_author_staff_id FOREIGN KEY (author_staff_id) REFERENCES public.staff(id)
);

CREATE TABLE public.encounter_diagnoses (
    id uuid NOT NULL CONSTRAINT pk_encounter_diagnoses PRIMARY KEY,
    encounter_id uuid NOT NULL,
    diagnosis_catalog_id uuid NULL,
    diagnosis_text varchar(500) NOT NULL,
    diagnosis_type varchar(24) NOT NULL,
    is_primary boolean NOT NULL DEFAULT false,
    recorded_by_staff_id uuid NOT NULL,
    recorded_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_encounter_diagnoses_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_encounter_diagnoses_diagnosis_catalog_id FOREIGN KEY (diagnosis_catalog_id) REFERENCES public.diagnosis_catalog(id),
    CONSTRAINT fk_encounter_diagnoses_recorded_by_staff_id FOREIGN KEY (recorded_by_staff_id) REFERENCES public.staff(id)
);

CREATE TABLE public.invoices (
    id uuid NOT NULL CONSTRAINT pk_invoices PRIMARY KEY,
    invoice_number varchar(40) NOT NULL,
    encounter_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    invoice_type varchar(24) NOT NULL,
    status varchar(20) NOT NULL,
    subtotal decimal(18,2) NOT NULL,
    discount_amount decimal(18,2) NOT NULL DEFAULT 0,
    total_amount decimal(18,2) NOT NULL,
    paid_amount decimal(18,2) NOT NULL DEFAULT 0,
    issued_at timestamptz(3) NOT NULL,
    created_by_user_id uuid NOT NULL,
    CONSTRAINT fk_invoices_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_invoices_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_invoices_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id),
    CONSTRAINT uq_invoices_invoice_number UNIQUE (invoice_number)
);

CREATE TABLE public.specimens (
    id uuid NOT NULL CONSTRAINT pk_specimens PRIMARY KEY,
    specimen_code varchar(50) NOT NULL,
    patient_id uuid NOT NULL,
    encounter_id uuid NULL,
    specimen_type varchar(40) NOT NULL,
    status varchar(20) NOT NULL,
    collected_at timestamptz(3) NULL,
    collected_by_staff_id uuid NULL,
    received_at timestamptz(3) NULL,
    CONSTRAINT fk_specimens_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_specimens_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_specimens_collected_by_staff_id FOREIGN KEY (collected_by_staff_id) REFERENCES public.staff(id),
    CONSTRAINT uq_specimens_specimen_code UNIQUE (specimen_code)
);

CREATE TABLE public.file_attachments (
    id uuid NOT NULL CONSTRAINT pk_file_attachments PRIMARY KEY,
    patient_id uuid NULL,
    encounter_id uuid NULL,
    entity_type varchar(40) NOT NULL,
    entity_id uuid NOT NULL,
    document_type varchar(40) NOT NULL,
    storage_provider varchar(24) NOT NULL,
    storage_key varchar(500) NOT NULL,
    file_name varchar(255) NOT NULL,
    mime_type varchar(100) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 char(64) NULL,
    created_by_user_id uuid NULL,
    created_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_file_attachments_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_file_attachments_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_file_attachments_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id)
);

CREATE TABLE public.prescriptions (
    id uuid NOT NULL CONSTRAINT pk_prescriptions PRIMARY KEY,
    prescription_number varchar(40) NOT NULL,
    encounter_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    prescriber_staff_id uuid NOT NULL,
    version_number int NOT NULL,
    status varchar(16) NOT NULL,
    supersedes_prescription_id uuid NULL,
    issued_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_prescriptions_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_prescriptions_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_prescriptions_prescriber_staff_id FOREIGN KEY (prescriber_staff_id) REFERENCES public.staff(id),
    CONSTRAINT fk_prescriptions_supersedes_prescription_id FOREIGN KEY (supersedes_prescription_id) REFERENCES public.prescriptions(id),
    CONSTRAINT uq_prescriptions_prescription_number UNIQUE (prescription_number)
);

CREATE TABLE public.document_template_versions (
    id uuid NOT NULL CONSTRAINT pk_document_template_versions PRIMARY KEY,
    document_template_id uuid NOT NULL,
    version_number int NOT NULL,
    source_file_attachment_id uuid NOT NULL,
    paper_size varchar(16) NOT NULL,
    custom_width_mm decimal(8,2) NULL,
    custom_height_mm decimal(8,2) NULL,
    orientation varchar(16) NOT NULL DEFAULT 'PORTRAIT',
    render_mode varchar(24) NOT NULL,
    renderer_type varchar(24) NOT NULL,
    schema_json text NOT NULL,
    render_template text NULL,
    effective_from timestamptz(3) NOT NULL,
    retired_at timestamptz(3) NULL,
    created_by_user_id uuid NOT NULL,
    created_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_document_template_versions_document_template_id FOREIGN KEY (document_template_id) REFERENCES public.document_templates(id),
    CONSTRAINT fk_document_template_versions_source_file_attachment_id FOREIGN KEY (source_file_attachment_id) REFERENCES public.file_attachments(id),
    CONSTRAINT fk_document_template_versions_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id),
    CONSTRAINT uq_document_template_versions_template_version UNIQUE (document_template_id, version_number)
);

CREATE TABLE public.invoice_adjustments (
    id uuid NOT NULL CONSTRAINT pk_invoice_adjustments PRIMARY KEY,
    invoice_id uuid NOT NULL,
    adjustment_type varchar(24) NOT NULL,
    amount decimal(18,2) NOT NULL,
    reason varchar(500) NOT NULL,
    created_by_user_id uuid NOT NULL,
    created_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_invoice_adjustments_invoice_id FOREIGN KEY (invoice_id) REFERENCES public.invoices(id),
    CONSTRAINT fk_invoice_adjustments_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id)
);

CREATE TABLE public.payments (
    id uuid NOT NULL CONSTRAINT pk_payments PRIMARY KEY,
    invoice_id uuid NOT NULL,
    payment_method varchar(24) NOT NULL,
    amount decimal(18,2) NOT NULL,
    status varchar(24) NOT NULL,
    gateway_transaction_id varchar(150) NULL,
    confirmed_by_user_id uuid NULL,
    confirmed_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL,
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    CONSTRAINT fk_payments_invoice_id FOREIGN KEY (invoice_id) REFERENCES public.invoices(id),
    CONSTRAINT fk_payments_confirmed_by_user_id FOREIGN KEY (confirmed_by_user_id) REFERENCES public.users(id)
);

CREATE TABLE public.prescription_items (
    id uuid NOT NULL CONSTRAINT pk_prescription_items PRIMARY KEY,
    prescription_id uuid NOT NULL,
    medication_id uuid NULL,
    medication_name_snapshot varchar(300) NOT NULL,
    strength_snapshot varchar(100) NULL,
    dose varchar(100) NOT NULL,
    route varchar(100) NULL,
    frequency varchar(100) NOT NULL,
    duration_days int NULL,
    quantity decimal(12,3) NULL,
    instructions varchar(500) NULL,
    display_order int NOT NULL,
    CONSTRAINT fk_prescription_items_prescription_id FOREIGN KEY (prescription_id) REFERENCES public.prescriptions(id),
    CONSTRAINT fk_prescription_items_medication_id FOREIGN KEY (medication_id) REFERENCES public.medications(id)
);

CREATE TABLE public.notifications (
    id uuid NOT NULL CONSTRAINT pk_notifications PRIMARY KEY,
    patient_id uuid NULL,
    appointment_id uuid NULL,
    encounter_id uuid NULL,
    notification_type varchar(40) NOT NULL,
    recipient_phone varchar(30) NOT NULL,
    sms_template_code varchar(60) NOT NULL,
    payload_json text NOT NULL,
    provider_code varchar(40) NULL,
    provider_message_id varchar(150) NULL,
    idempotency_key varchar(120) NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count int NOT NULL DEFAULT 0,
    scheduled_at timestamptz(3) NULL,
    sent_at timestamptz(3) NULL,
    delivered_at timestamptz(3) NULL,
    failed_at timestamptz(3) NULL,
    next_retry_at timestamptz(3) NULL,
    error_code varchar(100) NULL,
    error_message varchar(1000) NULL,
    created_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_notifications_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_notifications_appointment_id FOREIGN KEY (appointment_id) REFERENCES public.appointments(id),
    CONSTRAINT fk_notifications_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT uq_notifications_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE public.document_template_fields (
    id uuid NOT NULL CONSTRAINT pk_document_template_fields PRIMARY KEY,
    document_template_version_id uuid NOT NULL,
    field_key varchar(80) NOT NULL,
    display_label varchar(300) NULL,
    item_type varchar(24) NOT NULL,
    selection_mode varchar(24) NULL,
    eligibility_rule_json text NULL,
    display_order int NOT NULL,
    is_required boolean NOT NULL DEFAULT false,
    CONSTRAINT fk_document_template_fields_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES public.document_template_versions(id),
    CONSTRAINT uq_document_template_fields_version_field UNIQUE (document_template_version_id, field_key)
);

CREATE TABLE public.generated_documents (
    id uuid NOT NULL CONSTRAINT pk_generated_documents PRIMARY KEY,
    encounter_id uuid NOT NULL,
    document_template_version_id uuid NOT NULL,
    document_kind varchar(40) NOT NULL,
    print_sequence int NOT NULL DEFAULT 1,
    paper_size_snapshot varchar(16) NOT NULL,
    orientation_snapshot varchar(16) NOT NULL,
    version_number int NOT NULL DEFAULT 1,
    status varchar(24) NOT NULL,
    render_payload_hash char(64) NULL,
    file_attachment_id uuid NULL,
    generated_by_user_id uuid NULL,
    generated_at timestamptz(3) NOT NULL,
    invalidated_at timestamptz(3) NULL,
    CONSTRAINT fk_generated_documents_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_generated_documents_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES public.document_template_versions(id),
    CONSTRAINT fk_generated_documents_file_attachment_id FOREIGN KEY (file_attachment_id) REFERENCES public.file_attachments(id),
    CONSTRAINT fk_generated_documents_generated_by_user_id FOREIGN KEY (generated_by_user_id) REFERENCES public.users(id)
);

CREATE TABLE public.health_examination_batches (
    id uuid NOT NULL CONSTRAINT pk_health_examination_batches PRIMARY KEY,
    organization_id uuid NOT NULL,
    batch_code varchar(40) NOT NULL,
    batch_name varchar(250) NOT NULL,
    start_date date NULL,
    end_date date NULL,
    reason varchar(300) NULL,
    payer_type varchar(24) NULL,
    examination_site_type varchar(16) NOT NULL,
    examination_site_name varchar(250) NOT NULL,
    examination_site_address varchar(500) NULL,
    master_template_version_id uuid NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    finalized_at timestamptz(3) NULL,
    closed_at timestamptz(3) NULL,
    created_by_user_id uuid NOT NULL,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT ck_health_examination_batches_status CHECK (status IN ('DRAFT', 'READY', 'IN_PROGRESS', 'RESULT_PROCESSING', 'FINALIZED', 'CLOSED', 'CANCELED')),
    CONSTRAINT fk_health_examination_batches_organization_id FOREIGN KEY (organization_id) REFERENCES public.organizations(id),
    CONSTRAINT fk_health_examination_batches_master_template_version_id FOREIGN KEY (master_template_version_id) REFERENCES public.document_template_versions(id),
    CONSTRAINT fk_health_examination_batches_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id),
    CONSTRAINT uq_health_examination_batches_organization_id_batch_code UNIQUE (organization_id, batch_code)
);

CREATE TABLE public.notification_attempts (
    id uuid NOT NULL CONSTRAINT pk_notification_attempts PRIMARY KEY,
    notification_id uuid NOT NULL,
    attempt_number int NOT NULL,
    provider_code varchar(40) NOT NULL,
    provider_message_id varchar(150) NULL,
    status varchar(20) NOT NULL,
    request_reference varchar(500) NULL,
    response_reference varchar(500) NULL,
    error_code varchar(100) NULL,
    error_message varchar(1000) NULL,
    attempted_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_notification_attempts_notification_id FOREIGN KEY (notification_id) REFERENCES public.notifications(id),
    CONSTRAINT uq_notification_attempts_notification_id_attempt_number UNIQUE (notification_id, attempt_number)
);

CREATE TABLE public.health_examination_batch_services (
    id uuid NOT NULL CONSTRAINT pk_health_examination_batch_services PRIMARY KEY,
    health_examination_batch_id uuid NOT NULL,
    service_id uuid NOT NULL,
    document_template_version_id uuid NULL,
    service_code_snapshot varchar(40) NOT NULL,
    service_name_snapshot varchar(300) NOT NULL,
    base_price_snapshot decimal(18,2) NOT NULL,
    negotiated_unit_price decimal(18,2) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'VND',
    display_order int NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_health_examination_batch_services_health_examinatio_0c047c02 FOREIGN KEY (health_examination_batch_id) REFERENCES public.health_examination_batches(id),
    CONSTRAINT fk_health_examination_batch_services_service_id FOREIGN KEY (service_id) REFERENCES public.services(id),
    CONSTRAINT fk_health_examination_batch_services_document_template_cc6a7644 FOREIGN KEY (document_template_version_id) REFERENCES public.document_template_versions(id),
    CONSTRAINT uq_health_examination_batch_services_health_examinatio_7244f2fd UNIQUE (health_examination_batch_id, service_id)
);

CREATE TABLE public.health_examination_batch_participants (
    id uuid NOT NULL CONSTRAINT pk_health_examination_batch_participants PRIMARY KEY,
    health_examination_batch_id uuid NOT NULL,
    health_examination_participant_id uuid NOT NULL,
    participant_code_snapshot varchar(60) NOT NULL,
    department_snapshot varchar(200) NULL,
    job_title_snapshot varchar(200) NULL,
    occupation_snapshot varchar(200) NULL,
    administrative_snapshot_json text NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'REGISTERED',
    created_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_health_examination_batch_participants_health_examin_508052ab FOREIGN KEY (health_examination_batch_id) REFERENCES public.health_examination_batches(id),
    CONSTRAINT fk_health_examination_batch_participants_health_examin_0f29bbce FOREIGN KEY (health_examination_participant_id) REFERENCES public.health_examination_participants(id),
    CONSTRAINT uq_health_examination_batch_participants_batch_participant UNIQUE (health_examination_batch_id, health_examination_participant_id)
);

CREATE TABLE public.health_examination_import_jobs (
    id uuid NOT NULL CONSTRAINT pk_health_examination_import_jobs PRIMARY KEY,
    health_examination_batch_id uuid NOT NULL,
    import_type varchar(20) NOT NULL,
    source_file_attachment_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    column_mapping_json text NULL,
    total_rows int NOT NULL DEFAULT 0,
    valid_rows int NOT NULL DEFAULT 0,
    warning_rows int NOT NULL DEFAULT 0,
    error_rows int NOT NULL DEFAULT 0,
    created_by_user_id uuid NOT NULL,
    confirmed_by_user_id uuid NULL,
    created_at timestamptz(3) NOT NULL,
    confirmed_at timestamptz(3) NULL,
    CONSTRAINT fk_health_examination_import_jobs_health_examination_batch_id FOREIGN KEY (health_examination_batch_id) REFERENCES public.health_examination_batches(id),
    CONSTRAINT fk_health_examination_import_jobs_source_file_attachment_id FOREIGN KEY (source_file_attachment_id) REFERENCES public.file_attachments(id),
    CONSTRAINT fk_health_examination_import_jobs_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id),
    CONSTRAINT fk_health_examination_import_jobs_confirmed_by_user_id FOREIGN KEY (confirmed_by_user_id) REFERENCES public.users(id)
);

CREATE TABLE public.health_examination_records (
    id uuid NOT NULL CONSTRAINT pk_health_examination_records PRIMARY KEY,
    shs_code varchar(40) NOT NULL,
    source_type varchar(16) NOT NULL,
    patient_id uuid NOT NULL,
    health_examination_batch_participant_id uuid NULL,
    encounter_id uuid NOT NULL,
    master_template_version_id uuid NOT NULL,
    full_name_snapshot varchar(200) NOT NULL,
    date_of_birth_snapshot date NOT NULL,
    sex_snapshot varchar(16) NOT NULL,
    identification_number_snapshot varchar(20) NOT NULL,
    identification_number_issue_date_snapshot date NULL,
    identification_number_issue_place_snapshot varchar(200) NULL,
    ethnicity_snapshot varchar(100) NULL,
    subject_type_snapshot varchar(100) NULL,
    payer_source_snapshot varchar(150) NULL,
    blood_group_snapshot varchar(16) NULL,
    phone_snapshot varchar(30) NULL,
    province_snapshot varchar(150) NULL,
    ward_snapshot varchar(150) NULL,
    address_detail_snapshot varchar(500) NULL,
    occupation_snapshot varchar(200) NULL,
    workplace_or_school_snapshot varchar(300) NULL,
    health_examination_reason_snapshot varchar(500) NULL,
    planned_examination_date date NOT NULL,
    actual_examination_date date NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    replaces_health_examination_record_id uuid NULL,
    created_at timestamptz(3) NOT NULL,
    completed_at timestamptz(3) NULL,
    canceled_at timestamptz(3) NULL,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_health_examination_records_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELED', 'REPLACED')),
    CONSTRAINT fk_health_examination_records_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_health_examination_records_health_examination_batch_994ddf55 FOREIGN KEY (health_examination_batch_participant_id) REFERENCES public.health_examination_batch_participants(id),
    CONSTRAINT fk_health_examination_records_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_health_examination_records_master_template_version_id FOREIGN KEY (master_template_version_id) REFERENCES public.document_template_versions(id),
    CONSTRAINT fk_health_examination_records_replaces_health_examinat_e86b3a07 FOREIGN KEY (replaces_health_examination_record_id) REFERENCES public.health_examination_records(id)
);

CREATE TABLE public.order_rounds (
    id uuid NOT NULL CONSTRAINT pk_order_rounds PRIMARY KEY,
    encounter_id uuid NOT NULL,
    health_examination_record_id uuid NULL,
    round_number int NOT NULL,
    source_type varchar(24) NOT NULL,
    status varchar(16) NOT NULL,
    ordered_by_staff_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    ordered_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_order_rounds_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_order_rounds_health_examination_record_id FOREIGN KEY (health_examination_record_id) REFERENCES public.health_examination_records(id),
    CONSTRAINT fk_order_rounds_ordered_by_staff_id FOREIGN KEY (ordered_by_staff_id) REFERENCES public.staff(id),
    CONSTRAINT fk_order_rounds_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id),
    CONSTRAINT uq_order_rounds_encounter_id_round_number UNIQUE (encounter_id, round_number)
);

CREATE TABLE public.audit_logs (
    id uuid NOT NULL CONSTRAINT pk_audit_logs PRIMARY KEY,
    occurred_at timestamptz(3) NOT NULL,
    actor_user_id uuid NULL,
    action varchar(80) NOT NULL,
    entity_type varchar(50) NOT NULL,
    entity_id varchar(100) NOT NULL,
    patient_id uuid NULL,
    encounter_id uuid NULL,
    health_examination_record_id uuid NULL,
    correlation_id uuid NULL,
    reason varchar(500) NULL,
    before_json text NULL,
    after_json text NULL,
    ip_address varchar(64) NULL,
    user_agent varchar(500) NULL,
    CONSTRAINT fk_audit_logs_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES public.users(id),
    CONSTRAINT fk_audit_logs_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_audit_logs_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id),
    CONSTRAINT fk_audit_logs_health_examination_record_id FOREIGN KEY (health_examination_record_id) REFERENCES public.health_examination_records(id)
);

CREATE TABLE public.service_requests (
    id uuid NOT NULL CONSTRAINT pk_service_requests PRIMARY KEY,
    order_round_id uuid NOT NULL,
    service_id uuid NOT NULL,
    status varchar(16) NOT NULL,
    priority varchar(16) NOT NULL DEFAULT 'ROUTINE',
    performing_department_id uuid NULL,
    performing_room_id uuid NULL,
    performing_location_label varchar(200) NULL,
    service_name_snapshot varchar(300) NOT NULL,
    unit_price_snapshot decimal(18,2) NULL,
    preparation_instructions_snapshot varchar(1000) NULL,
    ordered_at timestamptz(3) NOT NULL,
    started_at timestamptz(3) NULL,
    completed_at timestamptz(3) NULL,
    CONSTRAINT ck_service_requests_status CHECK (status IN ('ORDERED', 'IN_PROGRESS', 'COMPLETED', 'CANCELED')),
    CONSTRAINT fk_service_requests_order_round_id FOREIGN KEY (order_round_id) REFERENCES public.order_rounds(id),
    CONSTRAINT fk_service_requests_service_id FOREIGN KEY (service_id) REFERENCES public.services(id),
    CONSTRAINT fk_service_requests_performing_department_id FOREIGN KEY (performing_department_id) REFERENCES public.departments(id),
    CONSTRAINT fk_service_requests_performing_room_id FOREIGN KEY (performing_room_id) REFERENCES public.rooms(id)
);

CREATE TABLE public.generated_document_service_requests (
    id uuid NOT NULL CONSTRAINT pk_generated_document_service_requests PRIMARY KEY,
    generated_document_id uuid NOT NULL,
    service_request_id uuid NOT NULL,
    service_code_snapshot varchar(40) NOT NULL,
    service_name_snapshot varchar(300) NOT NULL,
    display_order int NOT NULL DEFAULT 1,
    created_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_generated_document_service_requests_generated_document_id FOREIGN KEY (generated_document_id) REFERENCES public.generated_documents(id),
    CONSTRAINT fk_generated_document_service_requests_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id),
    CONSTRAINT uq_generated_doc_service_requests_doc_request UNIQUE (generated_document_id, service_request_id)
);

CREATE TABLE public.invoice_items (
    id uuid NOT NULL CONSTRAINT pk_invoice_items PRIMARY KEY,
    invoice_id uuid NOT NULL,
    service_request_id uuid NULL,
    service_id uuid NULL,
    description_snapshot varchar(300) NOT NULL,
    quantity decimal(12,3) NOT NULL DEFAULT 1,
    unit_price decimal(18,2) NOT NULL,
    discount_amount decimal(18,2) NOT NULL DEFAULT 0,
    line_total decimal(18,2) NOT NULL,
    CONSTRAINT fk_invoice_items_invoice_id FOREIGN KEY (invoice_id) REFERENCES public.invoices(id),
    CONSTRAINT fk_invoice_items_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id),
    CONSTRAINT fk_invoice_items_service_id FOREIGN KEY (service_id) REFERENCES public.services(id)
);

CREATE TABLE public.health_examination_batch_participant_services (
    id uuid NOT NULL CONSTRAINT pk_health_examination_batch_participant_services PRIMARY KEY,
    health_examination_batch_participant_id uuid NOT NULL,
    health_examination_batch_service_id uuid NOT NULL,
    service_request_id uuid NOT NULL,
    billable boolean NOT NULL DEFAULT false,
    unit_price_snapshot decimal(18,2) NOT NULL,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_health_examination_batch_participant_services_participant FOREIGN KEY (health_examination_batch_participant_id) REFERENCES public.health_examination_batch_participants(id),
    CONSTRAINT fk_health_examination_batch_participant_services_service FOREIGN KEY (health_examination_batch_service_id) REFERENCES public.health_examination_batch_services(id),
    CONSTRAINT fk_health_examination_batch_participant_services_servi_f13cfd69 FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id),
    CONSTRAINT uq_health_examination_batch_participant_services_parti_a7c3941d UNIQUE (health_examination_batch_participant_id, health_examination_batch_service_id)
);

CREATE TABLE public.health_examination_import_rows (
    id uuid NOT NULL CONSTRAINT pk_health_examination_import_rows PRIMARY KEY,
    health_examination_import_job_id uuid NOT NULL,
    row_number int NOT NULL,
    participant_code_snapshot varchar(60) NULL,
    identification_number_snapshot varchar(20) NULL,
    service_code_snapshot varchar(40) NULL,
    validation_status varchar(16) NOT NULL,
    error_codes_json text NULL,
    normalized_payload_json text NOT NULL,
    resolved_patient_id uuid NULL,
    resolved_health_examination_participant_id uuid NULL,
    resolved_batch_participant_id uuid NULL,
    resolved_batch_service_id uuid NULL,
    resolved_service_request_id uuid NULL,
    CONSTRAINT fk_health_examination_import_rows_health_examination_i_d82a1d21 FOREIGN KEY (health_examination_import_job_id) REFERENCES public.health_examination_import_jobs(id),
    CONSTRAINT fk_health_examination_import_rows_resolved_patient_id FOREIGN KEY (resolved_patient_id) REFERENCES public.patients(id),
    CONSTRAINT fk_health_examination_import_rows_resolved_health_exam_39da0b81 FOREIGN KEY (resolved_health_examination_participant_id) REFERENCES public.health_examination_participants(id),
    CONSTRAINT fk_health_examination_import_rows_resolved_batch_participant_id FOREIGN KEY (resolved_batch_participant_id) REFERENCES public.health_examination_batch_participants(id),
    CONSTRAINT fk_health_examination_import_rows_resolved_batch_service_id FOREIGN KEY (resolved_batch_service_id) REFERENCES public.health_examination_batch_services(id),
    CONSTRAINT fk_health_examination_import_rows_resolved_service_request_id FOREIGN KEY (resolved_service_request_id) REFERENCES public.service_requests(id),
    CONSTRAINT uq_health_examination_import_rows_job_row UNIQUE (health_examination_import_job_id, row_number)
);

CREATE TABLE public.specimen_service_requests (
    id uuid NOT NULL CONSTRAINT pk_specimen_service_requests PRIMARY KEY,
    specimen_id uuid NOT NULL,
    service_request_id uuid NOT NULL,
    CONSTRAINT fk_specimen_service_requests_specimen_id FOREIGN KEY (specimen_id) REFERENCES public.specimens(id),
    CONSTRAINT fk_specimen_service_requests_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id),
    CONSTRAINT uq_specimen_service_requests_specimen_id_service_request_id UNIQUE (specimen_id, service_request_id)
);

CREATE TABLE public.lab_results (
    id uuid NOT NULL CONSTRAINT pk_lab_results PRIMARY KEY,
    service_request_id uuid NOT NULL,
    specimen_id uuid NULL,
    version_number int NOT NULL,
    status varchar(16) NOT NULL,
    supersedes_lab_result_id uuid NULL,
    result_source varchar(24) NOT NULL,
    verified_by_staff_id uuid NULL,
    verified_at timestamptz(3) NULL,
    finalized_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL,
    raw_message_reference varchar(200) NULL,
    released_to_patient_at timestamptz(3) NULL,
    released_to_patient_by_user_id uuid NULL,
    CONSTRAINT fk_lab_results_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id),
    CONSTRAINT fk_lab_results_specimen_id FOREIGN KEY (specimen_id) REFERENCES public.specimens(id),
    CONSTRAINT fk_lab_results_supersedes_lab_result_id FOREIGN KEY (supersedes_lab_result_id) REFERENCES public.lab_results(id),
    CONSTRAINT fk_lab_results_verified_by_staff_id FOREIGN KEY (verified_by_staff_id) REFERENCES public.staff(id),
    CONSTRAINT uq_lab_results_service_request_id_version_number UNIQUE (service_request_id, version_number),
    CONSTRAINT fk_lab_results_released_to_patient_by_user_id FOREIGN KEY (released_to_patient_by_user_id) REFERENCES public.users(id)
);

CREATE TABLE public.imaging_studies (
    id uuid NOT NULL CONSTRAINT pk_imaging_studies PRIMARY KEY,
    service_request_id uuid NOT NULL,
    modality varchar(16) NOT NULL,
    external_study_uid varchar(200) NULL,
    device_identifier varchar(100) NULL,
    study_at timestamptz(3) NULL,
    metadata_json text NULL,
    created_at timestamptz(3) NOT NULL,
    CONSTRAINT fk_imaging_studies_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id)
);

CREATE TABLE public.payment_authorizations (
    id uuid NOT NULL CONSTRAINT pk_payment_authorizations PRIMARY KEY,
    service_request_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    invoice_item_id uuid NULL,
    authorized_at timestamptz(3) NULL,
    authorized_by_user_id uuid NULL,
    reason varchar(500) NULL,
    updated_at timestamptz(3) NOT NULL,
    CONSTRAINT ck_payment_authorizations_status CHECK (status IN ('NOT_REQUIRED', 'PENDING', 'AUTHORIZED', 'WAIVED', 'REVOKED')),
    CONSTRAINT fk_payment_authorizations_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id),
    CONSTRAINT fk_payment_authorizations_invoice_item_id FOREIGN KEY (invoice_item_id) REFERENCES public.invoice_items(id),
    CONSTRAINT fk_payment_authorizations_authorized_by_user_id FOREIGN KEY (authorized_by_user_id) REFERENCES public.users(id),
    CONSTRAINT uq_payment_authorizations_service_request_id UNIQUE (service_request_id)
);

CREATE TABLE public.lab_result_values (
    id uuid NOT NULL CONSTRAINT pk_lab_result_values PRIMARY KEY,
    lab_result_id uuid NOT NULL,
    analyte_id uuid NOT NULL,
    numeric_value decimal(18,6) NULL,
    text_value varchar(500) NULL,
    unit_snapshot varchar(40) NULL,
    reference_range_snapshot varchar(200) NULL,
    abnormal_flag varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    instrument_code varchar(50) NULL,
    measured_at timestamptz(3) NULL,
    CONSTRAINT fk_lab_result_values_lab_result_id FOREIGN KEY (lab_result_id) REFERENCES public.lab_results(id),
    CONSTRAINT fk_lab_result_values_analyte_id FOREIGN KEY (analyte_id) REFERENCES public.analytes(id),
    CONSTRAINT uq_lab_result_values_lab_result_id_analyte_id UNIQUE (lab_result_id, analyte_id)
);

CREATE TABLE public.diagnostic_reports (
    id uuid NOT NULL CONSTRAINT pk_diagnostic_reports PRIMARY KEY,
    service_request_id uuid NOT NULL,
    imaging_study_id uuid NULL,
    document_template_version_id uuid NULL,
    version_number int NOT NULL,
    status varchar(16) NOT NULL,
    findings text NULL,
    conclusion text NULL,
    structured_data_json text NULL,
    supersedes_report_id uuid NULL,
    author_staff_id uuid NOT NULL,
    verified_by_staff_id uuid NULL,
    finalized_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL,
    released_to_patient_at timestamptz(3) NULL,
    released_to_patient_by_user_id uuid NULL,
    CONSTRAINT fk_diagnostic_reports_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id),
    CONSTRAINT fk_diagnostic_reports_imaging_study_id FOREIGN KEY (imaging_study_id) REFERENCES public.imaging_studies(id),
    CONSTRAINT fk_diagnostic_reports_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES public.document_template_versions(id),
    CONSTRAINT fk_diagnostic_reports_supersedes_report_id FOREIGN KEY (supersedes_report_id) REFERENCES public.diagnostic_reports(id),
    CONSTRAINT fk_diagnostic_reports_author_staff_id FOREIGN KEY (author_staff_id) REFERENCES public.staff(id),
    CONSTRAINT fk_diagnostic_reports_verified_by_staff_id FOREIGN KEY (verified_by_staff_id) REFERENCES public.staff(id),
    CONSTRAINT uq_diagnostic_reports_service_request_id_version_number UNIQUE (service_request_id, version_number),
    CONSTRAINT fk_diagnostic_reports_released_to_patient_by_user_id FOREIGN KEY (released_to_patient_by_user_id) REFERENCES public.users(id)
);

-- Foreign keys are added after all tables so forward/self references are valid.

CREATE UNIQUE INDEX ux_patients_identification_number ON public.patients (identification_number);
CREATE UNIQUE INDEX ux_patients_patient_code ON public.patients (patient_code);
CREATE INDEX ix_patients_name_dob ON public.patients (full_name_normalized, date_of_birth);
CREATE INDEX ix_patients_phone ON public.patients (phone) WHERE phone IS NOT NULL;
CREATE INDEX ix_patient_allergies_patient ON public.patient_allergies (patient_id, ended_at);
CREATE INDEX ix_patient_conditions_patient ON public.patient_conditions (patient_id, clinical_status);
CREATE UNIQUE INDEX ux_staff_department_assignments_active_primary ON public.staff_department_assignments (staff_id) WHERE is_primary = true AND valid_to IS NULL;
CREATE INDEX ix_staff_department_assignments_staff ON public.staff_department_assignments (staff_id, valid_to);
CREATE INDEX ix_staff_department_assignments_department ON public.staff_department_assignments (department_id, valid_to);
CREATE INDEX ix_service_prices_lookup ON public.service_prices (service_id, price_type, effective_from, effective_to);
CREATE INDEX ix_document_template_fields_version ON public.document_template_fields (document_template_version_id, display_order);
CREATE UNIQUE INDEX ux_service_template_mappings_active_service ON public.service_template_mappings (service_id) WHERE is_active = true;
CREATE INDEX ix_service_template_mappings_template ON public.service_template_mappings (document_template_id, is_active);
CREATE INDEX ix_generated_documents_encounter ON public.generated_documents (encounter_id, print_sequence, status);
CREATE INDEX ix_generated_document_service_requests_document ON public.generated_document_service_requests (generated_document_id);
CREATE INDEX ix_generated_document_service_requests_request ON public.generated_document_service_requests (service_request_id);
CREATE INDEX ix_appointments_patient ON public.appointments (patient_id, scheduled_start);
CREATE INDEX ix_appointments_source_encounter ON public.appointments (source_encounter_id) WHERE source_encounter_id IS NOT NULL;
CREATE INDEX ix_appointments_schedule ON public.appointments (scheduled_start, status);
CREATE INDEX ix_encounters_patient ON public.encounters (patient_id, started_at desc);
CREATE INDEX ix_encounters_status ON public.encounters (status, started_at);
CREATE UNIQUE INDEX ux_encounter_assignments_active ON public.encounter_assignments (encounter_id) WHERE ended_at IS NULL;
CREATE INDEX ix_encounter_assignments_doctor ON public.encounter_assignments (physician_staff_id, ended_at);
CREATE INDEX ix_encounter_assignments_room ON public.encounter_assignments (room_id, ended_at, assigned_at);
CREATE INDEX ix_clinical_notes_encounter ON public.clinical_notes (encounter_id, created_at desc);
CREATE INDEX ix_service_requests_worklist ON public.service_requests (performing_department_id, performing_room_id, status, priority, ordered_at);
CREATE INDEX ix_payment_authorizations_status ON public.payment_authorizations (status, service_request_id);
CREATE INDEX ix_invoices_encounter ON public.invoices (encounter_id, status, issued_at);
CREATE INDEX ix_invoice_items_invoice ON public.invoice_items (invoice_id);
CREATE INDEX ix_payments_invoice ON public.payments (invoice_id, status);
CREATE UNIQUE INDEX ux_payments_gateway_transaction ON public.payments (gateway_transaction_id) WHERE gateway_transaction_id IS NOT NULL;
CREATE UNIQUE INDEX ux_organizations_tax_code ON public.organizations (tax_code) WHERE tax_code IS NOT NULL;
CREATE INDEX ix_organizations_name ON public.organizations (organization_name);
CREATE INDEX ix_health_examination_participants_organization ON public.health_examination_participants (organization_id, status);
CREATE INDEX ix_health_examination_participants_patient ON public.health_examination_participants (patient_id);
CREATE UNIQUE INDEX ux_health_examination_participants_organization_patient ON public.health_examination_participants (organization_id, patient_id) WHERE patient_id IS NOT NULL;
CREATE INDEX ix_health_examination_batches_organization ON public.health_examination_batches (organization_id, status, start_date);
CREATE INDEX ix_health_examination_batch_services_batch ON public.health_examination_batch_services (health_examination_batch_id, status);
CREATE INDEX ix_health_examination_batch_participants_batch ON public.health_examination_batch_participants (health_examination_batch_id, status);
CREATE UNIQUE INDEX ux_health_examination_records_shs_code ON public.health_examination_records (shs_code);
CREATE UNIQUE INDEX ux_health_examination_records_encounter ON public.health_examination_records (encounter_id);
CREATE UNIQUE INDEX ux_health_examination_records_batch_participant ON public.health_examination_records (health_examination_batch_participant_id) WHERE health_examination_batch_participant_id IS NOT NULL AND status = 'ACTIVE';
CREATE INDEX ix_health_examination_records_patient ON public.health_examination_records (patient_id, created_at desc);
CREATE INDEX ix_health_examination_batch_participant_services_participant ON public.health_examination_batch_participant_services (health_examination_batch_participant_id, billable);
CREATE INDEX ix_health_examination_batch_participant_services_service ON public.health_examination_batch_participant_services (health_examination_batch_service_id, billable);
CREATE UNIQUE INDEX ux_health_examination_batch_participant_services_request ON public.health_examination_batch_participant_services (service_request_id);
CREATE INDEX ix_health_examination_import_jobs_batch ON public.health_examination_import_jobs (health_examination_batch_id, import_type, created_at desc);
CREATE INDEX ix_health_examination_import_rows_job ON public.health_examination_import_rows (health_examination_import_job_id, validation_status);
CREATE INDEX ix_health_examination_import_rows_request ON public.health_examination_import_rows (resolved_service_request_id);
CREATE INDEX ix_notifications_status ON public.notifications (status, next_retry_at);
CREATE INDEX ix_notifications_patient ON public.notifications (patient_id, created_at desc);
CREATE INDEX ix_external_code_mappings_lookup ON public.external_code_mappings (integration_endpoint_id, mapping_type, external_code, valid_to);
CREATE INDEX ix_outbox_events_pending ON public.outbox_events (status, created_at);

-- Preserve SQL Server row version's automatic version-token behavior.
CREATE FUNCTION public.bump_row_version() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.row_version := OLD.row_version + 1;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_patients_row_version
    BEFORE UPDATE ON public.patients
    FOR EACH ROW EXECUTE FUNCTION public.bump_row_version();
CREATE TRIGGER trg_encounters_row_version
    BEFORE UPDATE ON public.encounters
    FOR EACH ROW EXECUTE FUNCTION public.bump_row_version();
CREATE TRIGGER trg_organizations_row_version
    BEFORE UPDATE ON public.organizations
    FOR EACH ROW EXECUTE FUNCTION public.bump_row_version();
CREATE TRIGGER trg_health_examination_records_row_version
    BEFORE UPDATE ON public.health_examination_records
    FOR EACH ROW EXECUTE FUNCTION public.bump_row_version();

