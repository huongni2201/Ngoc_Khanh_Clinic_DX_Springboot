-- PostgreSQL 17 baseline translated from table design v2.10
-- Source: docs/baseline/table-design-v2.10.md (baseline 2026-09-22)
-- Generated as a clean target schema; legacy MVP identifiers are intentionally absent.

CREATE TABLE public.patients (
    id uuid NOT NULL CONSTRAINT PK_patients PRIMARY KEY,
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

CREATE TABLE public.patient_allergies (
    id uuid NOT NULL CONSTRAINT PK_patient_allergies PRIMARY KEY,
    patient_id uuid NOT NULL,
    substance_code varchar(50) NULL,
    substance_name varchar(200) NOT NULL,
    reaction varchar(500) NULL,
    severity varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    verification_status varchar(16) NOT NULL DEFAULT 'CONFIRMED',
    recorded_by_user_id uuid NULL,
    recorded_at timestamptz(3) NOT NULL,
    ended_at timestamptz(3) NULL
);

CREATE TABLE public.patient_conditions (
    id uuid NOT NULL CONSTRAINT PK_patient_conditions PRIMARY KEY,
    patient_id uuid NOT NULL,
    diagnosis_code varchar(20) NULL,
    condition_name varchar(300) NOT NULL,
    clinical_status varchar(16) NOT NULL,
    onset_date date NULL,
    resolved_date date NULL,
    note varchar(1000) NULL,
    recorded_at timestamptz(3) NOT NULL
);

CREATE TABLE public.departments (
    id uuid NOT NULL CONSTRAINT PK_departments PRIMARY KEY,
    department_code varchar(30) NOT NULL,
    department_name varchar(200) NOT NULL,
    department_type varchar(32) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.rooms (
    id uuid NOT NULL CONSTRAINT PK_rooms PRIMARY KEY,
    department_id uuid NOT NULL,
    room_code varchar(30) NOT NULL,
    room_name varchar(200) NOT NULL,
    floor varchar(50) NULL,
    location_note varchar(300) NULL,
    room_type varchar(32) NOT NULL,
    is_active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.staff (
    id uuid NOT NULL CONSTRAINT PK_staff PRIMARY KEY,
    staff_code varchar(30) NOT NULL,
    full_name varchar(200) NOT NULL,
    staff_type varchar(32) NOT NULL,
    license_number varchar(100) NULL,
    phone varchar(30) NULL,
    is_active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.staff_department_assignments (
    id uuid NOT NULL CONSTRAINT PK_staff_department_assignments PRIMARY KEY,
    staff_id uuid NOT NULL,
    department_id uuid NOT NULL,
    is_primary boolean NOT NULL DEFAULT false,
    valid_from date NOT NULL,
    valid_to date NULL
);

CREATE TABLE public.diagnosis_catalog (
    id uuid NOT NULL CONSTRAINT PK_diagnosis_catalog PRIMARY KEY,
    diagnosis_code varchar(20) NOT NULL,
    name_vi varchar(500) NOT NULL,
    name_en varchar(500) NULL,
    parent_code varchar(20) NULL,
    is_active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.services (
    id uuid NOT NULL CONSTRAINT PK_services PRIMARY KEY,
    service_code varchar(40) NOT NULL,
    service_name varchar(300) NOT NULL,
    service_type varchar(32) NOT NULL,
    performing_department_id uuid NULL,
    default_room_id uuid NULL,
    requires_payment boolean NOT NULL DEFAULT true,
    requires_specimen boolean NOT NULL DEFAULT false,
    health_check_eligible boolean NOT NULL DEFAULT false,
    result_type varchar(24) NOT NULL DEFAULT 'NONE',
    lab_panel_id uuid NULL,
    preparation_instructions varchar(1000) NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.service_prices (
    id uuid NOT NULL CONSTRAINT PK_service_prices PRIMARY KEY,
    service_id uuid NOT NULL,
    price_type varchar(32) NOT NULL,
    payer_reference varchar(100) NULL,
    amount decimal(18,2) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'VND',
    effective_from timestamptz(3) NOT NULL,
    effective_to timestamptz(3) NULL,
    is_active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.medications (
    id uuid NOT NULL CONSTRAINT PK_medications PRIMARY KEY,
    medication_code varchar(40) NOT NULL,
    medication_name varchar(300) NOT NULL,
    generic_name varchar(300) NULL,
    strength varchar(100) NULL,
    dosage_form varchar(100) NULL,
    default_route varchar(100) NULL,
    unit varchar(50) NULL,
    is_active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.document_templates (
    id uuid NOT NULL CONSTRAINT PK_document_templates PRIMARY KEY,
    template_code varchar(50) NOT NULL,
    template_name varchar(250) NOT NULL,
    template_type varchar(40) NOT NULL,
    barcode_policy varchar(24) NOT NULL DEFAULT 'NONE',
    is_master_health_check_form boolean NOT NULL DEFAULT false,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.document_template_versions (
    id uuid NOT NULL CONSTRAINT PK_document_template_versions PRIMARY KEY,
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
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.document_template_fields (
    id uuid NOT NULL CONSTRAINT PK_document_template_fields PRIMARY KEY,
    document_template_version_id uuid NOT NULL,
    field_key varchar(80) NOT NULL,
    display_label varchar(300) NULL,
    item_type varchar(24) NOT NULL,
    selection_mode varchar(24) NULL,
    eligibility_rule_json text NULL,
    display_order int NOT NULL,
    is_required boolean NOT NULL DEFAULT false
);

CREATE TABLE public.service_template_mappings (
    id uuid NOT NULL CONSTRAINT PK_service_template_mappings PRIMARY KEY,
    service_id uuid NOT NULL,
    document_template_id uuid NOT NULL,
    display_order int NOT NULL DEFAULT 1,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.generated_documents (
    id uuid NOT NULL CONSTRAINT PK_generated_documents PRIMARY KEY,
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
    invalidated_at timestamptz(3) NULL
);

CREATE TABLE public.generated_document_service_requests (
    id uuid NOT NULL CONSTRAINT PK_generated_document_service_requests PRIMARY KEY,
    generated_document_id uuid NOT NULL,
    service_request_id uuid NOT NULL,
    service_code_snapshot varchar(40) NOT NULL,
    service_name_snapshot varchar(300) NOT NULL,
    display_order int NOT NULL DEFAULT 1,
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.appointments (
    id uuid NOT NULL CONSTRAINT PK_appointments PRIMARY KEY,
    patient_id uuid NOT NULL,
    source_encounter_id uuid NULL,
    department_id uuid NULL,
    doctor_staff_id uuid NULL,
    scheduled_start timestamptz(3) NOT NULL,
    scheduled_end timestamptz(3) NULL,
    status varchar(16) NOT NULL,
    reason varchar(500) NULL,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.encounters (
    id uuid NOT NULL CONSTRAINT PK_encounters PRIMARY KEY,
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
    checked_in_by_user_id uuid NULL
);

CREATE TABLE public.encounter_assignments (
    id uuid NOT NULL CONSTRAINT PK_encounter_assignments PRIMARY KEY,
    encounter_id uuid NOT NULL,
    department_id uuid NOT NULL,
    room_id uuid NULL,
    doctor_staff_id uuid NULL,
    assigned_at timestamptz(3) NOT NULL,
    ended_at timestamptz(3) NULL,
    assigned_by_user_id uuid NULL,
    destination_label varchar(200) NULL
);

CREATE TABLE public.journeys (
    id uuid NOT NULL CONSTRAINT PK_journeys PRIMARY KEY,
    encounter_id uuid NOT NULL,
    current_stage varchar(40) NOT NULL,
    current_department_id uuid NULL,
    current_room_id uuid NULL,
    stage_entered_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE public.journey_events (
    id uuid NOT NULL CONSTRAINT PK_journey_events PRIMARY KEY,
    journey_id uuid NOT NULL,
    from_stage varchar(40) NULL,
    to_stage varchar(40) NOT NULL,
    department_id uuid NULL,
    room_id uuid NULL,
    reason varchar(500) NULL,
    occurred_at timestamptz(3) NOT NULL,
    actor_user_id uuid NULL
);

CREATE TABLE public.vital_signs (
    id uuid NOT NULL CONSTRAINT PK_vital_signs PRIMARY KEY,
    encounter_id uuid NOT NULL,
    height_cm decimal(6,2) NULL,
    weight_kg decimal(6,2) NULL,
    bmi decimal(6,2) NULL,
    pulse_bpm int NULL,
    systolic_bp int NULL,
    diastolic_bp int NULL,
    physical_classification varchar(100) NULL,
    measured_at timestamptz(3) NOT NULL,
    recorded_by_staff_id uuid NULL
);

CREATE TABLE public.clinical_notes (
    id uuid NOT NULL CONSTRAINT PK_clinical_notes PRIMARY KEY,
    encounter_id uuid NOT NULL,
    note_type varchar(32) NOT NULL,
    content_json text NOT NULL,
    author_staff_id uuid NOT NULL,
    status varchar(16) NOT NULL,
    created_at timestamptz(3) NOT NULL,
    finalized_at timestamptz(3) NULL
);

CREATE TABLE public.encounter_diagnoses (
    id uuid NOT NULL CONSTRAINT PK_encounter_diagnoses PRIMARY KEY,
    encounter_id uuid NOT NULL,
    diagnosis_catalog_id uuid NULL,
    diagnosis_text varchar(500) NOT NULL,
    diagnosis_type varchar(24) NOT NULL,
    is_primary boolean NOT NULL DEFAULT false,
    recorded_by_staff_id uuid NOT NULL,
    recorded_at timestamptz(3) NOT NULL
);

CREATE TABLE public.order_rounds (
    id uuid NOT NULL CONSTRAINT PK_order_rounds PRIMARY KEY,
    encounter_id uuid NOT NULL,
    health_check_record_id uuid NULL,
    round_number int NOT NULL,
    source_type varchar(24) NOT NULL,
    status varchar(16) NOT NULL,
    ordered_by_staff_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    ordered_at timestamptz(3) NOT NULL
);

CREATE TABLE public.service_requests (
    id uuid NOT NULL CONSTRAINT PK_service_requests PRIMARY KEY,
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
    completed_at timestamptz(3) NULL
);

CREATE TABLE public.payment_authorizations (
    id uuid NOT NULL CONSTRAINT PK_payment_authorizations PRIMARY KEY,
    service_request_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    invoice_item_id uuid NULL,
    authorized_at timestamptz(3) NULL,
    authorized_by_user_id uuid NULL,
    reason varchar(500) NULL,
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.invoices (
    id uuid NOT NULL CONSTRAINT PK_invoices PRIMARY KEY,
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
    created_by_user_id uuid NOT NULL
);

CREATE TABLE public.invoice_items (
    id uuid NOT NULL CONSTRAINT PK_invoice_items PRIMARY KEY,
    invoice_id uuid NOT NULL,
    service_request_id uuid NULL,
    service_id uuid NULL,
    description_snapshot varchar(300) NOT NULL,
    quantity decimal(12,3) NOT NULL DEFAULT 1,
    unit_price decimal(18,2) NOT NULL,
    discount_amount decimal(18,2) NOT NULL DEFAULT 0,
    line_total decimal(18,2) NOT NULL
);

CREATE TABLE public.invoice_adjustments (
    id uuid NOT NULL CONSTRAINT PK_invoice_adjustments PRIMARY KEY,
    invoice_id uuid NOT NULL,
    adjustment_type varchar(24) NOT NULL,
    amount decimal(18,2) NOT NULL,
    reason varchar(500) NOT NULL,
    created_by_user_id uuid NOT NULL,
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.payments (
    id uuid NOT NULL CONSTRAINT PK_payments PRIMARY KEY,
    invoice_id uuid NOT NULL,
    payment_method varchar(24) NOT NULL,
    amount decimal(18,2) NOT NULL,
    status varchar(24) NOT NULL,
    gateway_transaction_id varchar(150) NULL,
    confirmed_by_user_id uuid NULL,
    confirmed_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.companies (
    id uuid NOT NULL CONSTRAINT PK_companies PRIMARY KEY,
    company_code varchar(40) NOT NULL,
    company_name varchar(300) NOT NULL,
    tax_code varchar(40) NULL,
    address varchar(500) NULL,
    contact_name varchar(200) NOT NULL,
    contact_phone varchar(30) NOT NULL,
    contact_job_title varchar(150) NULL,
    note varchar(1000) NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL,
    row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE public.company_employees (
    id uuid NOT NULL CONSTRAINT PK_company_employees PRIMARY KEY,
    company_id uuid NOT NULL,
    patient_id uuid NULL,
    employee_code varchar(60) NOT NULL,
    identification_number varchar(20) NOT NULL,
    full_name varchar(200) NOT NULL,
    date_of_birth date NOT NULL,
    sex varchar(16) NOT NULL,
    department_name varchar(200) NULL,
    job_title varchar(200) NULL,
    occupation varchar(200) NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.health_check_batches (
    id uuid NOT NULL CONSTRAINT PK_health_check_batches PRIMARY KEY,
    company_id uuid NOT NULL,
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
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.health_check_batch_services (
    id uuid NOT NULL CONSTRAINT PK_health_check_batch_services PRIMARY KEY,
    health_check_batch_id uuid NOT NULL,
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
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.health_check_batch_employees (
    id uuid NOT NULL CONSTRAINT PK_health_check_batch_employees PRIMARY KEY,
    health_check_batch_id uuid NOT NULL,
    company_employee_id uuid NOT NULL,
    employee_code_snapshot varchar(60) NOT NULL,
    department_snapshot varchar(200) NULL,
    job_title_snapshot varchar(200) NULL,
    occupation_snapshot varchar(200) NULL,
    administrative_snapshot_json text NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'REGISTERED',
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.health_check_records (
    id uuid NOT NULL CONSTRAINT PK_health_check_records PRIMARY KEY,
    shs_code varchar(40) NOT NULL,
    source_type varchar(16) NOT NULL,
    patient_id uuid NOT NULL,
    health_check_batch_employee_id uuid NULL,
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
    health_check_reason_snapshot varchar(500) NULL,
    planned_examination_date date NOT NULL,
    actual_examination_date date NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    replaces_health_check_record_id uuid NULL,
    created_at timestamptz(3) NOT NULL,
    completed_at timestamptz(3) NULL,
    canceled_at timestamptz(3) NULL,
    row_version bigint NOT NULL DEFAULT 0
);

CREATE TABLE public.health_check_batch_employee_services (
    id uuid NOT NULL CONSTRAINT PK_health_check_batch_employee_services PRIMARY KEY,
    health_check_batch_employee_id uuid NOT NULL,
    health_check_batch_service_id uuid NOT NULL,
    service_request_id uuid NOT NULL,
    billable boolean NOT NULL DEFAULT false,
    unit_price_snapshot decimal(18,2) NOT NULL,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.health_check_import_jobs (
    id uuid NOT NULL CONSTRAINT PK_health_check_import_jobs PRIMARY KEY,
    health_check_batch_id uuid NOT NULL,
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
    confirmed_at timestamptz(3) NULL
);

CREATE TABLE public.health_check_import_rows (
    id uuid NOT NULL CONSTRAINT PK_health_check_import_rows PRIMARY KEY,
    health_check_import_job_id uuid NOT NULL,
    row_number int NOT NULL,
    employee_code_snapshot varchar(60) NULL,
    identification_number_snapshot varchar(20) NULL,
    service_code_snapshot varchar(40) NULL,
    validation_status varchar(16) NOT NULL,
    error_codes_json text NULL,
    normalized_payload_json text NOT NULL,
    resolved_patient_id uuid NULL,
    resolved_company_employee_id uuid NULL,
    resolved_batch_employee_id uuid NULL,
    resolved_batch_service_id uuid NULL,
    resolved_service_request_id uuid NULL
);

CREATE TABLE public.specimens (
    id uuid NOT NULL CONSTRAINT PK_specimens PRIMARY KEY,
    specimen_code varchar(50) NOT NULL,
    patient_id uuid NOT NULL,
    encounter_id uuid NULL,
    specimen_type varchar(40) NOT NULL,
    status varchar(20) NOT NULL,
    collected_at timestamptz(3) NULL,
    collected_by_staff_id uuid NULL,
    received_at timestamptz(3) NULL
);

CREATE TABLE public.specimen_service_requests (
    id uuid NOT NULL CONSTRAINT PK_specimen_service_requests PRIMARY KEY,
    specimen_id uuid NOT NULL,
    service_request_id uuid NOT NULL
);

CREATE TABLE public.lab_panels (
    id uuid NOT NULL CONSTRAINT PK_lab_panels PRIMARY KEY,
    panel_code varchar(40) NOT NULL,
    panel_name varchar(250) NOT NULL,
    is_active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.analytes (
    id uuid NOT NULL CONSTRAINT PK_analytes PRIMARY KEY,
    analyte_code varchar(40) NOT NULL,
    analyte_name varchar(250) NOT NULL,
    default_unit varchar(40) NULL,
    value_type varchar(20) NOT NULL,
    is_active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.lab_panel_items (
    id uuid NOT NULL CONSTRAINT PK_lab_panel_items PRIMARY KEY,
    lab_panel_id uuid NOT NULL,
    analyte_id uuid NOT NULL,
    display_order int NOT NULL
);

CREATE TABLE public.analyte_reference_ranges (
    id uuid NOT NULL CONSTRAINT PK_analyte_reference_ranges PRIMARY KEY,
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
    effective_to timestamptz(3) NULL
);

CREATE TABLE public.lab_results (
    id uuid NOT NULL CONSTRAINT PK_lab_results PRIMARY KEY,
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
    raw_message_reference varchar(200) NULL
);

CREATE TABLE public.lab_result_values (
    id uuid NOT NULL CONSTRAINT PK_lab_result_values PRIMARY KEY,
    lab_result_id uuid NOT NULL,
    analyte_id uuid NOT NULL,
    numeric_value decimal(18,6) NULL,
    text_value varchar(500) NULL,
    unit_snapshot varchar(40) NULL,
    reference_range_snapshot varchar(200) NULL,
    abnormal_flag varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    instrument_code varchar(50) NULL,
    measured_at timestamptz(3) NULL
);

CREATE TABLE public.imaging_studies (
    id uuid NOT NULL CONSTRAINT PK_imaging_studies PRIMARY KEY,
    service_request_id uuid NOT NULL,
    modality varchar(16) NOT NULL,
    external_study_uid varchar(200) NULL,
    device_identifier varchar(100) NULL,
    study_at timestamptz(3) NULL,
    metadata_json text NULL,
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.diagnostic_reports (
    id uuid NOT NULL CONSTRAINT PK_diagnostic_reports PRIMARY KEY,
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
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.file_attachments (
    id uuid NOT NULL CONSTRAINT PK_file_attachments PRIMARY KEY,
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
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.prescriptions (
    id uuid NOT NULL CONSTRAINT PK_prescriptions PRIMARY KEY,
    prescription_number varchar(40) NOT NULL,
    encounter_id uuid NOT NULL,
    patient_id uuid NOT NULL,
    prescriber_staff_id uuid NOT NULL,
    version_number int NOT NULL,
    status varchar(16) NOT NULL,
    supersedes_prescription_id uuid NULL,
    issued_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.prescription_items (
    id uuid NOT NULL CONSTRAINT PK_prescription_items PRIMARY KEY,
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
    display_order int NOT NULL
);

CREATE TABLE public.users (
    id uuid NOT NULL CONSTRAINT PK_users PRIMARY KEY,
    principal_type varchar(16) NOT NULL,
    staff_id uuid NULL,
    patient_id uuid NULL,
    auth_provider varchar(32) NOT NULL,
    auth_subject varchar(200) NOT NULL,
    status varchar(16) NOT NULL,
    last_login_at timestamptz(3) NULL,
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.roles (
    id uuid NOT NULL CONSTRAINT PK_roles PRIMARY KEY,
    role_code varchar(40) NOT NULL,
    role_name varchar(150) NOT NULL,
    is_active boolean NOT NULL DEFAULT true
);

CREATE TABLE public.permissions (
    id uuid NOT NULL CONSTRAINT PK_permissions PRIMARY KEY,
    permission_code varchar(80) NOT NULL,
    module varchar(40) NOT NULL,
    description varchar(300) NULL
);

CREATE TABLE public.user_roles (
    id uuid NOT NULL CONSTRAINT PK_user_roles PRIMARY KEY,
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    department_id uuid NULL,
    room_id uuid NULL,
    valid_from timestamptz(3) NOT NULL,
    valid_to timestamptz(3) NULL
);

CREATE TABLE public.role_permissions (
    id uuid NOT NULL CONSTRAINT PK_role_permissions PRIMARY KEY,
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);

CREATE TABLE public.notifications (
    id uuid NOT NULL CONSTRAINT PK_notifications PRIMARY KEY,
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
    created_at timestamptz(3) NOT NULL
);

CREATE TABLE public.notification_attempts (
    id uuid NOT NULL CONSTRAINT PK_notification_attempts PRIMARY KEY,
    notification_id uuid NOT NULL,
    attempt_number int NOT NULL,
    provider_code varchar(40) NOT NULL,
    provider_message_id varchar(150) NULL,
    status varchar(20) NOT NULL,
    request_reference varchar(500) NULL,
    response_reference varchar(500) NULL,
    error_code varchar(100) NULL,
    error_message varchar(1000) NULL,
    attempted_at timestamptz(3) NOT NULL
);

CREATE TABLE public.audit_logs (
    id uuid NOT NULL CONSTRAINT PK_audit_logs PRIMARY KEY,
    occurred_at timestamptz(3) NOT NULL,
    actor_user_id uuid NULL,
    action varchar(80) NOT NULL,
    entity_type varchar(50) NOT NULL,
    entity_id varchar(100) NOT NULL,
    patient_id uuid NULL,
    encounter_id uuid NULL,
    health_check_record_id uuid NULL,
    correlation_id uuid NULL,
    reason varchar(500) NULL,
    before_json text NULL,
    after_json text NULL,
    ip_address varchar(64) NULL,
    user_agent varchar(500) NULL
);

CREATE TABLE public.integration_endpoints (
    id uuid NOT NULL CONSTRAINT PK_integration_endpoints PRIMARY KEY,
    endpoint_code varchar(40) NOT NULL,
    integration_type varchar(32) NOT NULL,
    endpoint_name varchar(200) NOT NULL,
    base_url varchar(500) NULL,
    credential_reference varchar(300) NULL,
    config_json text NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz(3) NOT NULL,
    updated_at timestamptz(3) NOT NULL
);

CREATE TABLE public.external_code_mappings (
    id uuid NOT NULL CONSTRAINT PK_external_code_mappings PRIMARY KEY,
    integration_endpoint_id uuid NOT NULL,
    mapping_type varchar(32) NOT NULL,
    external_code varchar(100) NOT NULL,
    internal_entity_type varchar(32) NOT NULL,
    internal_entity_id uuid NOT NULL,
    valid_from timestamptz(3) NOT NULL,
    valid_to timestamptz(3) NULL
);

CREATE TABLE public.integration_messages (
    id uuid NOT NULL CONSTRAINT PK_integration_messages PRIMARY KEY,
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
    retry_count int NOT NULL DEFAULT 0
);

CREATE TABLE public.idempotency_keys (
    id uuid NOT NULL CONSTRAINT PK_idempotency_keys PRIMARY KEY,
    scope varchar(50) NOT NULL,
    idempotency_key varchar(120) NOT NULL,
    request_hash char(64) NULL,
    result_entity_type varchar(50) NULL,
    result_entity_id varchar(100) NULL,
    status varchar(16) NOT NULL,
    created_at timestamptz(3) NOT NULL,
    expires_at timestamptz(3) NULL
);

CREATE TABLE public.outbox_events (
    id uuid NOT NULL CONSTRAINT PK_outbox_events PRIMARY KEY,
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

-- Foreign keys are added after all tables so forward/self references are valid.
ALTER TABLE public.patient_allergies ADD CONSTRAINT FK_patient_allergies_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.patient_allergies ADD CONSTRAINT FK_patient_allergies_recorded_by_user_id FOREIGN KEY (recorded_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.patient_conditions ADD CONSTRAINT FK_patient_conditions_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.rooms ADD CONSTRAINT FK_rooms_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id);
ALTER TABLE public.staff_department_assignments ADD CONSTRAINT FK_staff_department_assignments_staff_id FOREIGN KEY (staff_id) REFERENCES public.staff(id);
ALTER TABLE public.staff_department_assignments ADD CONSTRAINT FK_staff_department_assignments_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id);
ALTER TABLE public.services ADD CONSTRAINT FK_services_performing_department_id FOREIGN KEY (performing_department_id) REFERENCES public.departments(id);
ALTER TABLE public.services ADD CONSTRAINT FK_services_default_room_id FOREIGN KEY (default_room_id) REFERENCES public.rooms(id);
ALTER TABLE public.services ADD CONSTRAINT FK_services_lab_panel_id FOREIGN KEY (lab_panel_id) REFERENCES public.lab_panels(id);
ALTER TABLE public.service_prices ADD CONSTRAINT FK_service_prices_service_id FOREIGN KEY (service_id) REFERENCES public.services(id);
ALTER TABLE public.document_template_versions ADD CONSTRAINT FK_document_template_versions_document_template_id FOREIGN KEY (document_template_id) REFERENCES public.document_templates(id);
ALTER TABLE public.document_template_versions ADD CONSTRAINT FK_document_template_versions_source_file_attachment_id FOREIGN KEY (source_file_attachment_id) REFERENCES public.file_attachments(id);
ALTER TABLE public.document_template_versions ADD CONSTRAINT FK_document_template_versions_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.document_template_fields ADD CONSTRAINT FK_document_template_fields_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES public.document_template_versions(id);
ALTER TABLE public.service_template_mappings ADD CONSTRAINT FK_service_template_mappings_service_id FOREIGN KEY (service_id) REFERENCES public.services(id);
ALTER TABLE public.service_template_mappings ADD CONSTRAINT FK_service_template_mappings_document_template_id FOREIGN KEY (document_template_id) REFERENCES public.document_templates(id);
ALTER TABLE public.generated_documents ADD CONSTRAINT FK_generated_documents_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.generated_documents ADD CONSTRAINT FK_generated_documents_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES public.document_template_versions(id);
ALTER TABLE public.generated_documents ADD CONSTRAINT FK_generated_documents_file_attachment_id FOREIGN KEY (file_attachment_id) REFERENCES public.file_attachments(id);
ALTER TABLE public.generated_documents ADD CONSTRAINT FK_generated_documents_generated_by_user_id FOREIGN KEY (generated_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.generated_document_service_requests ADD CONSTRAINT FK_generated_document_service_requests_generated_document_id FOREIGN KEY (generated_document_id) REFERENCES public.generated_documents(id);
ALTER TABLE public.generated_document_service_requests ADD CONSTRAINT FK_generated_document_service_requests_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id);
ALTER TABLE public.appointments ADD CONSTRAINT FK_appointments_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.appointments ADD CONSTRAINT FK_appointments_source_encounter_id FOREIGN KEY (source_encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.appointments ADD CONSTRAINT FK_appointments_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id);
ALTER TABLE public.appointments ADD CONSTRAINT FK_appointments_doctor_staff_id FOREIGN KEY (doctor_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.encounters ADD CONSTRAINT FK_encounters_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.encounters ADD CONSTRAINT FK_encounters_checked_in_by_user_id FOREIGN KEY (checked_in_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.encounter_assignments ADD CONSTRAINT FK_encounter_assignments_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.encounter_assignments ADD CONSTRAINT FK_encounter_assignments_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id);
ALTER TABLE public.encounter_assignments ADD CONSTRAINT FK_encounter_assignments_room_id FOREIGN KEY (room_id) REFERENCES public.rooms(id);
ALTER TABLE public.encounter_assignments ADD CONSTRAINT FK_encounter_assignments_doctor_staff_id FOREIGN KEY (doctor_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.encounter_assignments ADD CONSTRAINT FK_encounter_assignments_assigned_by_user_id FOREIGN KEY (assigned_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.journeys ADD CONSTRAINT FK_journeys_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.journeys ADD CONSTRAINT FK_journeys_current_department_id FOREIGN KEY (current_department_id) REFERENCES public.departments(id);
ALTER TABLE public.journeys ADD CONSTRAINT FK_journeys_current_room_id FOREIGN KEY (current_room_id) REFERENCES public.rooms(id);
ALTER TABLE public.journey_events ADD CONSTRAINT FK_journey_events_journey_id FOREIGN KEY (journey_id) REFERENCES public.journeys(id);
ALTER TABLE public.journey_events ADD CONSTRAINT FK_journey_events_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id);
ALTER TABLE public.journey_events ADD CONSTRAINT FK_journey_events_room_id FOREIGN KEY (room_id) REFERENCES public.rooms(id);
ALTER TABLE public.journey_events ADD CONSTRAINT FK_journey_events_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES public.users(id);
ALTER TABLE public.vital_signs ADD CONSTRAINT FK_vital_signs_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.vital_signs ADD CONSTRAINT FK_vital_signs_recorded_by_staff_id FOREIGN KEY (recorded_by_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.clinical_notes ADD CONSTRAINT FK_clinical_notes_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.clinical_notes ADD CONSTRAINT FK_clinical_notes_author_staff_id FOREIGN KEY (author_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.encounter_diagnoses ADD CONSTRAINT FK_encounter_diagnoses_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.encounter_diagnoses ADD CONSTRAINT FK_encounter_diagnoses_diagnosis_catalog_id FOREIGN KEY (diagnosis_catalog_id) REFERENCES public.diagnosis_catalog(id);
ALTER TABLE public.encounter_diagnoses ADD CONSTRAINT FK_encounter_diagnoses_recorded_by_staff_id FOREIGN KEY (recorded_by_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.order_rounds ADD CONSTRAINT FK_order_rounds_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.order_rounds ADD CONSTRAINT FK_order_rounds_health_check_record_id FOREIGN KEY (health_check_record_id) REFERENCES public.health_check_records(id);
ALTER TABLE public.order_rounds ADD CONSTRAINT FK_order_rounds_ordered_by_staff_id FOREIGN KEY (ordered_by_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.order_rounds ADD CONSTRAINT FK_order_rounds_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.service_requests ADD CONSTRAINT FK_service_requests_order_round_id FOREIGN KEY (order_round_id) REFERENCES public.order_rounds(id);
ALTER TABLE public.service_requests ADD CONSTRAINT FK_service_requests_service_id FOREIGN KEY (service_id) REFERENCES public.services(id);
ALTER TABLE public.service_requests ADD CONSTRAINT FK_service_requests_performing_department_id FOREIGN KEY (performing_department_id) REFERENCES public.departments(id);
ALTER TABLE public.service_requests ADD CONSTRAINT FK_service_requests_performing_room_id FOREIGN KEY (performing_room_id) REFERENCES public.rooms(id);
ALTER TABLE public.payment_authorizations ADD CONSTRAINT FK_payment_authorizations_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id);
ALTER TABLE public.payment_authorizations ADD CONSTRAINT FK_payment_authorizations_invoice_item_id FOREIGN KEY (invoice_item_id) REFERENCES public.invoice_items(id);
ALTER TABLE public.payment_authorizations ADD CONSTRAINT FK_payment_authorizations_authorized_by_user_id FOREIGN KEY (authorized_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.invoices ADD CONSTRAINT FK_invoices_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.invoices ADD CONSTRAINT FK_invoices_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.invoices ADD CONSTRAINT FK_invoices_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.invoice_items ADD CONSTRAINT FK_invoice_items_invoice_id FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);
ALTER TABLE public.invoice_items ADD CONSTRAINT FK_invoice_items_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id);
ALTER TABLE public.invoice_items ADD CONSTRAINT FK_invoice_items_service_id FOREIGN KEY (service_id) REFERENCES public.services(id);
ALTER TABLE public.invoice_adjustments ADD CONSTRAINT FK_invoice_adjustments_invoice_id FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);
ALTER TABLE public.invoice_adjustments ADD CONSTRAINT FK_invoice_adjustments_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.payments ADD CONSTRAINT FK_payments_invoice_id FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);
ALTER TABLE public.payments ADD CONSTRAINT FK_payments_confirmed_by_user_id FOREIGN KEY (confirmed_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.company_employees ADD CONSTRAINT FK_company_employees_company_id FOREIGN KEY (company_id) REFERENCES public.companies(id);
ALTER TABLE public.company_employees ADD CONSTRAINT FK_company_employees_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.health_check_batches ADD CONSTRAINT FK_health_check_batches_company_id FOREIGN KEY (company_id) REFERENCES public.companies(id);
ALTER TABLE public.health_check_batches ADD CONSTRAINT FK_health_check_batches_master_template_version_id FOREIGN KEY (master_template_version_id) REFERENCES public.document_template_versions(id);
ALTER TABLE public.health_check_batches ADD CONSTRAINT FK_health_check_batches_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.health_check_batch_services ADD CONSTRAINT FK_health_check_batch_services_health_check_batch_id FOREIGN KEY (health_check_batch_id) REFERENCES public.health_check_batches(id);
ALTER TABLE public.health_check_batch_services ADD CONSTRAINT FK_health_check_batch_services_service_id FOREIGN KEY (service_id) REFERENCES public.services(id);
ALTER TABLE public.health_check_batch_services ADD CONSTRAINT FK_health_check_batch_services_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES public.document_template_versions(id);
ALTER TABLE public.health_check_batch_employees ADD CONSTRAINT FK_health_check_batch_employees_health_check_batch_id FOREIGN KEY (health_check_batch_id) REFERENCES public.health_check_batches(id);
ALTER TABLE public.health_check_batch_employees ADD CONSTRAINT FK_health_check_batch_employees_company_employee_id FOREIGN KEY (company_employee_id) REFERENCES public.company_employees(id);
ALTER TABLE public.health_check_records ADD CONSTRAINT FK_health_check_records_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.health_check_records ADD CONSTRAINT FK_health_check_records_health_check_batch_employee_id FOREIGN KEY (health_check_batch_employee_id) REFERENCES public.health_check_batch_employees(id);
ALTER TABLE public.health_check_records ADD CONSTRAINT FK_health_check_records_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.health_check_records ADD CONSTRAINT FK_health_check_records_master_template_version_id FOREIGN KEY (master_template_version_id) REFERENCES public.document_template_versions(id);
ALTER TABLE public.health_check_records ADD CONSTRAINT FK_health_check_records_replaces_health_check_record_id FOREIGN KEY (replaces_health_check_record_id) REFERENCES public.health_check_records(id);
ALTER TABLE public.health_check_batch_employee_services ADD CONSTRAINT FK_health_check_batch_employee_services_employee FOREIGN KEY (health_check_batch_employee_id) REFERENCES public.health_check_batch_employees(id);
ALTER TABLE public.health_check_batch_employee_services ADD CONSTRAINT FK_health_check_batch_employee_services_service FOREIGN KEY (health_check_batch_service_id) REFERENCES public.health_check_batch_services(id);
ALTER TABLE public.health_check_batch_employee_services ADD CONSTRAINT FK_health_check_batch_employee_services_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id);
ALTER TABLE public.health_check_import_jobs ADD CONSTRAINT FK_health_check_import_jobs_health_check_batch_id FOREIGN KEY (health_check_batch_id) REFERENCES public.health_check_batches(id);
ALTER TABLE public.health_check_import_jobs ADD CONSTRAINT FK_health_check_import_jobs_source_file_attachment_id FOREIGN KEY (source_file_attachment_id) REFERENCES public.file_attachments(id);
ALTER TABLE public.health_check_import_jobs ADD CONSTRAINT FK_health_check_import_jobs_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.health_check_import_jobs ADD CONSTRAINT FK_health_check_import_jobs_confirmed_by_user_id FOREIGN KEY (confirmed_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_health_check_import_job_id FOREIGN KEY (health_check_import_job_id) REFERENCES public.health_check_import_jobs(id);
ALTER TABLE public.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_resolved_patient_id FOREIGN KEY (resolved_patient_id) REFERENCES public.patients(id);
ALTER TABLE public.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_resolved_company_employee_id FOREIGN KEY (resolved_company_employee_id) REFERENCES public.company_employees(id);
ALTER TABLE public.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_resolved_batch_employee_id FOREIGN KEY (resolved_batch_employee_id) REFERENCES public.health_check_batch_employees(id);
ALTER TABLE public.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_resolved_batch_service_id FOREIGN KEY (resolved_batch_service_id) REFERENCES public.health_check_batch_services(id);
ALTER TABLE public.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_resolved_service_request_id FOREIGN KEY (resolved_service_request_id) REFERENCES public.service_requests(id);
ALTER TABLE public.specimens ADD CONSTRAINT FK_specimens_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.specimens ADD CONSTRAINT FK_specimens_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.specimens ADD CONSTRAINT FK_specimens_collected_by_staff_id FOREIGN KEY (collected_by_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.specimen_service_requests ADD CONSTRAINT FK_specimen_service_requests_specimen_id FOREIGN KEY (specimen_id) REFERENCES public.specimens(id);
ALTER TABLE public.specimen_service_requests ADD CONSTRAINT FK_specimen_service_requests_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id);
ALTER TABLE public.lab_panel_items ADD CONSTRAINT FK_lab_panel_items_lab_panel_id FOREIGN KEY (lab_panel_id) REFERENCES public.lab_panels(id);
ALTER TABLE public.lab_panel_items ADD CONSTRAINT FK_lab_panel_items_analyte_id FOREIGN KEY (analyte_id) REFERENCES public.analytes(id);
ALTER TABLE public.analyte_reference_ranges ADD CONSTRAINT FK_analyte_reference_ranges_analyte_id FOREIGN KEY (analyte_id) REFERENCES public.analytes(id);
ALTER TABLE public.lab_results ADD CONSTRAINT FK_lab_results_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id);
ALTER TABLE public.lab_results ADD CONSTRAINT FK_lab_results_specimen_id FOREIGN KEY (specimen_id) REFERENCES public.specimens(id);
ALTER TABLE public.lab_results ADD CONSTRAINT FK_lab_results_supersedes_lab_result_id FOREIGN KEY (supersedes_lab_result_id) REFERENCES public.lab_results(id);
ALTER TABLE public.lab_results ADD CONSTRAINT FK_lab_results_verified_by_staff_id FOREIGN KEY (verified_by_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.lab_result_values ADD CONSTRAINT FK_lab_result_values_lab_result_id FOREIGN KEY (lab_result_id) REFERENCES public.lab_results(id);
ALTER TABLE public.lab_result_values ADD CONSTRAINT FK_lab_result_values_analyte_id FOREIGN KEY (analyte_id) REFERENCES public.analytes(id);
ALTER TABLE public.imaging_studies ADD CONSTRAINT FK_imaging_studies_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id);
ALTER TABLE public.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_service_request_id FOREIGN KEY (service_request_id) REFERENCES public.service_requests(id);
ALTER TABLE public.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_imaging_study_id FOREIGN KEY (imaging_study_id) REFERENCES public.imaging_studies(id);
ALTER TABLE public.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES public.document_template_versions(id);
ALTER TABLE public.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_supersedes_report_id FOREIGN KEY (supersedes_report_id) REFERENCES public.diagnostic_reports(id);
ALTER TABLE public.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_author_staff_id FOREIGN KEY (author_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_verified_by_staff_id FOREIGN KEY (verified_by_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.file_attachments ADD CONSTRAINT FK_file_attachments_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.file_attachments ADD CONSTRAINT FK_file_attachments_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.file_attachments ADD CONSTRAINT FK_file_attachments_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES public.users(id);
ALTER TABLE public.prescriptions ADD CONSTRAINT FK_prescriptions_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.prescriptions ADD CONSTRAINT FK_prescriptions_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.prescriptions ADD CONSTRAINT FK_prescriptions_prescriber_staff_id FOREIGN KEY (prescriber_staff_id) REFERENCES public.staff(id);
ALTER TABLE public.prescriptions ADD CONSTRAINT FK_prescriptions_supersedes_prescription_id FOREIGN KEY (supersedes_prescription_id) REFERENCES public.prescriptions(id);
ALTER TABLE public.prescription_items ADD CONSTRAINT FK_prescription_items_prescription_id FOREIGN KEY (prescription_id) REFERENCES public.prescriptions(id);
ALTER TABLE public.prescription_items ADD CONSTRAINT FK_prescription_items_medication_id FOREIGN KEY (medication_id) REFERENCES public.medications(id);
ALTER TABLE public.users ADD CONSTRAINT FK_users_staff_id FOREIGN KEY (staff_id) REFERENCES public.staff(id);
ALTER TABLE public.users ADD CONSTRAINT FK_users_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.user_roles ADD CONSTRAINT FK_user_roles_user_id FOREIGN KEY (user_id) REFERENCES public.users(id);
ALTER TABLE public.user_roles ADD CONSTRAINT FK_user_roles_role_id FOREIGN KEY (role_id) REFERENCES public.roles(id);
ALTER TABLE public.user_roles ADD CONSTRAINT FK_user_roles_department_id FOREIGN KEY (department_id) REFERENCES public.departments(id);
ALTER TABLE public.user_roles ADD CONSTRAINT FK_user_roles_room_id FOREIGN KEY (room_id) REFERENCES public.rooms(id);
ALTER TABLE public.role_permissions ADD CONSTRAINT FK_role_permissions_role_id FOREIGN KEY (role_id) REFERENCES public.roles(id);
ALTER TABLE public.role_permissions ADD CONSTRAINT FK_role_permissions_permission_id FOREIGN KEY (permission_id) REFERENCES public.permissions(id);
ALTER TABLE public.notifications ADD CONSTRAINT FK_notifications_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.notifications ADD CONSTRAINT FK_notifications_appointment_id FOREIGN KEY (appointment_id) REFERENCES public.appointments(id);
ALTER TABLE public.notifications ADD CONSTRAINT FK_notifications_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.notification_attempts ADD CONSTRAINT FK_notification_attempts_notification_id FOREIGN KEY (notification_id) REFERENCES public.notifications(id);
ALTER TABLE public.audit_logs ADD CONSTRAINT FK_audit_logs_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES public.users(id);
ALTER TABLE public.audit_logs ADD CONSTRAINT FK_audit_logs_patient_id FOREIGN KEY (patient_id) REFERENCES public.patients(id);
ALTER TABLE public.audit_logs ADD CONSTRAINT FK_audit_logs_encounter_id FOREIGN KEY (encounter_id) REFERENCES public.encounters(id);
ALTER TABLE public.audit_logs ADD CONSTRAINT FK_audit_logs_health_check_record_id FOREIGN KEY (health_check_record_id) REFERENCES public.health_check_records(id);
ALTER TABLE public.external_code_mappings ADD CONSTRAINT FK_external_code_mappings_integration_endpoint_id FOREIGN KEY (integration_endpoint_id) REFERENCES public.integration_endpoints(id);
ALTER TABLE public.integration_messages ADD CONSTRAINT FK_integration_messages_integration_endpoint_id FOREIGN KEY (integration_endpoint_id) REFERENCES public.integration_endpoints(id);

ALTER TABLE public.departments ADD CONSTRAINT UQ_departments_department_code UNIQUE (department_code);
ALTER TABLE public.rooms ADD CONSTRAINT UQ_rooms_department_id_room_code UNIQUE (department_id, room_code);
ALTER TABLE public.staff ADD CONSTRAINT UQ_staff_staff_code UNIQUE (staff_code);
ALTER TABLE public.diagnosis_catalog ADD CONSTRAINT UQ_diagnosis_catalog_diagnosis_code UNIQUE (diagnosis_code);
ALTER TABLE public.services ADD CONSTRAINT UQ_services_service_code UNIQUE (service_code);
ALTER TABLE public.medications ADD CONSTRAINT UQ_medications_medication_code UNIQUE (medication_code);
ALTER TABLE public.document_templates ADD CONSTRAINT UQ_document_templates_template_code UNIQUE (template_code);
ALTER TABLE public.document_template_versions ADD CONSTRAINT UQ_document_template_versions_template_version UNIQUE (document_template_id, version_number);
ALTER TABLE public.document_template_fields ADD CONSTRAINT UQ_document_template_fields_version_field UNIQUE (document_template_version_id, field_key);
ALTER TABLE public.service_template_mappings ADD CONSTRAINT UQ_service_template_mappings_service_id_document_template_id UNIQUE (service_id, document_template_id);
ALTER TABLE public.generated_document_service_requests ADD CONSTRAINT UQ_generated_doc_service_requests_doc_request UNIQUE (generated_document_id, service_request_id);
ALTER TABLE public.encounters ADD CONSTRAINT UQ_encounters_encounter_code UNIQUE (encounter_code);
ALTER TABLE public.journeys ADD CONSTRAINT UQ_journeys_encounter_id UNIQUE (encounter_id);
ALTER TABLE public.order_rounds ADD CONSTRAINT UQ_order_rounds_encounter_id_round_number UNIQUE (encounter_id, round_number);
ALTER TABLE public.payment_authorizations ADD CONSTRAINT UQ_payment_authorizations_service_request_id UNIQUE (service_request_id);
ALTER TABLE public.invoices ADD CONSTRAINT UQ_invoices_invoice_number UNIQUE (invoice_number);
ALTER TABLE public.companies ADD CONSTRAINT UQ_companies_company_code UNIQUE (company_code);
ALTER TABLE public.company_employees ADD CONSTRAINT UQ_company_employees_company_id_employee_code UNIQUE (company_id, employee_code);
ALTER TABLE public.company_employees ADD CONSTRAINT UQ_company_employees_company_id_identification_number UNIQUE (company_id, identification_number);
ALTER TABLE public.health_check_batches ADD CONSTRAINT UQ_health_check_batches_company_id_batch_code UNIQUE (company_id, batch_code);
ALTER TABLE public.health_check_batch_services ADD CONSTRAINT UQ_health_check_batch_services_health_check_batch_id_service_id UNIQUE (health_check_batch_id, service_id);
ALTER TABLE public.health_check_batch_employees ADD CONSTRAINT UQ_health_check_batch_employees_batch_employee UNIQUE (health_check_batch_id, company_employee_id);
ALTER TABLE public.health_check_batch_employee_services ADD CONSTRAINT UQ_health_check_batch_employee_services_employee_service UNIQUE (health_check_batch_employee_id, health_check_batch_service_id);
ALTER TABLE public.health_check_import_rows ADD CONSTRAINT UQ_health_check_import_rows_job_row UNIQUE (health_check_import_job_id, row_number);
ALTER TABLE public.specimens ADD CONSTRAINT UQ_specimens_specimen_code UNIQUE (specimen_code);
ALTER TABLE public.specimen_service_requests ADD CONSTRAINT UQ_specimen_service_requests_specimen_id_service_request_id UNIQUE (specimen_id, service_request_id);
ALTER TABLE public.lab_panels ADD CONSTRAINT UQ_lab_panels_panel_code UNIQUE (panel_code);
ALTER TABLE public.analytes ADD CONSTRAINT UQ_analytes_analyte_code UNIQUE (analyte_code);
ALTER TABLE public.lab_panel_items ADD CONSTRAINT UQ_lab_panel_items_lab_panel_id_analyte_id UNIQUE (lab_panel_id, analyte_id);
ALTER TABLE public.lab_results ADD CONSTRAINT UQ_lab_results_service_request_id_version_number UNIQUE (service_request_id, version_number);
ALTER TABLE public.lab_result_values ADD CONSTRAINT UQ_lab_result_values_lab_result_id_analyte_id UNIQUE (lab_result_id, analyte_id);
ALTER TABLE public.diagnostic_reports ADD CONSTRAINT UQ_diagnostic_reports_service_request_id_version_number UNIQUE (service_request_id, version_number);
ALTER TABLE public.prescriptions ADD CONSTRAINT UQ_prescriptions_prescription_number UNIQUE (prescription_number);
ALTER TABLE public.users ADD CONSTRAINT UQ_users_auth_provider_auth_subject UNIQUE (auth_provider, auth_subject);
ALTER TABLE public.roles ADD CONSTRAINT UQ_roles_role_code UNIQUE (role_code);
ALTER TABLE public.permissions ADD CONSTRAINT UQ_permissions_permission_code UNIQUE (permission_code);
ALTER TABLE public.role_permissions ADD CONSTRAINT UQ_role_permissions_role_id_permission_id UNIQUE (role_id, permission_id);
ALTER TABLE public.notifications ADD CONSTRAINT UQ_notifications_idempotency_key UNIQUE (idempotency_key);
ALTER TABLE public.notification_attempts ADD CONSTRAINT UQ_notification_attempts_notification_id_attempt_number UNIQUE (notification_id, attempt_number);
ALTER TABLE public.integration_endpoints ADD CONSTRAINT UQ_integration_endpoints_endpoint_code UNIQUE (endpoint_code);
ALTER TABLE public.idempotency_keys ADD CONSTRAINT UQ_idempotency_keys_scope_idempotency_key UNIQUE (scope, idempotency_key);

CREATE UNIQUE INDEX UX_patients_identification_number ON public.patients (identification_number);
CREATE UNIQUE INDEX UX_patients_patient_code ON public.patients (patient_code);
CREATE INDEX IX_patients_name_dob ON public.patients (full_name_normalized, date_of_birth);
CREATE INDEX IX_patients_phone ON public.patients (phone) WHERE phone IS NOT NULL;
CREATE INDEX IX_patient_allergies_patient ON public.patient_allergies (patient_id, ended_at);
CREATE INDEX IX_patient_conditions_patient ON public.patient_conditions (patient_id, clinical_status);
CREATE UNIQUE INDEX UX_staff_department_assignments_active_primary ON public.staff_department_assignments (staff_id) WHERE is_primary = true AND valid_to IS NULL;
CREATE INDEX IX_staff_department_assignments_staff ON public.staff_department_assignments (staff_id, valid_to);
CREATE INDEX IX_staff_department_assignments_department ON public.staff_department_assignments (department_id, valid_to);
CREATE INDEX IX_service_prices_lookup ON public.service_prices (service_id, price_type, effective_from, effective_to);
CREATE INDEX IX_document_template_fields_version ON public.document_template_fields (document_template_version_id, display_order);
CREATE UNIQUE INDEX UX_service_template_mappings_active_service ON public.service_template_mappings (service_id) WHERE is_active = true;
CREATE INDEX IX_service_template_mappings_template ON public.service_template_mappings (document_template_id, is_active);
CREATE INDEX IX_generated_documents_encounter ON public.generated_documents (encounter_id, print_sequence, status);
CREATE INDEX IX_generated_document_service_requests_document ON public.generated_document_service_requests (generated_document_id);
CREATE INDEX IX_generated_document_service_requests_request ON public.generated_document_service_requests (service_request_id);
CREATE INDEX IX_appointments_patient ON public.appointments (patient_id, scheduled_start);
CREATE INDEX IX_appointments_source_encounter ON public.appointments (source_encounter_id) WHERE source_encounter_id IS NOT NULL;
CREATE INDEX IX_appointments_schedule ON public.appointments (scheduled_start, status);
CREATE INDEX IX_encounters_patient ON public.encounters (patient_id, started_at desc);
CREATE INDEX IX_encounters_status ON public.encounters (status, started_at);
CREATE UNIQUE INDEX UX_encounter_assignments_active ON public.encounter_assignments (encounter_id) WHERE ended_at IS NULL;
CREATE INDEX IX_encounter_assignments_doctor ON public.encounter_assignments (doctor_staff_id, ended_at);
CREATE INDEX IX_encounter_assignments_room ON public.encounter_assignments (room_id, ended_at, assigned_at);
CREATE INDEX IX_journeys_stage ON public.journeys (current_stage, current_room_id, stage_entered_at);
CREATE INDEX IX_journey_events_journey ON public.journey_events (journey_id, occurred_at);
CREATE INDEX IX_clinical_notes_encounter ON public.clinical_notes (encounter_id, created_at desc);
CREATE INDEX IX_service_requests_worklist ON public.service_requests (performing_department_id, performing_room_id, status, priority, ordered_at);
CREATE INDEX IX_payment_authorizations_status ON public.payment_authorizations (status, service_request_id);
CREATE INDEX IX_invoices_encounter ON public.invoices (encounter_id, status, issued_at);
CREATE INDEX IX_invoice_items_invoice ON public.invoice_items (invoice_id);
CREATE INDEX IX_payments_invoice ON public.payments (invoice_id, status);
CREATE UNIQUE INDEX UX_payments_gateway_transaction ON public.payments (gateway_transaction_id) WHERE gateway_transaction_id IS NOT NULL;
CREATE UNIQUE INDEX UX_companies_tax_code ON public.companies (tax_code) WHERE tax_code IS NOT NULL;
CREATE INDEX IX_companies_name ON public.companies (company_name);
CREATE INDEX IX_company_employees_company ON public.company_employees (company_id, status);
CREATE INDEX IX_company_employees_patient ON public.company_employees (patient_id);
CREATE UNIQUE INDEX UX_company_employees_company_patient ON public.company_employees (company_id, patient_id) WHERE patient_id IS NOT NULL;
CREATE INDEX IX_health_check_batches_company ON public.health_check_batches (company_id, status, start_date);
CREATE INDEX IX_health_check_batch_services_batch ON public.health_check_batch_services (health_check_batch_id, status);
CREATE INDEX IX_health_check_batch_employees_batch ON public.health_check_batch_employees (health_check_batch_id, status);
CREATE UNIQUE INDEX UX_health_check_records_shs_code ON public.health_check_records (shs_code);
CREATE UNIQUE INDEX UX_health_check_records_encounter ON public.health_check_records (encounter_id);
CREATE UNIQUE INDEX UX_health_check_records_batch_employee ON public.health_check_records (health_check_batch_employee_id) WHERE health_check_batch_employee_id IS NOT NULL AND status = 'ACTIVE';
CREATE INDEX IX_health_check_records_patient ON public.health_check_records (patient_id, created_at desc);
CREATE INDEX IX_hcbes_employee ON public.health_check_batch_employee_services (health_check_batch_employee_id, billable);
CREATE INDEX IX_hcbes_service ON public.health_check_batch_employee_services (health_check_batch_service_id, billable);
CREATE UNIQUE INDEX UX_hcbes_service_request ON public.health_check_batch_employee_services (service_request_id);
CREATE INDEX IX_health_check_import_jobs_batch ON public.health_check_import_jobs (health_check_batch_id, import_type, created_at desc);
CREATE INDEX IX_health_check_import_rows_job ON public.health_check_import_rows (health_check_import_job_id, validation_status);
CREATE INDEX IX_health_check_import_rows_request ON public.health_check_import_rows (resolved_service_request_id);
CREATE INDEX IX_notifications_status ON public.notifications (status, next_retry_at);
CREATE INDEX IX_notifications_patient ON public.notifications (patient_id, created_at desc);
CREATE INDEX IX_external_code_mappings_lookup ON public.external_code_mappings (integration_endpoint_id, mapping_type, external_code, valid_to);
CREATE INDEX IX_outbox_events_pending ON public.outbox_events (status, created_at);

-- Cross-column invariant explicitly defined by table-design-v2.10.
ALTER TABLE public.users ADD CONSTRAINT CK_users_principal_type
    CHECK ((principal_type = 'STAFF' AND staff_id IS NOT NULL AND patient_id IS NULL)
       OR (principal_type = 'PATIENT' AND patient_id IS NOT NULL AND staff_id IS NULL));

-- Preserve SQL Server rowversion's automatic version-token behavior.
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
CREATE TRIGGER trg_journeys_row_version
    BEFORE UPDATE ON public.journeys
    FOR EACH ROW EXECUTE FUNCTION public.bump_row_version();
CREATE TRIGGER trg_companies_row_version
    BEFORE UPDATE ON public.companies
    FOR EACH ROW EXECUTE FUNCTION public.bump_row_version();
CREATE TRIGGER trg_health_check_records_row_version
    BEFORE UPDATE ON public.health_check_records
    FOR EACH ROW EXECUTE FUNCTION public.bump_row_version();
