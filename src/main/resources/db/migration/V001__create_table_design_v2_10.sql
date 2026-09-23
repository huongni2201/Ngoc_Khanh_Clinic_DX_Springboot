-- Table design v2.10 - SQL Server 2022+
-- Source: docs/baseline/table-design-v2.10.md (baseline 2026-09-22)
-- Generated as a clean target schema; legacy MVP identifiers are intentionally absent.

CREATE TABLE dbo.patients (
    id uniqueidentifier NOT NULL CONSTRAINT PK_patients PRIMARY KEY,
    patient_code varchar(24) NOT NULL,
    identification_number varchar(20) NOT NULL,
    full_name nvarchar(200) NOT NULL,
    full_name_normalized nvarchar(200) NOT NULL,
    date_of_birth date NOT NULL,
    sex varchar(16) NOT NULL,
    phone nvarchar(30) NULL,
    email nvarchar(200) NULL,
    address nvarchar(500) NULL,
    ward_code varchar(20) NULL,
    province_code varchar(20) NULL,
    occupation nvarchar(200) NULL,
    note nvarchar(1000) NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL,
    row_version rowversion NOT NULL
);

CREATE TABLE dbo.patient_allergies (
    id uniqueidentifier NOT NULL CONSTRAINT PK_patient_allergies PRIMARY KEY,
    patient_id uniqueidentifier NOT NULL,
    substance_code varchar(50) NULL,
    substance_name nvarchar(200) NOT NULL,
    reaction nvarchar(500) NULL,
    severity varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    verification_status varchar(16) NOT NULL DEFAULT 'CONFIRMED',
    recorded_by_user_id uniqueidentifier NULL,
    recorded_at datetime2(3) NOT NULL,
    ended_at datetime2(3) NULL
);

CREATE TABLE dbo.patient_conditions (
    id uniqueidentifier NOT NULL CONSTRAINT PK_patient_conditions PRIMARY KEY,
    patient_id uniqueidentifier NOT NULL,
    diagnosis_code varchar(20) NULL,
    condition_name nvarchar(300) NOT NULL,
    clinical_status varchar(16) NOT NULL,
    onset_date date NULL,
    resolved_date date NULL,
    note nvarchar(1000) NULL,
    recorded_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.departments (
    id uniqueidentifier NOT NULL CONSTRAINT PK_departments PRIMARY KEY,
    department_code varchar(30) NOT NULL,
    department_name nvarchar(200) NOT NULL,
    department_type varchar(32) NOT NULL,
    is_active bit NOT NULL DEFAULT 1,
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.rooms (
    id uniqueidentifier NOT NULL CONSTRAINT PK_rooms PRIMARY KEY,
    department_id uniqueidentifier NOT NULL,
    room_code varchar(30) NOT NULL,
    room_name nvarchar(200) NOT NULL,
    floor nvarchar(50) NULL,
    location_note nvarchar(300) NULL,
    room_type varchar(32) NOT NULL,
    is_active bit NOT NULL DEFAULT 1
);

CREATE TABLE dbo.staff (
    id uniqueidentifier NOT NULL CONSTRAINT PK_staff PRIMARY KEY,
    staff_code varchar(30) NOT NULL,
    full_name nvarchar(200) NOT NULL,
    staff_type varchar(32) NOT NULL,
    license_number nvarchar(100) NULL,
    phone nvarchar(30) NULL,
    is_active bit NOT NULL DEFAULT 1
);

CREATE TABLE dbo.staff_department_assignments (
    id uniqueidentifier NOT NULL CONSTRAINT PK_staff_department_assignments PRIMARY KEY,
    staff_id uniqueidentifier NOT NULL,
    department_id uniqueidentifier NOT NULL,
    is_primary bit NOT NULL DEFAULT 0,
    valid_from date NOT NULL,
    valid_to date NULL
);

CREATE TABLE dbo.diagnosis_catalog (
    id uniqueidentifier NOT NULL CONSTRAINT PK_diagnosis_catalog PRIMARY KEY,
    diagnosis_code varchar(20) NOT NULL,
    name_vi nvarchar(500) NOT NULL,
    name_en nvarchar(500) NULL,
    parent_code varchar(20) NULL,
    is_active bit NOT NULL DEFAULT 1
);

CREATE TABLE dbo.services (
    id uniqueidentifier NOT NULL CONSTRAINT PK_services PRIMARY KEY,
    service_code varchar(40) NOT NULL,
    service_name nvarchar(300) NOT NULL,
    service_type varchar(32) NOT NULL,
    performing_department_id uniqueidentifier NULL,
    default_room_id uniqueidentifier NULL,
    requires_payment bit NOT NULL DEFAULT 1,
    requires_specimen bit NOT NULL DEFAULT 0,
    health_check_eligible bit NOT NULL DEFAULT 0,
    result_type varchar(24) NOT NULL DEFAULT 'NONE',
    lab_panel_id uniqueidentifier NULL,
    preparation_instructions nvarchar(1000) NULL,
    is_active bit NOT NULL DEFAULT 1,
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.service_prices (
    id uniqueidentifier NOT NULL CONSTRAINT PK_service_prices PRIMARY KEY,
    service_id uniqueidentifier NOT NULL,
    price_type varchar(32) NOT NULL,
    payer_reference nvarchar(100) NULL,
    amount decimal(18,2) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'VND',
    effective_from datetime2(3) NOT NULL,
    effective_to datetime2(3) NULL,
    is_active bit NOT NULL DEFAULT 1
);

CREATE TABLE dbo.medications (
    id uniqueidentifier NOT NULL CONSTRAINT PK_medications PRIMARY KEY,
    medication_code varchar(40) NOT NULL,
    medication_name nvarchar(300) NOT NULL,
    generic_name nvarchar(300) NULL,
    strength nvarchar(100) NULL,
    dosage_form nvarchar(100) NULL,
    default_route nvarchar(100) NULL,
    unit nvarchar(50) NULL,
    is_active bit NOT NULL DEFAULT 1
);

CREATE TABLE dbo.document_templates (
    id uniqueidentifier NOT NULL CONSTRAINT PK_document_templates PRIMARY KEY,
    template_code varchar(50) NOT NULL,
    template_name nvarchar(250) NOT NULL,
    template_type varchar(40) NOT NULL,
    barcode_policy varchar(24) NOT NULL DEFAULT 'NONE',
    is_master_health_check_form bit NOT NULL DEFAULT 0,
    is_active bit NOT NULL DEFAULT 1,
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.document_template_versions (
    id uniqueidentifier NOT NULL CONSTRAINT PK_document_template_versions PRIMARY KEY,
    document_template_id uniqueidentifier NOT NULL,
    version_number int NOT NULL,
    source_file_attachment_id uniqueidentifier NOT NULL,
    paper_size varchar(16) NOT NULL,
    custom_width_mm decimal(8,2) NULL,
    custom_height_mm decimal(8,2) NULL,
    orientation varchar(16) NOT NULL DEFAULT 'PORTRAIT',
    render_mode varchar(24) NOT NULL,
    renderer_type varchar(24) NOT NULL,
    schema_json nvarchar(max) NOT NULL,
    render_template nvarchar(max) NULL,
    effective_from datetime2(3) NOT NULL,
    retired_at datetime2(3) NULL,
    created_by_user_id uniqueidentifier NOT NULL,
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.document_template_fields (
    id uniqueidentifier NOT NULL CONSTRAINT PK_document_template_fields PRIMARY KEY,
    document_template_version_id uniqueidentifier NOT NULL,
    field_key varchar(80) NOT NULL,
    display_label nvarchar(300) NULL,
    item_type varchar(24) NOT NULL,
    selection_mode varchar(24) NULL,
    eligibility_rule_json nvarchar(max) NULL,
    display_order int NOT NULL,
    is_required bit NOT NULL DEFAULT 0
);

CREATE TABLE dbo.service_template_mappings (
    id uniqueidentifier NOT NULL CONSTRAINT PK_service_template_mappings PRIMARY KEY,
    service_id uniqueidentifier NOT NULL,
    document_template_id uniqueidentifier NOT NULL,
    display_order int NOT NULL DEFAULT 1,
    is_active bit NOT NULL DEFAULT 1,
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.generated_documents (
    id uniqueidentifier NOT NULL CONSTRAINT PK_generated_documents PRIMARY KEY,
    encounter_id uniqueidentifier NOT NULL,
    document_template_version_id uniqueidentifier NOT NULL,
    document_kind varchar(40) NOT NULL,
    print_sequence int NOT NULL DEFAULT 1,
    paper_size_snapshot varchar(16) NOT NULL,
    orientation_snapshot varchar(16) NOT NULL,
    version_number int NOT NULL DEFAULT 1,
    status varchar(24) NOT NULL,
    render_payload_hash char(64) NULL,
    file_attachment_id uniqueidentifier NULL,
    generated_by_user_id uniqueidentifier NULL,
    generated_at datetime2(3) NOT NULL,
    invalidated_at datetime2(3) NULL
);

CREATE TABLE dbo.generated_document_service_requests (
    id uniqueidentifier NOT NULL CONSTRAINT PK_generated_document_service_requests PRIMARY KEY,
    generated_document_id uniqueidentifier NOT NULL,
    service_request_id uniqueidentifier NOT NULL,
    service_code_snapshot varchar(40) NOT NULL,
    service_name_snapshot nvarchar(300) NOT NULL,
    display_order int NOT NULL DEFAULT 1,
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.appointments (
    id uniqueidentifier NOT NULL CONSTRAINT PK_appointments PRIMARY KEY,
    patient_id uniqueidentifier NOT NULL,
    source_encounter_id uniqueidentifier NULL,
    department_id uniqueidentifier NULL,
    doctor_staff_id uniqueidentifier NULL,
    scheduled_start datetime2(3) NOT NULL,
    scheduled_end datetime2(3) NULL,
    status varchar(16) NOT NULL,
    reason nvarchar(500) NULL,
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.encounters (
    id uniqueidentifier NOT NULL CONSTRAINT PK_encounters PRIMARY KEY,
    encounter_code varchar(30) NOT NULL,
    patient_id uniqueidentifier NOT NULL,
    encounter_type varchar(24) NOT NULL,
    reason nvarchar(500) NULL,
    priority varchar(16) NOT NULL DEFAULT 'ROUTINE',
    status varchar(16) NOT NULL,
    started_at datetime2(3) NULL,
    completed_at datetime2(3) NULL,
    canceled_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL,
    row_version rowversion NOT NULL,
    prepared_at datetime2(3) NULL,
    checked_in_at datetime2(3) NULL,
    checked_in_by_user_id uniqueidentifier NULL
);

CREATE TABLE dbo.encounter_assignments (
    id uniqueidentifier NOT NULL CONSTRAINT PK_encounter_assignments PRIMARY KEY,
    encounter_id uniqueidentifier NOT NULL,
    department_id uniqueidentifier NOT NULL,
    room_id uniqueidentifier NULL,
    doctor_staff_id uniqueidentifier NULL,
    assigned_at datetime2(3) NOT NULL,
    ended_at datetime2(3) NULL,
    assigned_by_user_id uniqueidentifier NULL,
    destination_label nvarchar(200) NULL
);

CREATE TABLE dbo.journeys (
    id uniqueidentifier NOT NULL CONSTRAINT PK_journeys PRIMARY KEY,
    encounter_id uniqueidentifier NOT NULL,
    current_stage varchar(40) NOT NULL,
    current_department_id uniqueidentifier NULL,
    current_room_id uniqueidentifier NULL,
    stage_entered_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL,
    row_version rowversion NOT NULL
);

CREATE TABLE dbo.journey_events (
    id uniqueidentifier NOT NULL CONSTRAINT PK_journey_events PRIMARY KEY,
    journey_id uniqueidentifier NOT NULL,
    from_stage varchar(40) NULL,
    to_stage varchar(40) NOT NULL,
    department_id uniqueidentifier NULL,
    room_id uniqueidentifier NULL,
    reason nvarchar(500) NULL,
    occurred_at datetime2(3) NOT NULL,
    actor_user_id uniqueidentifier NULL
);

CREATE TABLE dbo.vital_signs (
    id uniqueidentifier NOT NULL CONSTRAINT PK_vital_signs PRIMARY KEY,
    encounter_id uniqueidentifier NOT NULL,
    height_cm decimal(6,2) NULL,
    weight_kg decimal(6,2) NULL,
    bmi decimal(6,2) NULL,
    pulse_bpm int NULL,
    systolic_bp int NULL,
    diastolic_bp int NULL,
    physical_classification nvarchar(100) NULL,
    measured_at datetime2(3) NOT NULL,
    recorded_by_staff_id uniqueidentifier NULL
);

CREATE TABLE dbo.clinical_notes (
    id uniqueidentifier NOT NULL CONSTRAINT PK_clinical_notes PRIMARY KEY,
    encounter_id uniqueidentifier NOT NULL,
    note_type varchar(32) NOT NULL,
    content_json nvarchar(max) NOT NULL,
    author_staff_id uniqueidentifier NOT NULL,
    status varchar(16) NOT NULL,
    created_at datetime2(3) NOT NULL,
    finalized_at datetime2(3) NULL
);

CREATE TABLE dbo.encounter_diagnoses (
    id uniqueidentifier NOT NULL CONSTRAINT PK_encounter_diagnoses PRIMARY KEY,
    encounter_id uniqueidentifier NOT NULL,
    diagnosis_catalog_id uniqueidentifier NULL,
    diagnosis_text nvarchar(500) NOT NULL,
    diagnosis_type varchar(24) NOT NULL,
    is_primary bit NOT NULL DEFAULT 0,
    recorded_by_staff_id uniqueidentifier NOT NULL,
    recorded_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.order_rounds (
    id uniqueidentifier NOT NULL CONSTRAINT PK_order_rounds PRIMARY KEY,
    encounter_id uniqueidentifier NOT NULL,
    health_check_record_id uniqueidentifier NULL,
    round_number int NOT NULL,
    source_type varchar(24) NOT NULL,
    status varchar(16) NOT NULL,
    ordered_by_staff_id uniqueidentifier NOT NULL,
    created_by_user_id uniqueidentifier NOT NULL,
    ordered_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.service_requests (
    id uniqueidentifier NOT NULL CONSTRAINT PK_service_requests PRIMARY KEY,
    order_round_id uniqueidentifier NOT NULL,
    service_id uniqueidentifier NOT NULL,
    status varchar(16) NOT NULL,
    priority varchar(16) NOT NULL DEFAULT 'ROUTINE',
    performing_department_id uniqueidentifier NULL,
    performing_room_id uniqueidentifier NULL,
    performing_location_label nvarchar(200) NULL,
    service_name_snapshot nvarchar(300) NOT NULL,
    unit_price_snapshot decimal(18,2) NULL,
    preparation_instructions_snapshot nvarchar(1000) NULL,
    ordered_at datetime2(3) NOT NULL,
    started_at datetime2(3) NULL,
    completed_at datetime2(3) NULL
);

CREATE TABLE dbo.payment_authorizations (
    id uniqueidentifier NOT NULL CONSTRAINT PK_payment_authorizations PRIMARY KEY,
    service_request_id uniqueidentifier NOT NULL,
    status varchar(20) NOT NULL,
    invoice_item_id uniqueidentifier NULL,
    authorized_at datetime2(3) NULL,
    authorized_by_user_id uniqueidentifier NULL,
    reason nvarchar(500) NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.invoices (
    id uniqueidentifier NOT NULL CONSTRAINT PK_invoices PRIMARY KEY,
    invoice_number varchar(40) NOT NULL,
    encounter_id uniqueidentifier NOT NULL,
    patient_id uniqueidentifier NOT NULL,
    invoice_type varchar(24) NOT NULL,
    status varchar(20) NOT NULL,
    subtotal decimal(18,2) NOT NULL,
    discount_amount decimal(18,2) NOT NULL DEFAULT 0,
    total_amount decimal(18,2) NOT NULL,
    paid_amount decimal(18,2) NOT NULL DEFAULT 0,
    issued_at datetime2(3) NOT NULL,
    created_by_user_id uniqueidentifier NOT NULL
);

CREATE TABLE dbo.invoice_items (
    id uniqueidentifier NOT NULL CONSTRAINT PK_invoice_items PRIMARY KEY,
    invoice_id uniqueidentifier NOT NULL,
    service_request_id uniqueidentifier NULL,
    service_id uniqueidentifier NULL,
    description_snapshot nvarchar(300) NOT NULL,
    quantity decimal(12,3) NOT NULL DEFAULT 1,
    unit_price decimal(18,2) NOT NULL,
    discount_amount decimal(18,2) NOT NULL DEFAULT 0,
    line_total decimal(18,2) NOT NULL
);

CREATE TABLE dbo.invoice_adjustments (
    id uniqueidentifier NOT NULL CONSTRAINT PK_invoice_adjustments PRIMARY KEY,
    invoice_id uniqueidentifier NOT NULL,
    adjustment_type varchar(24) NOT NULL,
    amount decimal(18,2) NOT NULL,
    reason nvarchar(500) NOT NULL,
    created_by_user_id uniqueidentifier NOT NULL,
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.payments (
    id uniqueidentifier NOT NULL CONSTRAINT PK_payments PRIMARY KEY,
    invoice_id uniqueidentifier NOT NULL,
    payment_method varchar(24) NOT NULL,
    amount decimal(18,2) NOT NULL,
    status varchar(24) NOT NULL,
    gateway_transaction_id nvarchar(150) NULL,
    confirmed_by_user_id uniqueidentifier NULL,
    confirmed_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.companies (
    id uniqueidentifier NOT NULL CONSTRAINT PK_companies PRIMARY KEY,
    company_code varchar(40) NOT NULL,
    company_name nvarchar(300) NOT NULL,
    tax_code varchar(40) NULL,
    address nvarchar(500) NULL,
    contact_name nvarchar(200) NOT NULL,
    contact_phone nvarchar(30) NOT NULL,
    contact_job_title nvarchar(150) NULL,
    note nvarchar(1000) NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL,
    row_version rowversion NOT NULL
);

CREATE TABLE dbo.company_employees (
    id uniqueidentifier NOT NULL CONSTRAINT PK_company_employees PRIMARY KEY,
    company_id uniqueidentifier NOT NULL,
    patient_id uniqueidentifier NULL,
    employee_code nvarchar(60) NOT NULL,
    identification_number varchar(20) NOT NULL,
    full_name nvarchar(200) NOT NULL,
    date_of_birth date NOT NULL,
    sex varchar(16) NOT NULL,
    department_name nvarchar(200) NULL,
    job_title nvarchar(200) NULL,
    occupation nvarchar(200) NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.health_check_batches (
    id uniqueidentifier NOT NULL CONSTRAINT PK_health_check_batches PRIMARY KEY,
    company_id uniqueidentifier NOT NULL,
    batch_code varchar(40) NOT NULL,
    batch_name nvarchar(250) NOT NULL,
    start_date date NULL,
    end_date date NULL,
    reason nvarchar(300) NULL,
    payer_type varchar(24) NULL,
    examination_site_type varchar(16) NOT NULL,
    examination_site_name nvarchar(250) NOT NULL,
    examination_site_address nvarchar(500) NULL,
    master_template_version_id uniqueidentifier NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    finalized_at datetime2(3) NULL,
    closed_at datetime2(3) NULL,
    created_by_user_id uniqueidentifier NOT NULL,
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.health_check_batch_services (
    id uniqueidentifier NOT NULL CONSTRAINT PK_health_check_batch_services PRIMARY KEY,
    health_check_batch_id uniqueidentifier NOT NULL,
    service_id uniqueidentifier NOT NULL,
    document_template_version_id uniqueidentifier NULL,
    service_code_snapshot varchar(40) NOT NULL,
    service_name_snapshot nvarchar(300) NOT NULL,
    base_price_snapshot decimal(18,2) NOT NULL,
    negotiated_unit_price decimal(18,2) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'VND',
    display_order int NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.health_check_batch_employees (
    id uniqueidentifier NOT NULL CONSTRAINT PK_health_check_batch_employees PRIMARY KEY,
    health_check_batch_id uniqueidentifier NOT NULL,
    company_employee_id uniqueidentifier NOT NULL,
    employee_code_snapshot nvarchar(60) NOT NULL,
    department_snapshot nvarchar(200) NULL,
    job_title_snapshot nvarchar(200) NULL,
    occupation_snapshot nvarchar(200) NULL,
    administrative_snapshot_json nvarchar(max) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'REGISTERED',
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.health_check_records (
    id uniqueidentifier NOT NULL CONSTRAINT PK_health_check_records PRIMARY KEY,
    shs_code varchar(40) NOT NULL,
    source_type varchar(16) NOT NULL,
    patient_id uniqueidentifier NOT NULL,
    health_check_batch_employee_id uniqueidentifier NULL,
    encounter_id uniqueidentifier NOT NULL,
    master_template_version_id uniqueidentifier NOT NULL,
    full_name_snapshot nvarchar(200) NOT NULL,
    date_of_birth_snapshot date NOT NULL,
    sex_snapshot varchar(16) NOT NULL,
    identification_number_snapshot varchar(20) NOT NULL,
    identification_number_issue_date_snapshot date NULL,
    identification_number_issue_place_snapshot nvarchar(200) NULL,
    ethnicity_snapshot nvarchar(100) NULL,
    subject_type_snapshot nvarchar(100) NULL,
    payer_source_snapshot nvarchar(150) NULL,
    blood_group_snapshot varchar(16) NULL,
    phone_snapshot nvarchar(30) NULL,
    province_snapshot nvarchar(150) NULL,
    ward_snapshot nvarchar(150) NULL,
    address_detail_snapshot nvarchar(500) NULL,
    occupation_snapshot nvarchar(200) NULL,
    workplace_or_school_snapshot nvarchar(300) NULL,
    health_check_reason_snapshot nvarchar(500) NULL,
    planned_examination_date date NOT NULL,
    actual_examination_date date NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    replaces_health_check_record_id uniqueidentifier NULL,
    created_at datetime2(3) NOT NULL,
    completed_at datetime2(3) NULL,
    canceled_at datetime2(3) NULL,
    row_version rowversion NOT NULL
);

CREATE TABLE dbo.health_check_batch_employee_services (
    id uniqueidentifier NOT NULL CONSTRAINT PK_health_check_batch_employee_services PRIMARY KEY,
    health_check_batch_employee_id uniqueidentifier NOT NULL,
    health_check_batch_service_id uniqueidentifier NOT NULL,
    service_request_id uniqueidentifier NOT NULL,
    billable bit NOT NULL DEFAULT 0,
    unit_price_snapshot decimal(18,2) NOT NULL,
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.health_check_import_jobs (
    id uniqueidentifier NOT NULL CONSTRAINT PK_health_check_import_jobs PRIMARY KEY,
    health_check_batch_id uniqueidentifier NOT NULL,
    import_type varchar(20) NOT NULL,
    source_file_attachment_id uniqueidentifier NOT NULL,
    status varchar(20) NOT NULL,
    column_mapping_json nvarchar(max) NULL,
    total_rows int NOT NULL DEFAULT 0,
    valid_rows int NOT NULL DEFAULT 0,
    warning_rows int NOT NULL DEFAULT 0,
    error_rows int NOT NULL DEFAULT 0,
    created_by_user_id uniqueidentifier NOT NULL,
    confirmed_by_user_id uniqueidentifier NULL,
    created_at datetime2(3) NOT NULL,
    confirmed_at datetime2(3) NULL
);

CREATE TABLE dbo.health_check_import_rows (
    id uniqueidentifier NOT NULL CONSTRAINT PK_health_check_import_rows PRIMARY KEY,
    health_check_import_job_id uniqueidentifier NOT NULL,
    row_number int NOT NULL,
    employee_code_snapshot nvarchar(60) NULL,
    identification_number_snapshot varchar(20) NULL,
    service_code_snapshot varchar(40) NULL,
    validation_status varchar(16) NOT NULL,
    error_codes_json nvarchar(max) NULL,
    normalized_payload_json nvarchar(max) NOT NULL,
    resolved_patient_id uniqueidentifier NULL,
    resolved_company_employee_id uniqueidentifier NULL,
    resolved_batch_employee_id uniqueidentifier NULL,
    resolved_batch_service_id uniqueidentifier NULL,
    resolved_service_request_id uniqueidentifier NULL
);

CREATE TABLE dbo.specimens (
    id uniqueidentifier NOT NULL CONSTRAINT PK_specimens PRIMARY KEY,
    specimen_code varchar(50) NOT NULL,
    patient_id uniqueidentifier NOT NULL,
    encounter_id uniqueidentifier NULL,
    specimen_type varchar(40) NOT NULL,
    status varchar(20) NOT NULL,
    collected_at datetime2(3) NULL,
    collected_by_staff_id uniqueidentifier NULL,
    received_at datetime2(3) NULL
);

CREATE TABLE dbo.specimen_service_requests (
    id uniqueidentifier NOT NULL CONSTRAINT PK_specimen_service_requests PRIMARY KEY,
    specimen_id uniqueidentifier NOT NULL,
    service_request_id uniqueidentifier NOT NULL
);

CREATE TABLE dbo.lab_panels (
    id uniqueidentifier NOT NULL CONSTRAINT PK_lab_panels PRIMARY KEY,
    panel_code varchar(40) NOT NULL,
    panel_name nvarchar(250) NOT NULL,
    is_active bit NOT NULL DEFAULT 1
);

CREATE TABLE dbo.analytes (
    id uniqueidentifier NOT NULL CONSTRAINT PK_analytes PRIMARY KEY,
    analyte_code varchar(40) NOT NULL,
    analyte_name nvarchar(250) NOT NULL,
    default_unit nvarchar(40) NULL,
    value_type varchar(20) NOT NULL,
    is_active bit NOT NULL DEFAULT 1
);

CREATE TABLE dbo.lab_panel_items (
    id uniqueidentifier NOT NULL CONSTRAINT PK_lab_panel_items PRIMARY KEY,
    lab_panel_id uniqueidentifier NOT NULL,
    analyte_id uniqueidentifier NOT NULL,
    display_order int NOT NULL
);

CREATE TABLE dbo.analyte_reference_ranges (
    id uniqueidentifier NOT NULL CONSTRAINT PK_analyte_reference_ranges PRIMARY KEY,
    analyte_id uniqueidentifier NOT NULL,
    sex varchar(16) NULL,
    age_min_days int NULL,
    age_max_days int NULL,
    lower_bound decimal(18,6) NULL,
    upper_bound decimal(18,6) NULL,
    text_range nvarchar(200) NULL,
    warning_lower decimal(18,6) NULL,
    warning_upper decimal(18,6) NULL,
    effective_from datetime2(3) NOT NULL,
    effective_to datetime2(3) NULL
);

CREATE TABLE dbo.lab_results (
    id uniqueidentifier NOT NULL CONSTRAINT PK_lab_results PRIMARY KEY,
    service_request_id uniqueidentifier NOT NULL,
    specimen_id uniqueidentifier NULL,
    version_number int NOT NULL,
    status varchar(16) NOT NULL,
    supersedes_lab_result_id uniqueidentifier NULL,
    result_source varchar(24) NOT NULL,
    verified_by_staff_id uniqueidentifier NULL,
    verified_at datetime2(3) NULL,
    finalized_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL,
    raw_message_reference nvarchar(200) NULL
);

CREATE TABLE dbo.lab_result_values (
    id uniqueidentifier NOT NULL CONSTRAINT PK_lab_result_values PRIMARY KEY,
    lab_result_id uniqueidentifier NOT NULL,
    analyte_id uniqueidentifier NOT NULL,
    numeric_value decimal(18,6) NULL,
    text_value nvarchar(500) NULL,
    unit_snapshot nvarchar(40) NULL,
    reference_range_snapshot nvarchar(200) NULL,
    abnormal_flag varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    instrument_code nvarchar(50) NULL,
    measured_at datetime2(3) NULL
);

CREATE TABLE dbo.imaging_studies (
    id uniqueidentifier NOT NULL CONSTRAINT PK_imaging_studies PRIMARY KEY,
    service_request_id uniqueidentifier NOT NULL,
    modality varchar(16) NOT NULL,
    external_study_uid nvarchar(200) NULL,
    device_identifier nvarchar(100) NULL,
    study_at datetime2(3) NULL,
    metadata_json nvarchar(max) NULL,
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.diagnostic_reports (
    id uniqueidentifier NOT NULL CONSTRAINT PK_diagnostic_reports PRIMARY KEY,
    service_request_id uniqueidentifier NOT NULL,
    imaging_study_id uniqueidentifier NULL,
    document_template_version_id uniqueidentifier NULL,
    version_number int NOT NULL,
    status varchar(16) NOT NULL,
    findings nvarchar(max) NULL,
    conclusion nvarchar(max) NULL,
    structured_data_json nvarchar(max) NULL,
    supersedes_report_id uniqueidentifier NULL,
    author_staff_id uniqueidentifier NOT NULL,
    verified_by_staff_id uniqueidentifier NULL,
    finalized_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.file_attachments (
    id uniqueidentifier NOT NULL CONSTRAINT PK_file_attachments PRIMARY KEY,
    patient_id uniqueidentifier NULL,
    encounter_id uniqueidentifier NULL,
    entity_type varchar(40) NOT NULL,
    entity_id uniqueidentifier NOT NULL,
    document_type varchar(40) NOT NULL,
    storage_provider varchar(24) NOT NULL,
    storage_key nvarchar(500) NOT NULL,
    file_name nvarchar(255) NOT NULL,
    mime_type varchar(100) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 char(64) NULL,
    created_by_user_id uniqueidentifier NULL,
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.prescriptions (
    id uniqueidentifier NOT NULL CONSTRAINT PK_prescriptions PRIMARY KEY,
    prescription_number varchar(40) NOT NULL,
    encounter_id uniqueidentifier NOT NULL,
    patient_id uniqueidentifier NOT NULL,
    prescriber_staff_id uniqueidentifier NOT NULL,
    version_number int NOT NULL,
    status varchar(16) NOT NULL,
    supersedes_prescription_id uniqueidentifier NULL,
    issued_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.prescription_items (
    id uniqueidentifier NOT NULL CONSTRAINT PK_prescription_items PRIMARY KEY,
    prescription_id uniqueidentifier NOT NULL,
    medication_id uniqueidentifier NULL,
    medication_name_snapshot nvarchar(300) NOT NULL,
    strength_snapshot nvarchar(100) NULL,
    dose nvarchar(100) NOT NULL,
    route nvarchar(100) NULL,
    frequency nvarchar(100) NOT NULL,
    duration_days int NULL,
    quantity decimal(12,3) NULL,
    instructions nvarchar(500) NULL,
    display_order int NOT NULL
);

CREATE TABLE dbo.users (
    id uniqueidentifier NOT NULL CONSTRAINT PK_users PRIMARY KEY,
    principal_type varchar(16) NOT NULL,
    staff_id uniqueidentifier NULL,
    patient_id uniqueidentifier NULL,
    auth_provider varchar(32) NOT NULL,
    auth_subject nvarchar(200) NOT NULL,
    status varchar(16) NOT NULL,
    last_login_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.roles (
    id uniqueidentifier NOT NULL CONSTRAINT PK_roles PRIMARY KEY,
    role_code varchar(40) NOT NULL,
    role_name nvarchar(150) NOT NULL,
    is_active bit NOT NULL DEFAULT 1
);

CREATE TABLE dbo.permissions (
    id uniqueidentifier NOT NULL CONSTRAINT PK_permissions PRIMARY KEY,
    permission_code varchar(80) NOT NULL,
    module varchar(40) NOT NULL,
    description nvarchar(300) NULL
);

CREATE TABLE dbo.user_roles (
    id uniqueidentifier NOT NULL CONSTRAINT PK_user_roles PRIMARY KEY,
    user_id uniqueidentifier NOT NULL,
    role_id uniqueidentifier NOT NULL,
    department_id uniqueidentifier NULL,
    room_id uniqueidentifier NULL,
    valid_from datetime2(3) NOT NULL,
    valid_to datetime2(3) NULL
);

CREATE TABLE dbo.role_permissions (
    id uniqueidentifier NOT NULL CONSTRAINT PK_role_permissions PRIMARY KEY,
    role_id uniqueidentifier NOT NULL,
    permission_id uniqueidentifier NOT NULL
);

CREATE TABLE dbo.notifications (
    id uniqueidentifier NOT NULL CONSTRAINT PK_notifications PRIMARY KEY,
    patient_id uniqueidentifier NULL,
    appointment_id uniqueidentifier NULL,
    encounter_id uniqueidentifier NULL,
    notification_type varchar(40) NOT NULL,
    recipient_phone nvarchar(30) NOT NULL,
    sms_template_code varchar(60) NOT NULL,
    payload_json nvarchar(max) NOT NULL,
    provider_code varchar(40) NULL,
    provider_message_id nvarchar(150) NULL,
    idempotency_key varchar(120) NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count int NOT NULL DEFAULT 0,
    scheduled_at datetime2(3) NULL,
    sent_at datetime2(3) NULL,
    delivered_at datetime2(3) NULL,
    failed_at datetime2(3) NULL,
    next_retry_at datetime2(3) NULL,
    error_code nvarchar(100) NULL,
    error_message nvarchar(1000) NULL,
    created_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.notification_attempts (
    id uniqueidentifier NOT NULL CONSTRAINT PK_notification_attempts PRIMARY KEY,
    notification_id uniqueidentifier NOT NULL,
    attempt_number int NOT NULL,
    provider_code varchar(40) NOT NULL,
    provider_message_id nvarchar(150) NULL,
    status varchar(20) NOT NULL,
    request_reference nvarchar(500) NULL,
    response_reference nvarchar(500) NULL,
    error_code nvarchar(100) NULL,
    error_message nvarchar(1000) NULL,
    attempted_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.audit_logs (
    id uniqueidentifier NOT NULL CONSTRAINT PK_audit_logs PRIMARY KEY,
    occurred_at datetime2(3) NOT NULL,
    actor_user_id uniqueidentifier NULL,
    action varchar(80) NOT NULL,
    entity_type varchar(50) NOT NULL,
    entity_id nvarchar(100) NOT NULL,
    patient_id uniqueidentifier NULL,
    encounter_id uniqueidentifier NULL,
    health_check_record_id uniqueidentifier NULL,
    correlation_id uniqueidentifier NULL,
    reason nvarchar(500) NULL,
    before_json nvarchar(max) NULL,
    after_json nvarchar(max) NULL,
    ip_address varchar(64) NULL,
    user_agent nvarchar(500) NULL
);

CREATE TABLE dbo.integration_endpoints (
    id uniqueidentifier NOT NULL CONSTRAINT PK_integration_endpoints PRIMARY KEY,
    endpoint_code varchar(40) NOT NULL,
    integration_type varchar(32) NOT NULL,
    endpoint_name nvarchar(200) NOT NULL,
    base_url nvarchar(500) NULL,
    credential_reference nvarchar(300) NULL,
    config_json nvarchar(max) NULL,
    is_active bit NOT NULL DEFAULT 1,
    created_at datetime2(3) NOT NULL,
    updated_at datetime2(3) NOT NULL
);

CREATE TABLE dbo.external_code_mappings (
    id uniqueidentifier NOT NULL CONSTRAINT PK_external_code_mappings PRIMARY KEY,
    integration_endpoint_id uniqueidentifier NOT NULL,
    mapping_type varchar(32) NOT NULL,
    external_code nvarchar(100) NOT NULL,
    internal_entity_type varchar(32) NOT NULL,
    internal_entity_id uniqueidentifier NOT NULL,
    valid_from datetime2(3) NOT NULL,
    valid_to datetime2(3) NULL
);

CREATE TABLE dbo.integration_messages (
    id uniqueidentifier NOT NULL CONSTRAINT PK_integration_messages PRIMARY KEY,
    integration_endpoint_id uniqueidentifier NOT NULL,
    direction varchar(8) NOT NULL,
    message_type varchar(40) NOT NULL,
    external_message_id nvarchar(150) NULL,
    correlation_id uniqueidentifier NOT NULL,
    status varchar(16) NOT NULL,
    payload_reference nvarchar(500) NULL,
    payload_hash char(64) NULL,
    error_message nvarchar(2000) NULL,
    received_at datetime2(3) NULL,
    processed_at datetime2(3) NULL,
    retry_count int NOT NULL DEFAULT 0
);

CREATE TABLE dbo.idempotency_keys (
    id uniqueidentifier NOT NULL CONSTRAINT PK_idempotency_keys PRIMARY KEY,
    scope varchar(50) NOT NULL,
    idempotency_key varchar(120) NOT NULL,
    request_hash char(64) NULL,
    result_entity_type varchar(50) NULL,
    result_entity_id nvarchar(100) NULL,
    status varchar(16) NOT NULL,
    created_at datetime2(3) NOT NULL,
    expires_at datetime2(3) NULL
);

CREATE TABLE dbo.outbox_events (
    id uniqueidentifier NOT NULL CONSTRAINT PK_outbox_events PRIMARY KEY,
    aggregate_type varchar(50) NOT NULL,
    aggregate_id nvarchar(100) NOT NULL,
    event_type varchar(80) NOT NULL,
    payload_json nvarchar(max) NOT NULL,
    correlation_id uniqueidentifier NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    created_at datetime2(3) NOT NULL,
    published_at datetime2(3) NULL,
    retry_count int NOT NULL DEFAULT 0
);

-- Foreign keys are added after all tables so forward/self references are valid.
ALTER TABLE dbo.patient_allergies ADD CONSTRAINT FK_patient_allergies_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.patient_allergies ADD CONSTRAINT FK_patient_allergies_recorded_by_user_id FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.patient_conditions ADD CONSTRAINT FK_patient_conditions_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.rooms ADD CONSTRAINT FK_rooms_department_id FOREIGN KEY (department_id) REFERENCES dbo.departments(id);
ALTER TABLE dbo.staff_department_assignments ADD CONSTRAINT FK_staff_department_assignments_staff_id FOREIGN KEY (staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.staff_department_assignments ADD CONSTRAINT FK_staff_department_assignments_department_id FOREIGN KEY (department_id) REFERENCES dbo.departments(id);
ALTER TABLE dbo.services ADD CONSTRAINT FK_services_performing_department_id FOREIGN KEY (performing_department_id) REFERENCES dbo.departments(id);
ALTER TABLE dbo.services ADD CONSTRAINT FK_services_default_room_id FOREIGN KEY (default_room_id) REFERENCES dbo.rooms(id);
ALTER TABLE dbo.services ADD CONSTRAINT FK_services_lab_panel_id FOREIGN KEY (lab_panel_id) REFERENCES dbo.lab_panels(id);
ALTER TABLE dbo.service_prices ADD CONSTRAINT FK_service_prices_service_id FOREIGN KEY (service_id) REFERENCES dbo.services(id);
ALTER TABLE dbo.document_template_versions ADD CONSTRAINT FK_document_template_versions_document_template_id FOREIGN KEY (document_template_id) REFERENCES dbo.document_templates(id);
ALTER TABLE dbo.document_template_versions ADD CONSTRAINT FK_document_template_versions_source_file_attachment_id FOREIGN KEY (source_file_attachment_id) REFERENCES dbo.file_attachments(id);
ALTER TABLE dbo.document_template_versions ADD CONSTRAINT FK_document_template_versions_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.document_template_fields ADD CONSTRAINT FK_document_template_fields_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES dbo.document_template_versions(id);
ALTER TABLE dbo.service_template_mappings ADD CONSTRAINT FK_service_template_mappings_service_id FOREIGN KEY (service_id) REFERENCES dbo.services(id);
ALTER TABLE dbo.service_template_mappings ADD CONSTRAINT FK_service_template_mappings_document_template_id FOREIGN KEY (document_template_id) REFERENCES dbo.document_templates(id);
ALTER TABLE dbo.generated_documents ADD CONSTRAINT FK_generated_documents_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.generated_documents ADD CONSTRAINT FK_generated_documents_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES dbo.document_template_versions(id);
ALTER TABLE dbo.generated_documents ADD CONSTRAINT FK_generated_documents_file_attachment_id FOREIGN KEY (file_attachment_id) REFERENCES dbo.file_attachments(id);
ALTER TABLE dbo.generated_documents ADD CONSTRAINT FK_generated_documents_generated_by_user_id FOREIGN KEY (generated_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.generated_document_service_requests ADD CONSTRAINT FK_generated_document_service_requests_generated_document_id FOREIGN KEY (generated_document_id) REFERENCES dbo.generated_documents(id);
ALTER TABLE dbo.generated_document_service_requests ADD CONSTRAINT FK_generated_document_service_requests_service_request_id FOREIGN KEY (service_request_id) REFERENCES dbo.service_requests(id);
ALTER TABLE dbo.appointments ADD CONSTRAINT FK_appointments_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.appointments ADD CONSTRAINT FK_appointments_source_encounter_id FOREIGN KEY (source_encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.appointments ADD CONSTRAINT FK_appointments_department_id FOREIGN KEY (department_id) REFERENCES dbo.departments(id);
ALTER TABLE dbo.appointments ADD CONSTRAINT FK_appointments_doctor_staff_id FOREIGN KEY (doctor_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.encounters ADD CONSTRAINT FK_encounters_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.encounters ADD CONSTRAINT FK_encounters_checked_in_by_user_id FOREIGN KEY (checked_in_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.encounter_assignments ADD CONSTRAINT FK_encounter_assignments_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.encounter_assignments ADD CONSTRAINT FK_encounter_assignments_department_id FOREIGN KEY (department_id) REFERENCES dbo.departments(id);
ALTER TABLE dbo.encounter_assignments ADD CONSTRAINT FK_encounter_assignments_room_id FOREIGN KEY (room_id) REFERENCES dbo.rooms(id);
ALTER TABLE dbo.encounter_assignments ADD CONSTRAINT FK_encounter_assignments_doctor_staff_id FOREIGN KEY (doctor_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.encounter_assignments ADD CONSTRAINT FK_encounter_assignments_assigned_by_user_id FOREIGN KEY (assigned_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.journeys ADD CONSTRAINT FK_journeys_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.journeys ADD CONSTRAINT FK_journeys_current_department_id FOREIGN KEY (current_department_id) REFERENCES dbo.departments(id);
ALTER TABLE dbo.journeys ADD CONSTRAINT FK_journeys_current_room_id FOREIGN KEY (current_room_id) REFERENCES dbo.rooms(id);
ALTER TABLE dbo.journey_events ADD CONSTRAINT FK_journey_events_journey_id FOREIGN KEY (journey_id) REFERENCES dbo.journeys(id);
ALTER TABLE dbo.journey_events ADD CONSTRAINT FK_journey_events_department_id FOREIGN KEY (department_id) REFERENCES dbo.departments(id);
ALTER TABLE dbo.journey_events ADD CONSTRAINT FK_journey_events_room_id FOREIGN KEY (room_id) REFERENCES dbo.rooms(id);
ALTER TABLE dbo.journey_events ADD CONSTRAINT FK_journey_events_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.vital_signs ADD CONSTRAINT FK_vital_signs_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.vital_signs ADD CONSTRAINT FK_vital_signs_recorded_by_staff_id FOREIGN KEY (recorded_by_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.clinical_notes ADD CONSTRAINT FK_clinical_notes_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.clinical_notes ADD CONSTRAINT FK_clinical_notes_author_staff_id FOREIGN KEY (author_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.encounter_diagnoses ADD CONSTRAINT FK_encounter_diagnoses_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.encounter_diagnoses ADD CONSTRAINT FK_encounter_diagnoses_diagnosis_catalog_id FOREIGN KEY (diagnosis_catalog_id) REFERENCES dbo.diagnosis_catalog(id);
ALTER TABLE dbo.encounter_diagnoses ADD CONSTRAINT FK_encounter_diagnoses_recorded_by_staff_id FOREIGN KEY (recorded_by_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.order_rounds ADD CONSTRAINT FK_order_rounds_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.order_rounds ADD CONSTRAINT FK_order_rounds_health_check_record_id FOREIGN KEY (health_check_record_id) REFERENCES dbo.health_check_records(id);
ALTER TABLE dbo.order_rounds ADD CONSTRAINT FK_order_rounds_ordered_by_staff_id FOREIGN KEY (ordered_by_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.order_rounds ADD CONSTRAINT FK_order_rounds_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.service_requests ADD CONSTRAINT FK_service_requests_order_round_id FOREIGN KEY (order_round_id) REFERENCES dbo.order_rounds(id);
ALTER TABLE dbo.service_requests ADD CONSTRAINT FK_service_requests_service_id FOREIGN KEY (service_id) REFERENCES dbo.services(id);
ALTER TABLE dbo.service_requests ADD CONSTRAINT FK_service_requests_performing_department_id FOREIGN KEY (performing_department_id) REFERENCES dbo.departments(id);
ALTER TABLE dbo.service_requests ADD CONSTRAINT FK_service_requests_performing_room_id FOREIGN KEY (performing_room_id) REFERENCES dbo.rooms(id);
ALTER TABLE dbo.payment_authorizations ADD CONSTRAINT FK_payment_authorizations_service_request_id FOREIGN KEY (service_request_id) REFERENCES dbo.service_requests(id);
ALTER TABLE dbo.payment_authorizations ADD CONSTRAINT FK_payment_authorizations_invoice_item_id FOREIGN KEY (invoice_item_id) REFERENCES dbo.invoice_items(id);
ALTER TABLE dbo.payment_authorizations ADD CONSTRAINT FK_payment_authorizations_authorized_by_user_id FOREIGN KEY (authorized_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.invoices ADD CONSTRAINT FK_invoices_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.invoices ADD CONSTRAINT FK_invoices_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.invoices ADD CONSTRAINT FK_invoices_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.invoice_items ADD CONSTRAINT FK_invoice_items_invoice_id FOREIGN KEY (invoice_id) REFERENCES dbo.invoices(id);
ALTER TABLE dbo.invoice_items ADD CONSTRAINT FK_invoice_items_service_request_id FOREIGN KEY (service_request_id) REFERENCES dbo.service_requests(id);
ALTER TABLE dbo.invoice_items ADD CONSTRAINT FK_invoice_items_service_id FOREIGN KEY (service_id) REFERENCES dbo.services(id);
ALTER TABLE dbo.invoice_adjustments ADD CONSTRAINT FK_invoice_adjustments_invoice_id FOREIGN KEY (invoice_id) REFERENCES dbo.invoices(id);
ALTER TABLE dbo.invoice_adjustments ADD CONSTRAINT FK_invoice_adjustments_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.payments ADD CONSTRAINT FK_payments_invoice_id FOREIGN KEY (invoice_id) REFERENCES dbo.invoices(id);
ALTER TABLE dbo.payments ADD CONSTRAINT FK_payments_confirmed_by_user_id FOREIGN KEY (confirmed_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.company_employees ADD CONSTRAINT FK_company_employees_company_id FOREIGN KEY (company_id) REFERENCES dbo.companies(id);
ALTER TABLE dbo.company_employees ADD CONSTRAINT FK_company_employees_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.health_check_batches ADD CONSTRAINT FK_health_check_batches_company_id FOREIGN KEY (company_id) REFERENCES dbo.companies(id);
ALTER TABLE dbo.health_check_batches ADD CONSTRAINT FK_health_check_batches_master_template_version_id FOREIGN KEY (master_template_version_id) REFERENCES dbo.document_template_versions(id);
ALTER TABLE dbo.health_check_batches ADD CONSTRAINT FK_health_check_batches_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.health_check_batch_services ADD CONSTRAINT FK_health_check_batch_services_health_check_batch_id FOREIGN KEY (health_check_batch_id) REFERENCES dbo.health_check_batches(id);
ALTER TABLE dbo.health_check_batch_services ADD CONSTRAINT FK_health_check_batch_services_service_id FOREIGN KEY (service_id) REFERENCES dbo.services(id);
ALTER TABLE dbo.health_check_batch_services ADD CONSTRAINT FK_health_check_batch_services_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES dbo.document_template_versions(id);
ALTER TABLE dbo.health_check_batch_employees ADD CONSTRAINT FK_health_check_batch_employees_health_check_batch_id FOREIGN KEY (health_check_batch_id) REFERENCES dbo.health_check_batches(id);
ALTER TABLE dbo.health_check_batch_employees ADD CONSTRAINT FK_health_check_batch_employees_company_employee_id FOREIGN KEY (company_employee_id) REFERENCES dbo.company_employees(id);
ALTER TABLE dbo.health_check_records ADD CONSTRAINT FK_health_check_records_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.health_check_records ADD CONSTRAINT FK_health_check_records_health_check_batch_employee_id FOREIGN KEY (health_check_batch_employee_id) REFERENCES dbo.health_check_batch_employees(id);
ALTER TABLE dbo.health_check_records ADD CONSTRAINT FK_health_check_records_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.health_check_records ADD CONSTRAINT FK_health_check_records_master_template_version_id FOREIGN KEY (master_template_version_id) REFERENCES dbo.document_template_versions(id);
ALTER TABLE dbo.health_check_records ADD CONSTRAINT FK_health_check_records_replaces_health_check_record_id FOREIGN KEY (replaces_health_check_record_id) REFERENCES dbo.health_check_records(id);
ALTER TABLE dbo.health_check_batch_employee_services ADD CONSTRAINT FK_health_check_batch_employee_services_health_check_batch_employee_id FOREIGN KEY (health_check_batch_employee_id) REFERENCES dbo.health_check_batch_employees(id);
ALTER TABLE dbo.health_check_batch_employee_services ADD CONSTRAINT FK_health_check_batch_employee_services_health_check_batch_service_id FOREIGN KEY (health_check_batch_service_id) REFERENCES dbo.health_check_batch_services(id);
ALTER TABLE dbo.health_check_batch_employee_services ADD CONSTRAINT FK_health_check_batch_employee_services_service_request_id FOREIGN KEY (service_request_id) REFERENCES dbo.service_requests(id);
ALTER TABLE dbo.health_check_import_jobs ADD CONSTRAINT FK_health_check_import_jobs_health_check_batch_id FOREIGN KEY (health_check_batch_id) REFERENCES dbo.health_check_batches(id);
ALTER TABLE dbo.health_check_import_jobs ADD CONSTRAINT FK_health_check_import_jobs_source_file_attachment_id FOREIGN KEY (source_file_attachment_id) REFERENCES dbo.file_attachments(id);
ALTER TABLE dbo.health_check_import_jobs ADD CONSTRAINT FK_health_check_import_jobs_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.health_check_import_jobs ADD CONSTRAINT FK_health_check_import_jobs_confirmed_by_user_id FOREIGN KEY (confirmed_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_health_check_import_job_id FOREIGN KEY (health_check_import_job_id) REFERENCES dbo.health_check_import_jobs(id);
ALTER TABLE dbo.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_resolved_patient_id FOREIGN KEY (resolved_patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_resolved_company_employee_id FOREIGN KEY (resolved_company_employee_id) REFERENCES dbo.company_employees(id);
ALTER TABLE dbo.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_resolved_batch_employee_id FOREIGN KEY (resolved_batch_employee_id) REFERENCES dbo.health_check_batch_employees(id);
ALTER TABLE dbo.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_resolved_batch_service_id FOREIGN KEY (resolved_batch_service_id) REFERENCES dbo.health_check_batch_services(id);
ALTER TABLE dbo.health_check_import_rows ADD CONSTRAINT FK_health_check_import_rows_resolved_service_request_id FOREIGN KEY (resolved_service_request_id) REFERENCES dbo.service_requests(id);
ALTER TABLE dbo.specimens ADD CONSTRAINT FK_specimens_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.specimens ADD CONSTRAINT FK_specimens_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.specimens ADD CONSTRAINT FK_specimens_collected_by_staff_id FOREIGN KEY (collected_by_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.specimen_service_requests ADD CONSTRAINT FK_specimen_service_requests_specimen_id FOREIGN KEY (specimen_id) REFERENCES dbo.specimens(id);
ALTER TABLE dbo.specimen_service_requests ADD CONSTRAINT FK_specimen_service_requests_service_request_id FOREIGN KEY (service_request_id) REFERENCES dbo.service_requests(id);
ALTER TABLE dbo.lab_panel_items ADD CONSTRAINT FK_lab_panel_items_lab_panel_id FOREIGN KEY (lab_panel_id) REFERENCES dbo.lab_panels(id);
ALTER TABLE dbo.lab_panel_items ADD CONSTRAINT FK_lab_panel_items_analyte_id FOREIGN KEY (analyte_id) REFERENCES dbo.analytes(id);
ALTER TABLE dbo.analyte_reference_ranges ADD CONSTRAINT FK_analyte_reference_ranges_analyte_id FOREIGN KEY (analyte_id) REFERENCES dbo.analytes(id);
ALTER TABLE dbo.lab_results ADD CONSTRAINT FK_lab_results_service_request_id FOREIGN KEY (service_request_id) REFERENCES dbo.service_requests(id);
ALTER TABLE dbo.lab_results ADD CONSTRAINT FK_lab_results_specimen_id FOREIGN KEY (specimen_id) REFERENCES dbo.specimens(id);
ALTER TABLE dbo.lab_results ADD CONSTRAINT FK_lab_results_supersedes_lab_result_id FOREIGN KEY (supersedes_lab_result_id) REFERENCES dbo.lab_results(id);
ALTER TABLE dbo.lab_results ADD CONSTRAINT FK_lab_results_verified_by_staff_id FOREIGN KEY (verified_by_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.lab_result_values ADD CONSTRAINT FK_lab_result_values_lab_result_id FOREIGN KEY (lab_result_id) REFERENCES dbo.lab_results(id);
ALTER TABLE dbo.lab_result_values ADD CONSTRAINT FK_lab_result_values_analyte_id FOREIGN KEY (analyte_id) REFERENCES dbo.analytes(id);
ALTER TABLE dbo.imaging_studies ADD CONSTRAINT FK_imaging_studies_service_request_id FOREIGN KEY (service_request_id) REFERENCES dbo.service_requests(id);
ALTER TABLE dbo.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_service_request_id FOREIGN KEY (service_request_id) REFERENCES dbo.service_requests(id);
ALTER TABLE dbo.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_imaging_study_id FOREIGN KEY (imaging_study_id) REFERENCES dbo.imaging_studies(id);
ALTER TABLE dbo.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_document_template_version_id FOREIGN KEY (document_template_version_id) REFERENCES dbo.document_template_versions(id);
ALTER TABLE dbo.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_supersedes_report_id FOREIGN KEY (supersedes_report_id) REFERENCES dbo.diagnostic_reports(id);
ALTER TABLE dbo.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_author_staff_id FOREIGN KEY (author_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.diagnostic_reports ADD CONSTRAINT FK_diagnostic_reports_verified_by_staff_id FOREIGN KEY (verified_by_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.file_attachments ADD CONSTRAINT FK_file_attachments_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.file_attachments ADD CONSTRAINT FK_file_attachments_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.file_attachments ADD CONSTRAINT FK_file_attachments_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.prescriptions ADD CONSTRAINT FK_prescriptions_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.prescriptions ADD CONSTRAINT FK_prescriptions_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.prescriptions ADD CONSTRAINT FK_prescriptions_prescriber_staff_id FOREIGN KEY (prescriber_staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.prescriptions ADD CONSTRAINT FK_prescriptions_supersedes_prescription_id FOREIGN KEY (supersedes_prescription_id) REFERENCES dbo.prescriptions(id);
ALTER TABLE dbo.prescription_items ADD CONSTRAINT FK_prescription_items_prescription_id FOREIGN KEY (prescription_id) REFERENCES dbo.prescriptions(id);
ALTER TABLE dbo.prescription_items ADD CONSTRAINT FK_prescription_items_medication_id FOREIGN KEY (medication_id) REFERENCES dbo.medications(id);
ALTER TABLE dbo.users ADD CONSTRAINT FK_users_staff_id FOREIGN KEY (staff_id) REFERENCES dbo.staff(id);
ALTER TABLE dbo.users ADD CONSTRAINT FK_users_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.user_roles ADD CONSTRAINT FK_user_roles_user_id FOREIGN KEY (user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.user_roles ADD CONSTRAINT FK_user_roles_role_id FOREIGN KEY (role_id) REFERENCES dbo.roles(id);
ALTER TABLE dbo.user_roles ADD CONSTRAINT FK_user_roles_department_id FOREIGN KEY (department_id) REFERENCES dbo.departments(id);
ALTER TABLE dbo.user_roles ADD CONSTRAINT FK_user_roles_room_id FOREIGN KEY (room_id) REFERENCES dbo.rooms(id);
ALTER TABLE dbo.role_permissions ADD CONSTRAINT FK_role_permissions_role_id FOREIGN KEY (role_id) REFERENCES dbo.roles(id);
ALTER TABLE dbo.role_permissions ADD CONSTRAINT FK_role_permissions_permission_id FOREIGN KEY (permission_id) REFERENCES dbo.permissions(id);
ALTER TABLE dbo.notifications ADD CONSTRAINT FK_notifications_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.notifications ADD CONSTRAINT FK_notifications_appointment_id FOREIGN KEY (appointment_id) REFERENCES dbo.appointments(id);
ALTER TABLE dbo.notifications ADD CONSTRAINT FK_notifications_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.notification_attempts ADD CONSTRAINT FK_notification_attempts_notification_id FOREIGN KEY (notification_id) REFERENCES dbo.notifications(id);
ALTER TABLE dbo.audit_logs ADD CONSTRAINT FK_audit_logs_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id);
ALTER TABLE dbo.audit_logs ADD CONSTRAINT FK_audit_logs_patient_id FOREIGN KEY (patient_id) REFERENCES dbo.patients(id);
ALTER TABLE dbo.audit_logs ADD CONSTRAINT FK_audit_logs_encounter_id FOREIGN KEY (encounter_id) REFERENCES dbo.encounters(id);
ALTER TABLE dbo.audit_logs ADD CONSTRAINT FK_audit_logs_health_check_record_id FOREIGN KEY (health_check_record_id) REFERENCES dbo.health_check_records(id);
ALTER TABLE dbo.external_code_mappings ADD CONSTRAINT FK_external_code_mappings_integration_endpoint_id FOREIGN KEY (integration_endpoint_id) REFERENCES dbo.integration_endpoints(id);
ALTER TABLE dbo.integration_messages ADD CONSTRAINT FK_integration_messages_integration_endpoint_id FOREIGN KEY (integration_endpoint_id) REFERENCES dbo.integration_endpoints(id);

ALTER TABLE dbo.departments ADD CONSTRAINT UQ_departments_department_code UNIQUE (department_code);
ALTER TABLE dbo.rooms ADD CONSTRAINT UQ_rooms_department_id_room_code UNIQUE (department_id, room_code);
ALTER TABLE dbo.staff ADD CONSTRAINT UQ_staff_staff_code UNIQUE (staff_code);
ALTER TABLE dbo.diagnosis_catalog ADD CONSTRAINT UQ_diagnosis_catalog_diagnosis_code UNIQUE (diagnosis_code);
ALTER TABLE dbo.services ADD CONSTRAINT UQ_services_service_code UNIQUE (service_code);
ALTER TABLE dbo.medications ADD CONSTRAINT UQ_medications_medication_code UNIQUE (medication_code);
ALTER TABLE dbo.document_templates ADD CONSTRAINT UQ_document_templates_template_code UNIQUE (template_code);
ALTER TABLE dbo.document_template_versions ADD CONSTRAINT UQ_document_template_versions_document_template_id_version_number UNIQUE (document_template_id, version_number);
ALTER TABLE dbo.document_template_fields ADD CONSTRAINT UQ_document_template_fields_document_template_version_id_field_key UNIQUE (document_template_version_id, field_key);
ALTER TABLE dbo.service_template_mappings ADD CONSTRAINT UQ_service_template_mappings_service_id_document_template_id UNIQUE (service_id, document_template_id);
ALTER TABLE dbo.generated_document_service_requests ADD CONSTRAINT UQ_generated_document_service_requests_generated_document_id_service_request_id UNIQUE (generated_document_id, service_request_id);
ALTER TABLE dbo.encounters ADD CONSTRAINT UQ_encounters_encounter_code UNIQUE (encounter_code);
ALTER TABLE dbo.journeys ADD CONSTRAINT UQ_journeys_encounter_id UNIQUE (encounter_id);
ALTER TABLE dbo.order_rounds ADD CONSTRAINT UQ_order_rounds_encounter_id_round_number UNIQUE (encounter_id, round_number);
ALTER TABLE dbo.payment_authorizations ADD CONSTRAINT UQ_payment_authorizations_service_request_id UNIQUE (service_request_id);
ALTER TABLE dbo.invoices ADD CONSTRAINT UQ_invoices_invoice_number UNIQUE (invoice_number);
ALTER TABLE dbo.companies ADD CONSTRAINT UQ_companies_company_code UNIQUE (company_code);
ALTER TABLE dbo.company_employees ADD CONSTRAINT UQ_company_employees_company_id_employee_code UNIQUE (company_id, employee_code);
ALTER TABLE dbo.company_employees ADD CONSTRAINT UQ_company_employees_company_id_identification_number UNIQUE (company_id, identification_number);
ALTER TABLE dbo.health_check_batches ADD CONSTRAINT UQ_health_check_batches_company_id_batch_code UNIQUE (company_id, batch_code);
ALTER TABLE dbo.health_check_batch_services ADD CONSTRAINT UQ_health_check_batch_services_health_check_batch_id_service_id UNIQUE (health_check_batch_id, service_id);
ALTER TABLE dbo.health_check_batch_employees ADD CONSTRAINT UQ_health_check_batch_employees_health_check_batch_id_company_employee_id UNIQUE (health_check_batch_id, company_employee_id);
ALTER TABLE dbo.health_check_batch_employee_services ADD CONSTRAINT UQ_health_check_batch_employee_services_health_check_batch_employee_id_health_check_batch_service_id UNIQUE (health_check_batch_employee_id, health_check_batch_service_id);
ALTER TABLE dbo.health_check_import_rows ADD CONSTRAINT UQ_health_check_import_rows_health_check_import_job_id_row_number UNIQUE (health_check_import_job_id, row_number);
ALTER TABLE dbo.specimens ADD CONSTRAINT UQ_specimens_specimen_code UNIQUE (specimen_code);
ALTER TABLE dbo.specimen_service_requests ADD CONSTRAINT UQ_specimen_service_requests_specimen_id_service_request_id UNIQUE (specimen_id, service_request_id);
ALTER TABLE dbo.lab_panels ADD CONSTRAINT UQ_lab_panels_panel_code UNIQUE (panel_code);
ALTER TABLE dbo.analytes ADD CONSTRAINT UQ_analytes_analyte_code UNIQUE (analyte_code);
ALTER TABLE dbo.lab_panel_items ADD CONSTRAINT UQ_lab_panel_items_lab_panel_id_analyte_id UNIQUE (lab_panel_id, analyte_id);
ALTER TABLE dbo.lab_results ADD CONSTRAINT UQ_lab_results_service_request_id_version_number UNIQUE (service_request_id, version_number);
ALTER TABLE dbo.lab_result_values ADD CONSTRAINT UQ_lab_result_values_lab_result_id_analyte_id UNIQUE (lab_result_id, analyte_id);
ALTER TABLE dbo.diagnostic_reports ADD CONSTRAINT UQ_diagnostic_reports_service_request_id_version_number UNIQUE (service_request_id, version_number);
ALTER TABLE dbo.prescriptions ADD CONSTRAINT UQ_prescriptions_prescription_number UNIQUE (prescription_number);
ALTER TABLE dbo.users ADD CONSTRAINT UQ_users_auth_provider_auth_subject UNIQUE (auth_provider, auth_subject);
ALTER TABLE dbo.roles ADD CONSTRAINT UQ_roles_role_code UNIQUE (role_code);
ALTER TABLE dbo.permissions ADD CONSTRAINT UQ_permissions_permission_code UNIQUE (permission_code);
ALTER TABLE dbo.role_permissions ADD CONSTRAINT UQ_role_permissions_role_id_permission_id UNIQUE (role_id, permission_id);
ALTER TABLE dbo.notifications ADD CONSTRAINT UQ_notifications_idempotency_key UNIQUE (idempotency_key);
ALTER TABLE dbo.notification_attempts ADD CONSTRAINT UQ_notification_attempts_notification_id_attempt_number UNIQUE (notification_id, attempt_number);
ALTER TABLE dbo.integration_endpoints ADD CONSTRAINT UQ_integration_endpoints_endpoint_code UNIQUE (endpoint_code);
ALTER TABLE dbo.idempotency_keys ADD CONSTRAINT UQ_idempotency_keys_scope_idempotency_key UNIQUE (scope, idempotency_key);

CREATE UNIQUE INDEX UX_patients_identification_number ON dbo.patients (identification_number);
CREATE UNIQUE INDEX UX_patients_patient_code ON dbo.patients (patient_code);
CREATE INDEX IX_patients_name_dob ON dbo.patients (full_name_normalized, date_of_birth);
CREATE INDEX IX_patients_phone ON dbo.patients (phone) WHERE phone IS NOT NULL;
CREATE INDEX IX_patient_allergies_patient ON dbo.patient_allergies (patient_id, ended_at);
CREATE INDEX IX_patient_conditions_patient ON dbo.patient_conditions (patient_id, clinical_status);
CREATE UNIQUE INDEX UX_staff_department_assignments_active_primary ON dbo.staff_department_assignments (staff_id) WHERE is_primary = 1 AND valid_to IS NULL;
CREATE INDEX IX_staff_department_assignments_staff ON dbo.staff_department_assignments (staff_id, valid_to);
CREATE INDEX IX_staff_department_assignments_department ON dbo.staff_department_assignments (department_id, valid_to);
CREATE INDEX IX_service_prices_lookup ON dbo.service_prices (service_id, price_type, effective_from, effective_to);
CREATE INDEX IX_document_template_fields_version ON dbo.document_template_fields (document_template_version_id, display_order);
CREATE UNIQUE INDEX UX_service_template_mappings_active_service ON dbo.service_template_mappings (service_id) WHERE is_active = 1;
CREATE INDEX IX_service_template_mappings_template ON dbo.service_template_mappings (document_template_id, is_active);
CREATE INDEX IX_generated_documents_encounter ON dbo.generated_documents (encounter_id, print_sequence, status);
CREATE INDEX IX_generated_document_service_requests_document ON dbo.generated_document_service_requests (generated_document_id);
CREATE INDEX IX_generated_document_service_requests_request ON dbo.generated_document_service_requests (service_request_id);
CREATE INDEX IX_appointments_patient ON dbo.appointments (patient_id, scheduled_start);
CREATE INDEX IX_appointments_source_encounter ON dbo.appointments (source_encounter_id) WHERE source_encounter_id IS NOT NULL;
CREATE INDEX IX_appointments_schedule ON dbo.appointments (scheduled_start, status);
CREATE INDEX IX_encounters_patient ON dbo.encounters (patient_id, started_at desc);
CREATE INDEX IX_encounters_status ON dbo.encounters (status, started_at);
CREATE UNIQUE INDEX UX_encounter_assignments_active ON dbo.encounter_assignments (encounter_id) WHERE ended_at IS NULL;
CREATE INDEX IX_encounter_assignments_doctor ON dbo.encounter_assignments (doctor_staff_id, ended_at);
CREATE INDEX IX_encounter_assignments_room ON dbo.encounter_assignments (room_id, ended_at, assigned_at);
CREATE INDEX IX_journeys_stage ON dbo.journeys (current_stage, current_room_id, stage_entered_at);
CREATE INDEX IX_journey_events_journey ON dbo.journey_events (journey_id, occurred_at);
CREATE INDEX IX_clinical_notes_encounter ON dbo.clinical_notes (encounter_id, created_at desc);
CREATE INDEX IX_service_requests_worklist ON dbo.service_requests (performing_department_id, performing_room_id, status, priority, ordered_at);
CREATE INDEX IX_payment_authorizations_status ON dbo.payment_authorizations (status, service_request_id);
CREATE INDEX IX_invoices_encounter ON dbo.invoices (encounter_id, status, issued_at);
CREATE INDEX IX_invoice_items_invoice ON dbo.invoice_items (invoice_id);
CREATE INDEX IX_payments_invoice ON dbo.payments (invoice_id, status);
CREATE UNIQUE INDEX UX_payments_gateway_transaction ON dbo.payments (gateway_transaction_id) WHERE gateway_transaction_id IS NOT NULL;
CREATE UNIQUE INDEX UX_companies_tax_code ON dbo.companies (tax_code) WHERE tax_code IS NOT NULL;
CREATE INDEX IX_companies_name ON dbo.companies (company_name);
CREATE INDEX IX_company_employees_company ON dbo.company_employees (company_id, status);
CREATE INDEX IX_company_employees_patient ON dbo.company_employees (patient_id);
CREATE UNIQUE INDEX UX_company_employees_company_patient ON dbo.company_employees (company_id, patient_id) WHERE patient_id IS NOT NULL;
CREATE INDEX IX_health_check_batches_company ON dbo.health_check_batches (company_id, status, start_date);
CREATE INDEX IX_health_check_batch_services_batch ON dbo.health_check_batch_services (health_check_batch_id, status);
CREATE INDEX IX_health_check_batch_employees_batch ON dbo.health_check_batch_employees (health_check_batch_id, status);
CREATE UNIQUE INDEX UX_health_check_records_shs_code ON dbo.health_check_records (shs_code);
CREATE UNIQUE INDEX UX_health_check_records_encounter ON dbo.health_check_records (encounter_id);
CREATE UNIQUE INDEX UX_health_check_records_batch_employee ON dbo.health_check_records (health_check_batch_employee_id) WHERE health_check_batch_employee_id IS NOT NULL AND status = 'ACTIVE';
CREATE INDEX IX_health_check_records_patient ON dbo.health_check_records (patient_id, created_at desc);
CREATE INDEX IX_hcbes_employee ON dbo.health_check_batch_employee_services (health_check_batch_employee_id, billable);
CREATE INDEX IX_hcbes_service ON dbo.health_check_batch_employee_services (health_check_batch_service_id, billable);
CREATE UNIQUE INDEX UX_hcbes_service_request ON dbo.health_check_batch_employee_services (service_request_id);
CREATE INDEX IX_health_check_import_jobs_batch ON dbo.health_check_import_jobs (health_check_batch_id, import_type, created_at desc);
CREATE INDEX IX_health_check_import_rows_job ON dbo.health_check_import_rows (health_check_import_job_id, validation_status);
CREATE INDEX IX_health_check_import_rows_request ON dbo.health_check_import_rows (resolved_service_request_id);
CREATE INDEX IX_notifications_status ON dbo.notifications (status, next_retry_at);
CREATE INDEX IX_notifications_patient ON dbo.notifications (patient_id, created_at desc);
CREATE INDEX IX_external_code_mappings_lookup ON dbo.external_code_mappings (integration_endpoint_id, mapping_type, external_code, valid_to);
CREATE INDEX IX_outbox_events_pending ON dbo.outbox_events (status, created_at);

-- Cross-column invariant explicitly defined by table-design-v2.10.
ALTER TABLE dbo.users ADD CONSTRAINT CK_users_principal_type
    CHECK ((principal_type = 'STAFF' AND staff_id IS NOT NULL AND patient_id IS NULL)
       OR (principal_type = 'PATIENT' AND patient_id IS NOT NULL AND staff_id IS NULL));

