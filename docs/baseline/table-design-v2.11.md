<!-- Updated directly per user instruction because the FINAL DOCX source is unavailable; regenerate from DOCX if restored. -->
Source: `docs/baseline/table-design-v2.11.docx`
Version: 2.11
Baseline date: 23/09/2026
Last sync: 2026-09-23

---
# DATABASE TABLE DESIGN

### Ngoc Khanh Clinic Digital Transformation

Version: 2.11 — baseline 23/09/2026

Source of truth: requirement-v2.5 + use-case-v2.7 + uploaded/current clinic print templates

Status: synchronized baseline for pre-check-in preparation, off-site corporate examinations, manager-only employee import, uniform batch repricing and UUID v7 identifiers

## 1. Design Principles

This is a greenfield target schema for the new outpatient + adult/corporate health-check architecture. It intentionally removes legacy workflow shapes that are no longer part of the agreed business flow.

Core rules:

• Database identifiers are English, snake_case, plural table names, primary key id, foreign key <entity>_id.

• Persisted surrogate identifiers use application-generated UUID v7 values stored as SQL Server uniqueidentifier. Every primary key id and typed relationship key <entity>_id uses uniqueidentifier; public_id and IDENTITY are not used. Business identifiers, correlation IDs, untyped string polymorphic references and non-identifier numeric fields retain their documented types.

• Clinical and financial history is never hard-deleted.

• Final clinical results and issued prescriptions are versioned, never overwritten.

• Corporate prices belong to a batch. A privileged batch-service repricing command updates every related employee assignment price and corporate ServiceRequest price atomically; retail prices and other batches do not change.

• Company-first flow: Company → Batch/scope/location/planned dates → manager imports roster (no Patient creation) → prepare Patient/Encounter PREPARED/SHS/forms → check-in using the same records → doctor selection/execution/results → reports.

• One person in one health-check record/visit has exactly one SHS. The same SHS is shown on every form for that visit. Mẫu số 03 is the master form and carries the Code 128 barcode of SHS.

• There is no separate business barcode/document code per specialist form. Barcode identity belongs to the health-check record.

• Service and print template are different concepts. The uploaded template version decides paper size and render behavior. Multiple services may share one form only when the same template version explicitly uses MERGE_BY_TEMPLATE.

• Front Desk collects both initial consultation fees and diagnostic fees after doctor order. There is no Cashier/Diagnostic Billing role/table.

• Clinic Manager and System Administrator are separate roles.

• Outbound notification baseline is SMS only.

• No queue-ticket model and no pharmacy inventory/dispensing model in target core.

• Employee import is CLINIC_MANAGER-only at upload, mapping, preview and confirm. FRONT_DESK and SYSTEM_ADMIN do not gain this business permission implicitly.

• Preparation/printing is not attendance. Health checks may be performed at the clinic or at a company examination site. Check-in does not create or print a new visit/SHS.

## 2. Domain Model

Patient

-> Encounter

-> OrderRound

-> ServiceRequest

-> PaymentAuthorization

-> Lab/Diagnostic Result

-> Invoice -> Payment

-> Prescription

Company

-> CompanyEmployee

-> HealthCheckBatch

-> HealthCheckBatchService

-> HealthCheckBatchEmployee

-> HealthCheckRecord (SHS)

-> HealthCheckBatchEmployeeService

HealthCheckRecord

-> GeneratedDocument

-> GeneratedDocumentServiceRequest -> ServiceRequest

-> DocumentTemplateVersion

DocumentTemplateVersion -> DocumentTemplateField

Service -> ServiceTemplateMapping -> DocumentTemplate

HealthCheckBatch

-> HealthCheckImportJob -> HealthCheckImportRow

## 3. Table Inventory

### 3.1 Patient & Identity

patients

patient_allergies

patient_conditions

### 3.2 Organization & Catalog

departments

rooms

staff

staff_department_assignments

diagnosis_catalog

services

service_prices

medications

### 3.3 Templates & Documents

document_templates

document_template_versions

document_template_fields

service_template_mappings

generated_documents

generated_document_service_requests

### 3.4 Appointment, Encounter & Clinical

appointments

encounters

encounter_assignments

vital_signs

clinical_notes

encounter_diagnoses

### 3.5 Orders & Billing

order_rounds

service_requests

payment_authorizations

invoices

invoice_items

invoice_adjustments

payments

### 3.6 Corporate Health Check

companies

company_employees

health_check_batches

health_check_batch_services

health_check_batch_employees

health_check_records

health_check_batch_employee_services

health_check_import_jobs

health_check_import_rows

### 3.7 Laboratory & Diagnostics

specimens

specimen_service_requests

lab_panels

analytes

lab_panel_items

analyte_reference_ranges

lab_results

lab_result_values

imaging_studies

diagnostic_reports

file_attachments

### 3.8 Prescription

prescriptions

prescription_items

### 3.9 Security, SMS, Audit & Integration

users

roles

permissions

user_roles

role_permissions

notifications

notification_attempts

audit_logs

integration_endpoints

external_code_mappings

integration_messages

idempotency_keys

outbox_events

## 4. Detailed Table Design

#### 4.1 patients

Purpose: Canonical patient record. CCCD is the only supported legal/business identifier in the baseline.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| patient_code | varchar(24) | NOT NULL UNIQUE |
| identification_number | varchar(20) | NOT NULL UNIQUE |
| full_name | nvarchar(200) | NOT NULL |
| full_name_normalized | nvarchar(200) | NOT NULL |
| date_of_birth | date | NOT NULL |
| sex | varchar(16) | NOT NULL |
| phone | nvarchar(30) | NULL |
| email | nvarchar(200) | NULL |
| address | nvarchar(500) | NULL |
| ward_code | varchar(20) | NULL |
| province_code | varchar(20) | NULL |
| occupation | nvarchar(200) | NULL |
| note | nvarchar(1000) | NULL |
| status | varchar(16) | NOT NULL DEFAULT 'ACTIVE' |
| created_at datetime2(3) NOT NULL |  |  |
| updated_at datetime2(3) NOT NULL |  |  |
| row_version | rowversion |  |

Rules / Constraints:

• CCCD is mandatory and is the only identity document supported by the baseline.

• CCCD is stored directly on patients as the sole supported patient identifier.

• New Patient creation performs exact lookup by identification_number before insert.

• No fuzzy duplicate/merge flow by name or phone is part of the target schema.

• status: ACTIVE | INACTIVE.

Indexes:

• UX_patients_identification_number(identification_number)

• UX_patients_patient_code(patient_code)

• IX_patients_name_dob(full_name_normalized, date_of_birth)

• IX_patients_phone(phone) filtered non-null

#### 4.2 patient_allergies

Purpose: Patient safety allergy/contraindication information.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| patient_id | uniqueidentifier | NOT NULL REFERENCES patients(id) |
| substance_code | varchar(50) | NULL |
| substance_name | nvarchar(200) | NOT NULL |
| reaction | nvarchar(500) | NULL |
| severity | varchar(16) | NOT NULL DEFAULT 'UNKNOWN' |
| verification_status | varchar(16) | NOT NULL DEFAULT 'CONFIRMED' |
| recorded_by_user_id | uniqueidentifier | NULL REFERENCES users(id) |
| recorded_at datetime2(3) NOT NULL |  |  |
| ended_at datetime2(3) NULL |  |  |

Indexes:

• IX_patient_allergies_patient(patient_id, ended_at)

#### 4.3 patient_conditions

Purpose: Long-term patient-level conditions.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| patient_id | uniqueidentifier | NOT NULL REFERENCES patients(id) |
| diagnosis_code | varchar(20) | NULL |
| condition_name | nvarchar(300) | NOT NULL |
| clinical_status | varchar(16) | NOT NULL |
| onset_date | date | NULL |
| resolved_date | date | NULL |
| note | nvarchar(1000) | NULL |
| recorded_at datetime2(3) NOT NULL |  |  |

Indexes:

• IX_patient_conditions_patient(patient_id, clinical_status)

#### 4.4 departments

Purpose: Operational/clinical departments.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| department_code | varchar(30) | NOT NULL UNIQUE |
| department_name | nvarchar(200) | NOT NULL |
| department_type | varchar(32) | NOT NULL |
| is_active | bit | NOT NULL DEFAULT 1 |
| created_at datetime2(3) NOT NULL |  |  |
| updated_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• department_type examples: FRONT_DESK | CLINIC | LAB | ULTRASOUND | XRAY | ECG | OTHER.

• No BILLING department is required by the agreed flow; Front Desk performs payment operations through permissions.

#### 4.5 rooms

Purpose: Physical rooms/desks.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| department_id | uniqueidentifier | NOT NULL REFERENCES departments(id) |
| room_code | varchar(30) | NOT NULL |
| room_name | nvarchar(200) | NOT NULL |
| floor | nvarchar(50) | NULL |
| location_note | nvarchar(300) | NULL |
| room_type | varchar(32) | NOT NULL |
| is_active | bit | NOT NULL DEFAULT 1 |

Rules / Constraints:

• UNIQUE(department_id, room_code).

#### 4.6 staff

Purpose: Clinical/operational staff profile independent of login identity.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| staff_code | varchar(30) | NOT NULL UNIQUE |
| full_name | nvarchar(200) | NOT NULL |
| staff_type | varchar(32) | NOT NULL |
| license_number | nvarchar(100) | NULL |
| phone | nvarchar(30) | NULL |
| is_active | bit | NOT NULL DEFAULT 1 |

Rules / Constraints:

• Passwords are never stored here.

#### 4.7 staff_department_assignments

Purpose: Staff-to-department membership history.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| staff_id | uniqueidentifier | NOT NULL REFERENCES staff(id) |
| department_id | uniqueidentifier | NOT NULL REFERENCES departments(id) |
| is_primary | bit | NOT NULL DEFAULT 0 |
| valid_from | date | NOT NULL |
| valid_to | date | NULL |

Rules / Constraints:

• A staff member may have multiple active department memberships but at most one active primary assignment.

Indexes:

• UX_staff_department_assignments_active_primary(staff_id) filtered is_primary=1 AND valid_to IS NULL

• IX_staff_department_assignments_staff(staff_id, valid_to)

• IX_staff_department_assignments_department(department_id, valid_to)

#### 4.8 diagnosis_catalog

Purpose: Diagnosis/ICD reference catalog.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| diagnosis_code | varchar(20) | NOT NULL UNIQUE |
| name_vi | nvarchar(500) | NOT NULL |
| name_en | nvarchar(500) | NULL |
| parent_code | varchar(20) | NULL |
| is_active | bit | NOT NULL DEFAULT 1 |

#### 4.9 services

Purpose: Unified catalog of exam, lab, imaging and procedure items. This is the billable/operational unit, not the print-template unit.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| service_code | varchar(40) | NOT NULL UNIQUE |
| service_name | nvarchar(300) | NOT NULL |
| service_type | varchar(32) | NOT NULL |
| performing_department_id | uniqueidentifier | NULL REFERENCES departments(id) |
| default_room_id | uniqueidentifier | NULL REFERENCES rooms(id) |
| requires_payment | bit | NOT NULL DEFAULT 1 |
| requires_specimen | bit | NOT NULL DEFAULT 0 |
| health_check_eligible | bit | NOT NULL DEFAULT 0 |
| result_type | varchar(24) | NOT NULL DEFAULT 'NONE' |
| lab_panel_id | uniqueidentifier | NULL REFERENCES lab_panels(id) |
| preparation_instructions | nvarchar(1000) | NULL |
| is_active | bit | NOT NULL DEFAULT 1 |
| created_at datetime2(3) NOT NULL |  |  |
| updated_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• service_type: EXAM | LAB | ULTRASOUND | XRAY | ECG | PROCEDURE | OTHER.

• result_type: NONE | LAB | REPORT | IMAGE_REPORT.

• For MVP, a LAB service may reference one lab_panel_id. The panel expands to analytes through lab_panel_items. If future requirements need one Service to compose multiple panels, migrate to a bridge table then; do not add that complexity now.

#### 4.10 service_prices

Purpose: Effective-dated retail/reference prices. Corporate negotiated prices do not live here.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| service_id | uniqueidentifier | NOT NULL REFERENCES services(id) |
| price_type | varchar(32) | NOT NULL |
| payer_reference | nvarchar(100) | NULL |
| amount | decimal(18,2) | NOT NULL |
| currency | char(3) | NOT NULL DEFAULT 'VND' |
| effective_from datetime2(3) NOT NULL |  |  |
| effective_to datetime2(3) NULL |  |  |
| is_active | bit | NOT NULL DEFAULT 1 |

Rules / Constraints:

• Corporate negotiated price snapshot belongs to health_check_batch_services.

• For the same service_id + price_type + payer_reference, active effective date ranges must not overlap. Enforce in application/service layer or a SQL Server-safe constraint strategy.

Indexes:

• IX_service_prices_lookup(service_id, price_type, effective_from, effective_to)

#### 4.11 medications

Purpose: Medication catalog for prescribing only; no stock/dispensing model.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| medication_code | varchar(40) | NOT NULL UNIQUE |
| medication_name | nvarchar(300) | NOT NULL |
| generic_name | nvarchar(300) | NULL |
| strength | nvarchar(100) | NULL |
| dosage_form | nvarchar(100) | NULL |
| default_route | nvarchar(100) | NULL |
| unit | nvarchar(50) | NULL |
| is_active | bit | NOT NULL DEFAULT 1 |

#### 4.12 document_templates

Purpose: Logical print/report template identity. A template represents one uploaded form family such as Mẫu số 03, Phiếu siêu âm, Phiếu xét nghiệm, Phiếu X-quang, Điện tim or Phụ khoa. Layout is version-specific.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| template_code | varchar(50) | NOT NULL UNIQUE |
| template_name | nvarchar(250) | NOT NULL |
| template_type | varchar(40) | NOT NULL |
| barcode_policy | varchar(24) | NOT NULL DEFAULT 'NONE' |
| is_master_health_check_form | bit | NOT NULL DEFAULT 0 |
| is_active | bit | NOT NULL DEFAULT 1 |
| created_at datetime2(3) NOT NULL |  |  |
| updated_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• barcode_policy: NONE | SHS_CODE128.

• HEALTH_CHECK_FORM_03 is the master form and must use SHS_CODE128.

• Specialist forms display the same SHS as the health-check record; they do not create a separate business code.

• Logical template identity does not determine paper size. Paper size belongs to the uploaded template version.

#### 4.13 document_template_versions

Purpose: Immutable uploaded template versions. This is the source of truth for physical page size, orientation and rendering behavior.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| document_template_id | uniqueidentifier | NOT NULL REFERENCES document_templates(id) |
| version_number | int | NOT NULL |
| source_file_attachment_id | uniqueidentifier | NOT NULL REFERENCES file_attachments(id) |
| paper_size | varchar(16) | NOT NULL |
| custom_width_mm | decimal(8,2) | NULL |
| custom_height_mm | decimal(8,2) | NULL |
| orientation | varchar(16) | NOT NULL DEFAULT 'PORTRAIT' |
| render_mode | varchar(24) | NOT NULL |
| renderer_type | varchar(24) | NOT NULL |
| schema_json | nvarchar(max) | NOT NULL |
| render_template | nvarchar(max) | NULL |
| effective_from datetime2(3) NOT NULL |  |  |
| retired_at datetime2(3) NULL |  |  |
| created_by_user_id | uniqueidentifier | NOT NULL REFERENCES users(id) |
| created_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• UNIQUE(document_template_id, version_number).

• A used version is never edited in place.

• paper_size: A3 | A4 | A5 | A6 | CUSTOM.

• CUSTOM requires custom_width_mm and custom_height_mm.

• render_mode: MASTER_FORM | ONE_PER_SERVICE | MERGE_BY_TEMPLATE.

• renderer_type identifies how the uploaded source is rendered/converted, for example DOCX_TEMPLATE | HTML_TEMPLATE | PDF_OVERLAY.

• Reprint uses the original document_template_version_id stored by generated_documents unless an authorized replacement workflow is explicitly performed.

• The uploaded source file referenced by source_file_attachment_id is authoritative for layout; application code must not infer page size from service_type.

#### 4.14 document_template_fields

#### Purpose: Field/placeholder definitions inside one uploaded template version. This table describes where patient, SHS, company, result and signature values are rendered; it does not decide which service uses the template.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| document_template_version_id | uniqueidentifier | NOT NULL REFERENCES document_template_versions(id) |
| field_key | varchar(80) | NOT NULL |
| display_label | nvarchar(300) | NULL |
| item_type | varchar(24) | NOT NULL |
| selection_mode | varchar(24) | NULL |
| eligibility_rule_json | nvarchar(max) | NULL |
| display_order | int | NOT NULL |
| is_required | bit | NOT NULL DEFAULT 0 |

#### Rules / Constraints:

• UNIQUE(document_template_version_id, field_key).

• No service_id column exists here.

• Generic and result placeholders are configured per uploaded template version.

#### Indexes:

• IX_document_template_fields_version(document_template_version_id, display_order)

#### 4.15 service_template_mappings

#### Purpose: Maps a billable/clinical service to a logical document template. This is the only service -> template mapping source for print-plan resolution.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| service_id | uniqueidentifier | NOT NULL REFERENCES services(id) |
| document_template_id | uniqueidentifier | NOT NULL REFERENCES document_templates(id) |
| display_order | int | NOT NULL DEFAULT 1 |
| is_active | bit | NOT NULL DEFAULT 1 |
| created_at | datetime2(3) | NOT NULL |
| updated_at | datetime2(3) | NOT NULL |

#### Rules / Constraints:

• UNIQUE(service_id, document_template_id).

• Mapping points to the logical template; runtime resolves the active/required document_template_version according to batch/template policy.

• At most one mapping may be active per service at a time. One template may serve many services.

#### Indexes:

• UX_service_template_mappings_active_service(service_id) filtered is_active=1

• IX_service_template_mappings_template(document_template_id, is_active)

#### 4.16 generated_documents

#### Purpose: Shared rendered/printable document instance for every patient encounter. The same engine is used for individual patients and company health-check patients.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| encounter_id | uniqueidentifier | NOT NULL REFERENCES encounters(id) |
| document_template_version_id | uniqueidentifier | NOT NULL REFERENCES document_template_versions(id) |
| document_kind | varchar(40) | NOT NULL |
| print_sequence | int | NOT NULL DEFAULT 1 |
| paper_size_snapshot | varchar(16) | NOT NULL |
| orientation_snapshot | varchar(16) | NOT NULL |
| version_number | int | NOT NULL DEFAULT 1 |
| status | varchar(24) | NOT NULL |
| render_payload_hash | char(64) | NULL |
| file_attachment_id | uniqueidentifier | NULL REFERENCES file_attachments(id) |
| generated_by_user_id | uniqueidentifier | NULL REFERENCES users(id) |
| generated_at | datetime2(3) | NOT NULL |
| invalidated_at | datetime2(3) | NULL |

#### Rules / Constraints:

• encounter_id is mandatory; a generated document always belongs to one Patient through Encounter.

• For health-check encounters, SHS and health-check snapshot are resolved through the unique health_check_records row sharing encounter_id; generated_documents does not duplicate health_check_record_id.

• Mẫu số 03 is generated once per active health-check record as MASTER_FORM.

• Specialist forms are generated from ServiceRequest + service_template_mappings and may contain one or many ServiceRequests.

• status: PREVIEW_GENERATED | PRINT_REQUESTED | STORED | INVALIDATED.

• PRINT_REQUESTED is not proof of physical print success.

#### Indexes:

• IX_generated_documents_encounter(encounter_id, print_sequence, status)

#### 4.17 generated_document_service_requests

#### Purpose: Many-to-many snapshot linking a generated specialist document to the exact shared ServiceRequests rendered on that document.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| generated_document_id | uniqueidentifier | NOT NULL REFERENCES generated_documents(id) |
| service_request_id | uniqueidentifier | NOT NULL REFERENCES service_requests(id) |
| service_code_snapshot | varchar(40) | NOT NULL |
| service_name_snapshot | nvarchar(300) | NOT NULL |
| display_order | int | NOT NULL DEFAULT 1 |
| created_at | datetime2(3) | NOT NULL |

#### Rules / Constraints:

• UNIQUE(generated_document_id, service_request_id).

• MASTER_FORM may have zero rows.

• ONE_PER_SERVICE normally has one row.

• MERGE_BY_TEMPLATE may have many rows.

• This table is shared by individual and corporate patient flows.

#### Indexes:

• IX_generated_document_service_requests_document(generated_document_id)

• IX_generated_document_service_requests_request(service_request_id)

#### 4.18 appointments

Purpose: Appointment/revisit scheduling with optional trace back to the Encounter that created a follow-up appointment.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| patient_id | uniqueidentifier | NOT NULL REFERENCES patients(id) |
| source_encounter_id | uniqueidentifier | NULL REFERENCES encounters(id) |
| department_id | uniqueidentifier | NULL REFERENCES departments(id) |
| doctor_staff_id | uniqueidentifier | NULL REFERENCES staff(id) |
| scheduled_start datetime2(3) NOT NULL |  |  |
| scheduled_end datetime2(3) NULL |  |  |
| status | varchar(16) | NOT NULL |
| reason | nvarchar(500) | NULL |
| created_at datetime2(3) NOT NULL |  |  |
| updated_at datetime2(3) NOT NULL |  |  |

Indexes:

• IX_appointments_patient(patient_id, scheduled_start)

• IX_appointments_source_encounter(source_encounter_id) filtered non-null

• IX_appointments_schedule(scheduled_start, status)

#### 4.19 encounters

Purpose: One outpatient visit/lifecycle aggregate. Reason and priority belong to Encounter; current room/doctor remains in encounter_assignments.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| encounter_code | varchar(30) | NOT NULL UNIQUE |
| patient_id | uniqueidentifier | NOT NULL REFERENCES patients(id) |
| encounter_type | varchar(24) | NOT NULL |
| reason | nvarchar(500) | NULL |
| priority | varchar(16) | NOT NULL DEFAULT 'ROUTINE' |
| status | varchar(16) | NOT NULL |
| started_at | datetime2(3) | NULL for PREPARED; set on actual reception/check-in, not on pre-print preparation |
| completed_at datetime2(3) NULL |  |  |
| canceled_at datetime2(3) NULL |  |  |
| created_at datetime2(3) NOT NULL |  |  |
| updated_at datetime2(3) NOT NULL |  |  |
| row_version | rowversion |  |
| prepared_at | datetime2(3) | NULL; preparation timestamp for a pre-created HEALTH_CHECK visit |
| checked_in_at | datetime2(3) | NULL until attendance is explicitly confirmed |
| checked_in_by_user_id | uniqueidentifier | NULL REFERENCES users(id); actor of actual check-in |

Rules / Constraints:

• encounter_type includes at least OUTPATIENT | HEALTH_CHECK.

• priority: ROUTINE | URGENT (extend only when required).

• status: PREPARED | IN_PROGRESS | COMPLETED | CANCELED. PREPARED is valid only for HEALTH_CHECK. Preparation creates PREPARED; successful check-in changes it to IN_PROGRESS. Ordinary outpatient reception remains IN_PROGRESS.

• Reading/scanning/printing a prepared record is not check-in. Before check-in, started_at and checked_in_at remain NULL; prepared encounters are excluded from doctor/diagnostic worklists and attended-volume metrics.

• Check-in is idempotent and reuses the existing Patient/Encounter/HealthCheckRecord/SHS. It records actual examination date and age validation; a duplicate check-in returns the existing result.

Indexes:

• IX_encounters_patient(patient_id, started_at DESC)

• IX_encounters_status(status, started_at)

#### 4.20 encounter_assignments

Purpose: Doctor/room assignment history and source of the current active clinical destination.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| encounter_id | uniqueidentifier | NOT NULL REFERENCES encounters(id) |
| department_id | uniqueidentifier | NOT NULL REFERENCES departments(id) |
| room_id | uniqueidentifier | NULL REFERENCES rooms(id) |
| doctor_staff_id | uniqueidentifier | NULL REFERENCES staff(id) |
| assigned_at datetime2(3) NOT NULL |  |  |
| ended_at datetime2(3) NULL |  |  |
| assigned_by_user_id | uniqueidentifier | NULL REFERENCES users(id) |
| destination_label | nvarchar(200) | NULL; desk/zone at the batch examination site when room_id is NULL |

Rules / Constraints:

• At most one active assignment (ended_at IS NULL) is allowed per Encounter.

• Moving an Encounter closes the previous active assignment and inserts a new one; history is never overwritten.

• For off-site corporate work, department_id retains clinical ownership; room_id may be NULL and destination_label identifies the examination desk/zone within the batch site. Do not point to a fictitious clinic room.

Indexes:

• UX_encounter_assignments_active(encounter_id) filtered ended_at IS NULL

• IX_encounter_assignments_doctor(doctor_staff_id, ended_at)

• IX_encounter_assignments_room(room_id, ended_at, assigned_at)

#### 4.21 vital_signs

Purpose: Vitals measured during an Encounter. Health-check context is resolved through health_check_records.encounter_id; no duplicate health_check_record_id FK is stored.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| encounter_id | uniqueidentifier | NOT NULL REFERENCES encounters(id) |
| height_cm | decimal(6,2) | NULL |
| weight_kg | decimal(6,2) | NULL |
| bmi | decimal(6,2) | NULL |
| pulse_bpm | int | NULL |
| systolic_bp | int | NULL |
| diastolic_bp | int | NULL |
| physical_classification | nvarchar(100) | NULL |
| measured_at datetime2(3) NOT NULL |  |  |
| recorded_by_staff_id | uniqueidentifier | NULL REFERENCES staff(id) |

Rules / Constraints:

• encounter_id is mandatory. For health checks, resolve HealthCheckRecord through its UNIQUE encounter_id relation.

#### 4.22 clinical_notes

Purpose: Clinical notes for an Encounter. MVP uses DRAFT/FINAL state plus audit; a separate correction/version chain is deferred until required.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| encounter_id | uniqueidentifier | NOT NULL REFERENCES encounters(id) |
| note_type | varchar(32) | NOT NULL |
| content_json | nvarchar(max) | NOT NULL |
| author_staff_id | uniqueidentifier | NOT NULL REFERENCES staff(id) |
| status | varchar(16) | NOT NULL |
| created_at datetime2(3) NOT NULL |  |  |
| finalized_at datetime2(3) NULL |  |  |

Rules / Constraints:

• status: DRAFT | FINAL.

• DRAFT may be edited by authorized clinical users.

• FINAL is not silently overwritten; later changes require an audited correction workflow. Explicit note-version tables/links are deferred beyond MVP.

Indexes:

• IX_clinical_notes_encounter(encounter_id, created_at DESC)

#### 4.23 encounter_diagnoses

Purpose: Diagnoses/conclusions linked to encounter.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| encounter_id | uniqueidentifier | NOT NULL REFERENCES encounters(id) |
| diagnosis_catalog_id | uniqueidentifier | NULL REFERENCES diagnosis_catalog(id) |
| diagnosis_text | nvarchar(500) | NOT NULL |
| diagnosis_type | varchar(24) | NOT NULL |
| is_primary | bit | NOT NULL DEFAULT 0 |
| recorded_by_staff_id | uniqueidentifier | NOT NULL REFERENCES staff(id) |
| recorded_at datetime2(3) NOT NULL |  |  |

#### 4.24 order_rounds

#### Purpose: One doctor-confirmed service-order cycle within an Encounter. Both ordinary orders and corporate health-check selections are confirmed by a Doctor.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| encounter_id | uniqueidentifier | NOT NULL REFERENCES encounters(id) |
| health_check_record_id | uniqueidentifier | NULL REFERENCES health_check_records(id) |
| round_number | int | NOT NULL |
| source_type | varchar(24) | NOT NULL |
| status | varchar(16) | NOT NULL |
| ordered_by_staff_id | uniqueidentifier | NOT NULL REFERENCES staff(id) |
| created_by_user_id | uniqueidentifier | NOT NULL REFERENCES users(id) |
| ordered_at | datetime2(3) | NOT NULL |

#### Rules / Constraints:

• UNIQUE(encounter_id, round_number).

• source_type: DOCTOR | HEALTH_CHECK_PACKAGE.

• ordered_by_staff_id is mandatory for every OrderRound in MVP.

• HEALTH_CHECK_PACKAGE requires health_check_record_id and is created only after a Doctor selects services from the employee's HealthCheckBatchService scope.

• Corporate package ServiceRequests create PaymentAuthorization=NOT_REQUIRED and do not create an individual patient invoice in MVP.

• All downstream ServiceRequests use the same shared clinical worklists/results regardless of source_type.

#### 4.25 service_requests

Purpose: One requested clinical/diagnostic service.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| order_round_id | uniqueidentifier | NOT NULL REFERENCES order_rounds(id) |
| service_id | uniqueidentifier | NOT NULL REFERENCES services(id) |
| status | varchar(16) | NOT NULL |
| priority | varchar(16) | NOT NULL DEFAULT 'ROUTINE' |
| performing_department_id | uniqueidentifier | NULL REFERENCES departments(id) |
| performing_room_id | uniqueidentifier | NULL REFERENCES rooms(id) |
| performing_location_label | nvarchar(200) | NULL; desk/zone at the encounter batch site when no physical clinic room applies |
| service_name_snapshot | nvarchar(300) | NOT NULL |
| unit_price_snapshot | decimal(18,2) | NULL |
| preparation_instructions_snapshot | nvarchar(1000) | NULL |
| ordered_at datetime2(3) NOT NULL |  |  |
| started_at datetime2(3) NULL |  |  |
| completed_at datetime2(3) NULL |  |  |

Rules / Constraints:

• status: ORDERED | IN_PROGRESS | COMPLETED | CANCELED.

• HEALTH_CHECK_PACKAGE price snapshots follow uniform batch repricing: update unit_price_snapshot together with the linked corporate assignment; do not mutate retail requests.

• Clinical execution requires actual check-in and valid payment authorization. Pre-print preparation cannot create executable orders on its own.

Indexes:

• IX_service_requests_worklist(performing_department_id, performing_room_id, status, priority, ordered_at)

#### 4.26 payment_authorizations

Purpose: Whether a service request is financially allowed to execute.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| service_request_id | uniqueidentifier | NOT NULL UNIQUE REFERENCES service_requests(id) |
| status | varchar(20) | NOT NULL |
| invoice_item_id | uniqueidentifier | NULL REFERENCES invoice_items(id) |
| authorized_at datetime2(3) NULL |  |  |
| authorized_by_user_id | uniqueidentifier | NULL REFERENCES users(id) |
| reason | nvarchar(500) | NULL |
| updated_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• status: NOT_REQUIRED | PENDING | AUTHORIZED | WAIVED | REVOKED.

Indexes:

• IX_payment_authorizations_status(status, service_request_id)

#### 4.27 invoices

Purpose: Financial document for an encounter.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| invoice_number | varchar(40) | NOT NULL UNIQUE |
| encounter_id | uniqueidentifier | NOT NULL REFERENCES encounters(id) |
| patient_id | uniqueidentifier | NOT NULL REFERENCES patients(id) |
| invoice_type | varchar(24) | NOT NULL |
| status | varchar(20) | NOT NULL |
| subtotal | decimal(18,2) | NOT NULL |
| discount_amount | decimal(18,2) | NOT NULL DEFAULT 0 |
| total_amount | decimal(18,2) | NOT NULL |
| paid_amount | decimal(18,2) | NOT NULL DEFAULT 0 |
| issued_at datetime2(3) NOT NULL |  |  |
| created_by_user_id | uniqueidentifier | NOT NULL REFERENCES users(id) |

Indexes:

• IX_invoices_encounter(encounter_id, status, issued_at)

#### 4.28 invoice_items

Purpose: Invoice lines.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| invoice_id | uniqueidentifier | NOT NULL REFERENCES invoices(id) |
| service_request_id | uniqueidentifier | NULL REFERENCES service_requests(id) |
| service_id | uniqueidentifier | NULL REFERENCES services(id) |
| description_snapshot | nvarchar(300) | NOT NULL |
| quantity | decimal(12,3) | NOT NULL DEFAULT 1 |
| unit_price | decimal(18,2) | NOT NULL |
| discount_amount | decimal(18,2) | NOT NULL DEFAULT 0 |
| line_total | decimal(18,2) | NOT NULL |

Indexes:

• IX_invoice_items_invoice(invoice_id)

#### 4.29 invoice_adjustments

Purpose: Refund/discount/correction audit.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| invoice_id | uniqueidentifier | NOT NULL REFERENCES invoices(id) |
| adjustment_type | varchar(24) | NOT NULL |
| amount | decimal(18,2) | NOT NULL |
| reason | nvarchar(500) | NOT NULL |
| created_by_user_id | uniqueidentifier | NOT NULL REFERENCES users(id) |
| created_at datetime2(3) NOT NULL |  |  |

#### 4.30 payments

Purpose: Payment transactions performed by authorized Front Desk users.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| invoice_id | uniqueidentifier | NOT NULL REFERENCES invoices(id) |
| payment_method | varchar(24) | NOT NULL |
| amount | decimal(18,2) | NOT NULL |
| status | varchar(24) | NOT NULL |
| gateway_transaction_id | nvarchar(150) | NULL |
| confirmed_by_user_id | uniqueidentifier | NULL REFERENCES users(id) |
| confirmed_at datetime2(3) NULL |  |  |
| created_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• Payment actor is permission-driven; no cashier-specific table exists.

• status: PENDING | CONFIRMED | FAILED | PARTIALLY_REFUNDED | REFUNDED.

Indexes:

• IX_payments_invoice(invoice_id, status)

• UX_payments_gateway_transaction(gateway_transaction_id) filtered non-null

#### 4.31 companies

Purpose: Corporate customer master.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| company_code | varchar(40) | NOT NULL UNIQUE |
| company_name | nvarchar(300) | NOT NULL |
| tax_code | varchar(40) | NULL |
| address | nvarchar(500) | NULL |
| contact_name | nvarchar(200) | NOT NULL |
| contact_phone | nvarchar(30) | NOT NULL |
| contact_job_title | nvarchar(150) | NULL |
| note | nvarchar(1000) | NULL |
| status | varchar(16) | NOT NULL DEFAULT 'ACTIVE' |
| created_at datetime2(3) NOT NULL |  |  |
| updated_at datetime2(3) NOT NULL |  |  |
| row_version | rowversion |  |

Rules / Constraints:

• Do not duplicate Company per health-check batch.

• Baseline supports exactly one primary contact per Company; contact_name, contact_phone and contact_job_title are stored directly on companies.

Indexes:

• UX_companies_tax_code(tax_code) filtered non-null

• IX_companies_name(company_name)

#### 4.32 company_employees

#### Purpose: Company roster membership, independent of Patient creation. Roster identity/demographics are imported business data; patient_id becomes linked when the visit is prepared. They are not a replacement for the clinical Patient master.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| company_id | uniqueidentifier | NOT NULL REFERENCES companies(id) |
| patient_id | uniqueidentifier | NULL REFERENCES patients(id); linked/resolved at visit preparation, not required at roster import |
| employee_code | nvarchar(60) | NOT NULL |
| identification_number | varchar(20) | NOT NULL; roster identity used for exact Patient resolution |
| full_name | nvarchar(200) | NOT NULL; roster name |
| date_of_birth | date | NOT NULL; roster DOB |
| sex | varchar(16) | NOT NULL; roster sex |
| department_name | nvarchar(200) | NULL |
| job_title | nvarchar(200) | NULL |
| occupation | nvarchar(200) | NULL |
| status | varchar(16) | NOT NULL DEFAULT 'ACTIVE' |
| created_at | datetime2(3) | NOT NULL |
| updated_at | datetime2(3) | NOT NULL |

#### Rules / Constraints:

• UNIQUE(company_id, employee_code).

• UNIQUE(company_id, identification_number). For non-null patient_id, enforce one membership per company/patient using a filtered unique index.

• Employee import does not create Patient or Encounter. At preparation, resolve/create Patient by exact CCCD and link patient_id. Re-import must not silently relink an employee to a different Patient or overwrite Patient master. Identity conflicts require review before issuing forms.

• employee_code, department_name, job_title and occupation are company-specific attributes.

#### Indexes:

• IX_company_employees_company(company_id, status)

• IX_company_employees_patient(patient_id)

• UX_company_employees_company_patient(company_id, patient_id) filtered patient_id IS NOT NULL; do not use an unfiltered nullable unique constraint that prevents multiple unlinked roster members.

#### 4.33 health_check_batches

#### Purpose: One corporate health-check campaign/batch.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| company_id | uniqueidentifier | NOT NULL REFERENCES companies(id) |
| batch_code | varchar(40) | NOT NULL |
| batch_name | nvarchar(250) | NOT NULL |
| start_date | date | NULL |
| end_date | date | NULL |
| reason | nvarchar(300) | NULL |
| payer_type | varchar(24) | NULL |
| examination_site_type | varchar(16) | NOT NULL; CLINIC \| COMPANY |
| examination_site_name | nvarchar(250) | NOT NULL; planned examination site |
| examination_site_address | nvarchar(500) | NULL for clinic context; required for COMPANY before preparation/printing |
| master_template_version_id | uniqueidentifier | NOT NULL REFERENCES document_template_versions(id) |
| status | varchar(24) | NOT NULL DEFAULT 'DRAFT' |
| finalized_at | datetime2(3) | NULL |
| closed_at | datetime2(3) | NULL |
| created_by_user_id | uniqueidentifier | NOT NULL REFERENCES users(id) |
| created_at | datetime2(3) | NOT NULL |
| updated_at | datetime2(3) | NOT NULL |

#### Rules / Constraints:

• UNIQUE(company_id, batch_code).

• Lifecycle: DRAFT | READY | IN_PROGRESS | RESULT_PROCESSING | FINALIZED | CLOSED | CANCELED.

• Scope/template configuration is freely editable in DRAFT; READY freezes normal changes. Price remains adjustable through the privileged uniform-repricing transaction in section 7.7. FINALIZED/CLOSED requires an authorized reopen, reason and audit before repricing, then re-finalization; previously exported reports remain historical.

• master_template_version_id freezes the Mẫu số 03 version used by the batch.

• start_date/end_date describe the planned campaign window. Each prepared HealthCheckRecord additionally requires its planned examination date; the actual date is captured at check-in.

• A company examination site uses the same application/workflow with department assignments and desk/zone labels. Offline operation is a separate unresolved deployment requirement, not implied by COMPANY.

#### Indexes:

• IX_health_check_batches_company(company_id, status, start_date)

#### 4.34 health_check_batch_services

#### Purpose: Selected subset of Service Catalog plus negotiated corporate price and frozen specialist-template version for one batch.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| health_check_batch_id | uniqueidentifier | NOT NULL REFERENCES health_check_batches(id) |
| service_id | uniqueidentifier | NOT NULL REFERENCES services(id) |
| document_template_version_id | uniqueidentifier | NULL REFERENCES document_template_versions(id) |
| service_code_snapshot | varchar(40) | NOT NULL |
| service_name_snapshot | nvarchar(300) | NOT NULL |
| base_price_snapshot | decimal(18,2) | NOT NULL |
| negotiated_unit_price | decimal(18,2) | NOT NULL |
| currency | char(3) | NOT NULL DEFAULT 'VND' |
| display_order | int | NOT NULL |
| status | varchar(16) | NOT NULL DEFAULT 'ACTIVE' |
| created_at | datetime2(3) | NOT NULL |
| updated_at | datetime2(3) | NOT NULL |

#### Rules / Constraints:

• UNIQUE(health_check_batch_id, service_id).

• Corporate price does not modify service_prices.

• negotiated_unit_price applies to the same service for all employees in the batch in MVP.

• negotiated_unit_price is the current common price for every employee assignment of this service in the batch, including completed/billable assignments. Repricing updates all copies atomically; base_price_snapshot remains the original retail/reference snapshot.

• When batch becomes READY, document_template_version_id is frozen from service_template_mappings for printable services.

• NULL document_template_version_id is allowed only when the service does not require a printable specialist form.

#### Indexes:

• IX_health_check_batch_services_batch(health_check_batch_id, status)

#### 4.35 health_check_batch_employees

#### Purpose: CompanyEmployee participation in a batch, with employment and validated administrative roster snapshots used to prepare the visit. Patient is optional before preparation and is resolved through CompanyEmployee.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| health_check_batch_id | uniqueidentifier | NOT NULL REFERENCES health_check_batches(id) |
| company_employee_id | uniqueidentifier | NOT NULL REFERENCES company_employees(id) |
| employee_code_snapshot | nvarchar(60) | NOT NULL |
| department_snapshot | nvarchar(200) | NULL |
| job_title_snapshot | nvarchar(200) | NULL |
| occupation_snapshot | nvarchar(200) | NULL |
| administrative_snapshot_json | nvarchar(max) | NOT NULL; validated versioned-schema object containing all confirmed import administrative fields for this employee/batch, including CCCD, name, DOB, sex and optional Form 03 fields |
| status | varchar(24) | NOT NULL DEFAULT 'REGISTERED' |
| created_at | datetime2(3) | NOT NULL |

#### Rules / Constraints:

• UNIQUE(health_check_batch_id, company_employee_id).

• Do not duplicate patient_id. administrative_snapshot_json intentionally freezes imported visit-prefill data, including identity fields, independently of mutable CompanyEmployee/Patient master. Validate its schema and required fields; do not treat arbitrary JSON as an unchecked API contract.

• Employment snapshots protect historical company reports when CompanyEmployee fields later change.

• Confirmed import data remains available until visit preparation; HealthCheckRecord receives its own typed administrative snapshot. Re-import never rewrites an already-issued HealthCheckRecord snapshot.

• The import/preview may flag differences against Patient; only the authorized preparation workflow resolves identity conflicts. Non-empty validated roster values prefill the visit; missing optional values may be supplemented from Patient/batch context without modifying Patient master.

#### Indexes:

• IX_health_check_batch_employees_batch(health_check_batch_id, status)

#### 4.36 health_check_records

#### Purpose: One adult health-check record/visit identity and owner of SHS plus the immutable administrative snapshot used by Mẫu số 03 for that visit. It always belongs to a shared Encounter.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| shs_code | varchar(40) | NOT NULL UNIQUE |
| source_type | varchar(16) | NOT NULL |
| patient_id | uniqueidentifier | NOT NULL REFERENCES patients(id) |
| health_check_batch_employee_id | uniqueidentifier | NULL REFERENCES health_check_batch_employees(id) |
| encounter_id | uniqueidentifier | NOT NULL REFERENCES encounters(id) |
| master_template_version_id | uniqueidentifier | NOT NULL REFERENCES document_template_versions(id) |
| full_name_snapshot | nvarchar(200) | NOT NULL |
| date_of_birth_snapshot | date | NOT NULL |
| sex_snapshot | varchar(16) | NOT NULL |
| identification_number_snapshot | varchar(20) | NOT NULL |
| identification_number_issue_date_snapshot | date | NULL |
| identification_number_issue_place_snapshot | nvarchar(200) | NULL |
| ethnicity_snapshot | nvarchar(100) | NULL |
| subject_type_snapshot | nvarchar(100) | NULL |
| payer_source_snapshot | nvarchar(150) | NULL |
| blood_group_snapshot | varchar(16) | NULL |
| phone_snapshot | nvarchar(30) | NULL |
| province_snapshot | nvarchar(150) | NULL |
| ward_snapshot | nvarchar(150) | NULL |
| address_detail_snapshot | nvarchar(500) | NULL |
| occupation_snapshot | nvarchar(200) | NULL |
| workplace_or_school_snapshot | nvarchar(300) | NULL |
| health_check_reason_snapshot | nvarchar(500) | NULL |
| planned_examination_date | date | NOT NULL; validate adult eligibility before preparing/printing |
| actual_examination_date | date | NULL until check-in; revalidate adult eligibility on this date |
| status | varchar(24) | NOT NULL DEFAULT 'ACTIVE' |
| replaces_health_check_record_id | uniqueidentifier | NULL REFERENCES health_check_records(id) |
| created_at | datetime2(3) | NOT NULL |
| completed_at | datetime2(3) | NULL |
| canceled_at | datetime2(3) | NULL |
| row_version | rowversion |  |

#### Rules / Constraints:

• shs_code is immutable and is the single business code shared by Mẫu số 03 and specialist forms of the health-check Encounter.

• Mẫu số 03 barcode is Code 128 of shs_code.

• patient_id and encounter_id are mandatory.

• Each new HEALTH_CHECK visit creates one HealthCheckRecord and snapshot, possibly before check-in while Encounter is PREPARED. Use validated batch administrative_snapshot_json first, fill missing optional data from Patient/company/batch context, and resolve identity conflicts before issue. Preparation retries resolve the existing valid visit; check-in never creates another record.

• full_name_snapshot, date_of_birth_snapshot, sex_snapshot and identification_number_snapshot are mandatory; identification_number_snapshot is always NOT NULL. Other administrative snapshot fields may be NULL when not required/available.

• Reprint must render from this HealthCheckRecord snapshot and the original template version, never from the current mutable Patient master. Later changes to Patient phone/address/other demographics do not rewrite previous health-check records.

• No identity_type/passport model is introduced; baseline uses CCCD only.

• UNIQUE(encounter_id): one HealthCheckRecord/SHS per health-check Encounter.

• For corporate flow, patient_id must equal company_employees.patient_id resolved through health_check_batch_employee_id.

• Only one active health-check record is allowed per health_check_batch_employee_id.

• For all sources, patient_id must equal encounters.patient_id. For corporate visits, CompanyEmployee.company_id must equal the batch company, and the resolved Patient and planned/actual examination date must belong to this visit.

• Existing issued snapshots and SHS are reused at check-in. If actual identity differs, stop and resolve the discrepancy; do not silently issue a new visit or change the printed identity.

#### Indexes:

• UX_health_check_records_shs_code(shs_code)

• UX_health_check_records_encounter(encounter_id)

• UX_health_check_records_batch_employee(health_check_batch_employee_id) filtered active/non-null

• IX_health_check_records_patient(patient_id, created_at DESC)

#### 4.37 health_check_batch_employee_services

#### Purpose: Doctor-confirmed per-employee corporate service assignment. Row existence means the doctor selected this batch service for the employee. The row is created together with the ServiceRequest; medical execution state and results remain in ServiceRequest/LabResult/DiagnosticReport.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| health_check_batch_employee_id | uniqueidentifier | NOT NULL REFERENCES health_check_batch_employees(id) |
| health_check_batch_service_id | uniqueidentifier | NOT NULL REFERENCES health_check_batch_services(id) |
| service_request_id | uniqueidentifier | NOT NULL REFERENCES service_requests(id) |
| billable | bit | NOT NULL DEFAULT 0 |
| unit_price_snapshot | decimal(18,2) | NOT NULL |
| created_at | datetime2(3) | NOT NULL |
| updated_at | datetime2(3) | NOT NULL |

#### Rules / Constraints:

• UNIQUE(health_check_batch_employee_id, health_check_batch_service_id).

• A row exists only after a doctor selects/confirms the service for that employee; do not add a redundant selected boolean.

• Only Doctor-authorized application commands may create/delete an assignment before clinical execution begins; Front Desk and Patient cannot modify the subset.

• health_check_batch_service_id must belong to the same HealthCheckBatch as health_check_batch_employee_id; services outside batch scope are rejected.

• Different employees in the same batch may have different service subsets.

• service_request_id is NOT NULL for every assignment because assignment row and ServiceRequest are created in the same transaction.

• The selecting doctor is derived from the linked ServiceRequest → OrderRound.ordered_by_staff_id; do not duplicate selected_by/assignment_source columns here.

• unit_price_snapshot copies the batch negotiated price at assignment and is updated for every related assignment by privileged uniform batch repricing. It is never an employee-specific override. Keep the corporate ServiceRequest price copy synchronized in the same transaction.

• billable determines whether the row participates in corporate financial reports. MVP sets billable=1 only after the linked ServiceRequest reaches COMPLETED successfully; canceled/unperformed assignments remain 0.

• No execution_status, result_status, result JSON or line_amount is stored here.

• Employee detail and service summary reports both SUM(unit_price_snapshot) from the same billable rows.

• Reports generated for comparison must use the same consistent data snapshot. The detail sum equals summary sum and quantity × the common batch-service price after any committed repricing.

• Canceled/unperformed rows remain non-billable; preparing or printing a visit never changes billable to 1. Repricing does not change medical execution state or billable eligibility.

#### Indexes:

• IX_hcbes_employee(health_check_batch_employee_id, billable)

• IX_hcbes_service(health_check_batch_service_id, billable)

• UX_hcbes_service_request(service_request_id) UNIQUE

#### 4.38 health_check_import_jobs

#### Purpose: Audit/control header for corporate Excel employee-list and result imports.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| health_check_batch_id | uniqueidentifier | NOT NULL REFERENCES health_check_batches(id) |
| import_type | varchar(20) | NOT NULL |
| source_file_attachment_id | uniqueidentifier | NOT NULL REFERENCES file_attachments(id) |
| status | varchar(20) | NOT NULL |
| column_mapping_json | nvarchar(max) | NULL |
| total_rows | int | NOT NULL DEFAULT 0 |
| valid_rows | int | NOT NULL DEFAULT 0 |
| warning_rows | int | NOT NULL DEFAULT 0 |
| error_rows | int | NOT NULL DEFAULT 0 |
| created_by_user_id | uniqueidentifier | NOT NULL REFERENCES users(id) |
| confirmed_by_user_id | uniqueidentifier | NULL REFERENCES users(id) |
| created_at | datetime2(3) | NOT NULL |
| confirmed_at | datetime2(3) | NULL |

#### Rules / Constraints:

• import_type: EMPLOYEE_LIST | RESULTS.

• EMPLOYEE_LIST upload/mapping/preview/confirm is allowed only to CLINIC_MANAGER. RESULTS import has separate permissions; employee-import permission must not grant result verification/finalization.

• Confirm roster imports without creating Patient or Encounter. Persist validated administrative fields into health_check_batch_employees.administrative_snapshot_json so preparation does not depend on transient upload data.

• status: UPLOADED | VALIDATED | CONFIRMED | PARTIAL | FAILED | CANCELED.

#### Indexes:

• IX_health_check_import_jobs_batch(health_check_batch_id, import_type, created_at DESC)

#### 4.39 health_check_import_rows

#### Purpose: Row-level validation/resolution for corporate Excel imports. Result imports resolve to the shared ServiceRequest/result domain.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| health_check_import_job_id | uniqueidentifier | NOT NULL REFERENCES health_check_import_jobs(id) |
| row_number | int | NOT NULL |
| employee_code_snapshot | nvarchar(60) | NULL |
| identification_number_snapshot | varchar(20) | NULL |
| service_code_snapshot | varchar(40) | NULL |
| validation_status | varchar(16) | NOT NULL |
| error_codes_json | nvarchar(max) | NULL |
| normalized_payload_json | nvarchar(max) | NOT NULL |
| resolved_patient_id | uniqueidentifier | NULL REFERENCES patients(id) |
| resolved_company_employee_id | uniqueidentifier | NULL REFERENCES company_employees(id) |
| resolved_batch_employee_id | uniqueidentifier | NULL REFERENCES health_check_batch_employees(id) |
| resolved_batch_service_id | uniqueidentifier | NULL REFERENCES health_check_batch_services(id) |
| resolved_service_request_id | uniqueidentifier | NULL REFERENCES service_requests(id) |

#### Rules / Constraints:

• UNIQUE(health_check_import_job_id, row_number).

• EMPLOYEE_LIST requires employeeCode, valid CCCD, full name, DOB and sex. Resolve CompanyEmployee/BatchEmployee; resolved_patient_id may remain NULL until a separate preparation command.

• RESULTS requires serviceCode and at least one of employeeCode or CCCD.

• Exact employee/service resolution is required; no fuzzy name matching.

• No polymorphic applied_entity_type/applied_entity_id fields are used.

#### Indexes:

• IX_health_check_import_rows_job(health_check_import_job_id, validation_status)

• IX_health_check_import_rows_request(resolved_service_request_id)

#### 4.40 specimens

Purpose: Laboratory specimen header.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| specimen_code | varchar(50) | NOT NULL UNIQUE |
| patient_id | uniqueidentifier | NOT NULL REFERENCES patients(id) |
| encounter_id | uniqueidentifier | NULL REFERENCES encounters(id) |
| specimen_type | varchar(40) | NOT NULL |
| status | varchar(20) | NOT NULL |
| collected_at datetime2(3) NULL |  |  |
| collected_by_staff_id | uniqueidentifier | NULL REFERENCES staff(id) |
| received_at datetime2(3) NULL |  |  |

#### 4.41 specimen_service_requests

Purpose: Many-to-many specimen to lab service request.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| specimen_id | uniqueidentifier | NOT NULL REFERENCES specimens(id) |
| service_request_id | uniqueidentifier | NOT NULL REFERENCES service_requests(id) |

Rules / Constraints:

• UNIQUE(specimen_id, service_request_id).

#### 4.42 lab_panels

Purpose: Laboratory panel definition.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| panel_code | varchar(40) | NOT NULL UNIQUE |
| panel_name | nvarchar(250) | NOT NULL |
| is_active | bit | NOT NULL DEFAULT 1 |

#### 4.43 analytes

Purpose: Laboratory analyte catalog.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| analyte_code | varchar(40) | NOT NULL UNIQUE |
| analyte_name | nvarchar(250) | NOT NULL |
| default_unit | nvarchar(40) | NULL |
| value_type | varchar(20) | NOT NULL |
| is_active | bit | NOT NULL DEFAULT 1 |

#### 4.44 lab_panel_items

Purpose: Analytes contained by a lab panel.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| lab_panel_id | uniqueidentifier | NOT NULL REFERENCES lab_panels(id) |
| analyte_id | uniqueidentifier | NOT NULL REFERENCES analytes(id) |
| display_order | int | NOT NULL |

Rules / Constraints:

• UNIQUE(lab_panel_id, analyte_id).

#### 4.45 analyte_reference_ranges

Purpose: Demographic/effective reference ranges.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| analyte_id | uniqueidentifier | NOT NULL REFERENCES analytes(id) |
| sex | varchar(16) | NULL |
| age_min_days | int | NULL |
| age_max_days | int | NULL |
| lower_bound | decimal(18,6) | NULL |
| upper_bound | decimal(18,6) | NULL |
| text_range | nvarchar(200) | NULL |
| warning_lower | decimal(18,6) | NULL |
| warning_upper | decimal(18,6) | NULL |
| effective_from datetime2(3) NOT NULL |  |  |
| effective_to datetime2(3) NULL |  |  |

#### 4.46 lab_results

Purpose: Version header for a laboratory result.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| service_request_id | uniqueidentifier | NOT NULL REFERENCES service_requests(id) |
| specimen_id | uniqueidentifier | NULL REFERENCES specimens(id) |
| version_number | int | NOT NULL |
| status | varchar(16) | NOT NULL |
| supersedes_lab_result_id | uniqueidentifier | NULL REFERENCES lab_results(id) |
| result_source | varchar(24) | NOT NULL |
| verified_by_staff_id | uniqueidentifier | NULL REFERENCES staff(id) |
| verified_at datetime2(3) NULL |  |  |
| finalized_at datetime2(3) NULL |  |  |
| created_at datetime2(3) NOT NULL |  |  |
| raw_message_reference | nvarchar(200) | NULL |

Rules / Constraints:

• UNIQUE(service_request_id, version_number).

• FINAL rows are immutable; correction creates a new version.

#### 4.47 lab_result_values

Purpose: One analyte value in one lab-result version.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| lab_result_id | uniqueidentifier | NOT NULL REFERENCES lab_results(id) |
| analyte_id | uniqueidentifier | NOT NULL REFERENCES analytes(id) |
| numeric_value | decimal(18,6) | NULL |
| text_value | nvarchar(500) | NULL |
| unit_snapshot | nvarchar(40) | NULL |
| reference_range_snapshot | nvarchar(200) | NULL |
| abnormal_flag | varchar(16) | NOT NULL DEFAULT 'UNKNOWN' |
| instrument_code | nvarchar(50) | NULL |
| measured_at datetime2(3) NULL |  |  |

Rules / Constraints:

• UNIQUE(lab_result_id, analyte_id).

#### 4.48 imaging_studies

Purpose: Ultrasound/X-ray/ECG study metadata.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| service_request_id | uniqueidentifier | NOT NULL REFERENCES service_requests(id) |
| modality | varchar(16) | NOT NULL |
| external_study_uid | nvarchar(200) | NULL |
| device_identifier | nvarchar(100) | NULL |
| study_at datetime2(3) NULL |  |  |
| metadata_json | nvarchar(max) | NULL |
| created_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• Large binaries stay in object storage/PACS.

#### 4.49 diagnostic_reports

Purpose: Versioned ultrasound/X-ray/ECG/generic diagnostic report.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| service_request_id | uniqueidentifier | NOT NULL REFERENCES service_requests(id) |
| imaging_study_id | uniqueidentifier | NULL REFERENCES imaging_studies(id) |
| document_template_version_id | uniqueidentifier | NULL REFERENCES document_template_versions(id) |
| version_number | int | NOT NULL |
| status | varchar(16) | NOT NULL |
| findings | nvarchar(max) | NULL |
| conclusion | nvarchar(max) | NULL |
| structured_data_json | nvarchar(max) | NULL |
| supersedes_report_id | uniqueidentifier | NULL REFERENCES diagnostic_reports(id) |
| author_staff_id | uniqueidentifier | NOT NULL REFERENCES staff(id) |
| verified_by_staff_id | uniqueidentifier | NULL REFERENCES staff(id) |
| finalized_at datetime2(3) NULL |  |  |
| created_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• UNIQUE(service_request_id, version_number).

• FINAL rows are immutable; correction creates a new version.

#### 4.50 file_attachments

Purpose: Metadata for files/images/import sources; bytes live outside SQL.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| patient_id | uniqueidentifier | NULL REFERENCES patients(id) |
| encounter_id | uniqueidentifier | NULL REFERENCES encounters(id) |
| entity_type | varchar(40) | NOT NULL |
| entity_id | uniqueidentifier | NOT NULL |
| document_type | varchar(40) | NOT NULL |
| storage_provider | varchar(24) | NOT NULL |
| storage_key | nvarchar(500) | NOT NULL |
| file_name | nvarchar(255) | NOT NULL |
| mime_type | varchar(100) | NOT NULL |
| size_bytes | bigint | NOT NULL |
| sha256 | char(64) | NULL |
| created_by_user_id | uniqueidentifier | NULL REFERENCES users(id) |
| created_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• Do not store file bytes in relational image/blob columns.

#### 4.51 prescriptions

Purpose: Versioned prescription header.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| prescription_number | varchar(40) | NOT NULL UNIQUE |
| encounter_id | uniqueidentifier | NOT NULL REFERENCES encounters(id) |
| patient_id | uniqueidentifier | NOT NULL REFERENCES patients(id) |
| prescriber_staff_id | uniqueidentifier | NOT NULL REFERENCES staff(id) |
| version_number | int | NOT NULL |
| status | varchar(16) | NOT NULL |
| supersedes_prescription_id | uniqueidentifier | NULL REFERENCES prescriptions(id) |
| issued_at datetime2(3) NULL |  |  |
| created_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• ISSUED is immutable; correction creates a new version.

#### 4.52 prescription_items

Purpose: Prescription medication lines.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| prescription_id | uniqueidentifier | NOT NULL REFERENCES prescriptions(id) |
| medication_id | uniqueidentifier | NULL REFERENCES medications(id) |
| medication_name_snapshot | nvarchar(300) | NOT NULL |
| strength_snapshot | nvarchar(100) | NULL |
| dose | nvarchar(100) | NOT NULL |
| route | nvarchar(100) | NULL |
| frequency | nvarchar(100) | NOT NULL |
| duration_days | int | NULL |
| quantity | decimal(12,3) | NULL |
| instructions | nvarchar(500) | NULL |
| display_order | int | NOT NULL |

#### 4.53 users

Purpose: Authentication principal separated from staff/patient data.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| principal_type | varchar(16) | NOT NULL |
| staff_id | uniqueidentifier | NULL REFERENCES staff(id) |
| patient_id | uniqueidentifier | NULL REFERENCES patients(id) |
| auth_provider | varchar(32) | NOT NULL |
| auth_subject | nvarchar(200) | NOT NULL |
| status | varchar(16) | NOT NULL |
| last_login_at datetime2(3) NULL |  |  |
| created_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• UNIQUE(auth_provider, auth_subject).

• principal_type: STAFF | PATIENT.

• STAFF requires staff_id NOT NULL and patient_id NULL; PATIENT requires patient_id NOT NULL and staff_id NULL. Enforce with CHECK constraint.

• Never store plaintext passwords.

#### 4.54 roles

Purpose: Application roles.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| role_code | varchar(40) | NOT NULL UNIQUE |
| role_name | nvarchar(150) | NOT NULL |
| is_active | bit | NOT NULL DEFAULT 1 |

Rules / Constraints:

• Seed at minimum FRONT_DESK, DOCTOR, CLINIC_MANAGER, SYSTEM_ADMIN and required diagnostic-staff roles.

• CLINIC_MANAGER and SYSTEM_ADMIN are distinct roles.

• No CASHIER or DIAGNOSTIC_BILLING role is required by baseline.

• User-facing name for CLINIC_MANAGER is Quản lý bệnh viện. Do not create a duplicate role only for the label change; FRONT_DESK cannot import EMPLOYEE_LIST. SYSTEM_ADMIN alone does not grant that permission.

#### 4.55 permissions

Purpose: Fine-grained permissions.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| permission_code | varchar(80) | NOT NULL UNIQUE |
| module | varchar(40) | NOT NULL |
| description | nvarchar(300) | NULL |

#### 4.56 user_roles

Purpose: Role assignment with optional department/room scope.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| user_id | uniqueidentifier | NOT NULL REFERENCES users(id) |
| role_id | uniqueidentifier | NOT NULL REFERENCES roles(id) |
| department_id | uniqueidentifier | NULL REFERENCES departments(id) |
| room_id | uniqueidentifier | NULL REFERENCES rooms(id) |
| valid_from datetime2(3) NOT NULL |  |  |
| valid_to datetime2(3) NULL |  |  |

#### 4.57 role_permissions

Purpose: Role to permission mapping.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| role_id | uniqueidentifier | NOT NULL REFERENCES roles(id) |
| permission_id | uniqueidentifier | NOT NULL REFERENCES permissions(id) |

Rules / Constraints:

• UNIQUE(role_id, permission_id).

#### 4.58 notifications

Purpose: SMS-only notification delivery state.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| patient_id | uniqueidentifier | NULL REFERENCES patients(id) |
| appointment_id | uniqueidentifier | NULL REFERENCES appointments(id) |
| encounter_id | uniqueidentifier | NULL REFERENCES encounters(id) |
| notification_type | varchar(40) | NOT NULL |
| recipient_phone | nvarchar(30) | NOT NULL |
| sms_template_code | varchar(60) | NOT NULL |
| payload_json | nvarchar(max) | NOT NULL |
| provider_code | varchar(40) | NULL |
| provider_message_id | nvarchar(150) | NULL |
| idempotency_key | varchar(120) | NOT NULL |
| status | varchar(20) | NOT NULL |
| attempt_count | int | NOT NULL DEFAULT 0 |
| scheduled_at datetime2(3) NULL |  |  |
| sent_at datetime2(3) NULL |  |  |
| delivered_at datetime2(3) NULL |  |  |
| failed_at datetime2(3) NULL |  |  |
| next_retry_at datetime2(3) NULL |  |  |
| error_code | nvarchar(100) | NULL |
| error_message | nvarchar(1000) | NULL |
| created_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• UNIQUE(idempotency_key).

• status: QUEUED | SENT | DELIVERED | FAILED | RETRYING.

• recipient_phone is a snapshot of the current patients.phone at notification creation time.

• No phone verification/OTP state is required before sending SMS in the current baseline.

• Do not place diagnosis or detailed sensitive results in SMS payload.

Indexes:

• IX_notifications_status(status, next_retry_at)

• IX_notifications_patient(patient_id, created_at DESC)

#### 4.59 notification_attempts

Purpose: One provider attempt/retry for an SMS notification.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| notification_id | uniqueidentifier | NOT NULL REFERENCES notifications(id) |
| attempt_number | int | NOT NULL |
| provider_code | varchar(40) | NOT NULL |
| provider_message_id | nvarchar(150) | NULL |
| status | varchar(20) | NOT NULL |
| request_reference | nvarchar(500) | NULL |
| response_reference | nvarchar(500) | NULL |
| error_code | nvarchar(100) | NULL |
| error_message | nvarchar(1000) | NULL |
| attempted_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• UNIQUE(notification_id, attempt_number).

#### 4.60 audit_logs

Purpose: Append-only security/business audit trail.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| occurred_at datetime2(3) NOT NULL |  |  |
| actor_user_id | uniqueidentifier | NULL REFERENCES users(id) |
| action | varchar(80) | NOT NULL |
| entity_type | varchar(50) | NOT NULL |
| entity_id | nvarchar(100) | NOT NULL |
| patient_id | uniqueidentifier | NULL REFERENCES patients(id) |
| encounter_id | uniqueidentifier | NULL REFERENCES encounters(id) |
| health_check_record_id | uniqueidentifier | NULL REFERENCES health_check_records(id) |
| correlation_id | uniqueidentifier | NULL |
| reason | nvarchar(500) | NULL |
| before_json | nvarchar(max) | NULL |
| after_json | nvarchar(max) | NULL |
| ip_address | varchar(64) | NULL |
| user_agent | nvarchar(500) | NULL |

Rules / Constraints:

• Append-only.

• Never log passwords, tokens or secrets.

• Audit SHS scan, reprint, invalidation, import confirmation and price changes.

#### 4.61 integration_endpoints

Purpose: External system/provider configuration.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| endpoint_code | varchar(40) | NOT NULL UNIQUE |
| integration_type | varchar(32) | NOT NULL |
| endpoint_name | nvarchar(200) | NOT NULL |
| base_url | nvarchar(500) | NULL |
| credential_reference | nvarchar(300) | NULL |
| config_json | nvarchar(max) | NULL |
| is_active | bit | NOT NULL DEFAULT 1 |
| created_at datetime2(3) NOT NULL |  |  |
| updated_at datetime2(3) NOT NULL |  |  |

Rules / Constraints:

• credential_reference points to protected secret storage.

#### 4.62 external_code_mappings

Purpose: External-to-internal code mappings.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| integration_endpoint_id | uniqueidentifier | NOT NULL REFERENCES integration_endpoints(id) |
| mapping_type | varchar(32) | NOT NULL |
| external_code | nvarchar(100) | NOT NULL |
| internal_entity_type | varchar(32) | NOT NULL |
| internal_entity_id | uniqueidentifier | NOT NULL |
| valid_from datetime2(3) NOT NULL |  |  |
| valid_to datetime2(3) NULL |  |  |

Indexes:

• IX_external_code_mappings_lookup(integration_endpoint_id, mapping_type, external_code, valid_to)

#### 4.63 integration_messages

Purpose: Reliable integration inbox/outbox processing log.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| integration_endpoint_id | uniqueidentifier | NOT NULL REFERENCES integration_endpoints(id) |
| direction | varchar(8) | NOT NULL |
| message_type | varchar(40) | NOT NULL |
| external_message_id | nvarchar(150) | NULL |
| correlation_id | uniqueidentifier | NOT NULL |
| status | varchar(16) | NOT NULL |
| payload_reference | nvarchar(500) | NULL |
| payload_hash | char(64) | NULL |
| error_message | nvarchar(2000) | NULL |
| received_at datetime2(3) NULL |  |  |
| processed_at datetime2(3) NULL |  |  |
| retry_count | int | NOT NULL DEFAULT 0 |

#### 4.64 idempotency_keys

Purpose: Prevent duplicate commands/import confirms/webhooks.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| scope | varchar(50) | NOT NULL |
| idempotency_key | varchar(120) | NOT NULL |
| request_hash | char(64) | NULL |
| result_entity_type | varchar(50) | NULL |
| result_entity_id | nvarchar(100) | NULL |
| status | varchar(16) | NOT NULL |
| created_at datetime2(3) NOT NULL |  |  |
| expires_at datetime2(3) NULL |  |  |

Rules / Constraints:

• UNIQUE(scope, idempotency_key).

#### 4.65 outbox_events

Purpose: Transactional outbox for reliable domain/integration events.

| Field / Item | Type / Definition | Constraints / Notes |
| --- | --- | --- |
| id | uniqueidentifier | NOT NULL PRIMARY KEY; application-generated UUID v7 (no database default) |
| aggregate_type | varchar(50) | NOT NULL |
| aggregate_id | nvarchar(100) | NOT NULL |
| event_type | varchar(80) | NOT NULL |
| payload_json | nvarchar(max) | NOT NULL |
| correlation_id | uniqueidentifier | NOT NULL |
| status | varchar(16) | NOT NULL DEFAULT 'PENDING' |
| created_at datetime2(3) NOT NULL |  |  |
| published_at datetime2(3) NULL |  |  |
| retry_count | int | NOT NULL DEFAULT 0 |

Indexes:

• IX_outbox_events_pending(status, created_at)

## 5. State Ownership

#### 5.1 Encounter

HEALTH_CHECK: PREPARED -> IN_PROGRESS -> COMPLETED

PREPARED/IN_PROGRESS -> CANCELED
Ordinary OUTPATIENT reception: IN_PROGRESS -> COMPLETED | CANCELED
The PREPARED -> IN_PROGRESS transition is explicit check-in, not print or scan.

#### 5.2 CLS Progress Read Model

CLS progress is not a persisted state machine. It is derived per Encounter from OrderRound, ServiceRequest, performing room, PaymentAuthorization, and Result status.

Authorized doctors can view the complete CLS checklist and available results for the Encounter. Front Desk sees only payment data; specialty staff see only their authorized worklist items.

#### 5.3 Service Request

ORDERED -> IN_PROGRESS -> COMPLETED

ORDERED/IN_PROGRESS -> CANCELED

#### 5.4 Payment Authorization

NOT_REQUIRED | PENDING | AUTHORIZED | WAIVED | REVOKED

#### 5.5 Payment

PENDING -> CONFIRMED

-> FAILED

CONFIRMED -> PARTIALLY_REFUNDED -> REFUNDED

#### 5.6 Result

DRAFT -> VERIFIED -> FINAL

FINAL -> CORRECTED as a new version

#### 5.7 Prescription

DRAFT -> ISSUED

ISSUED -> CORRECTED as a new version using supersedes_prescription_id; never overwrite an issued row. Authorized correction may occur after the related Encounter is COMPLETED and does not reopen the Encounter.

ISSUED -> CANCELED

#### 5.8 HealthCheckBatch

DRAFT -> READY -> IN_PROGRESS -> RESULT_PROCESSING -> FINALIZED -> CLOSED

Any non-closed operational state may transition to CANCELED according to permission/business policy.

Privileged repricing: FINALIZED/CLOSED -> RESULT_PROCESSING -> FINALIZED -> CLOSED as applicable. Reopening requires reason/audit, preserves the prior close/finalization history in audit, clears current finalized_at/closed_at, and does not change attendance or medical execution state.

#### 5.9 HealthCheckRecord

ACTIVE -> COMPLETED

-> CANCELED

-> REPLACED

SHS is immutable for the record. A replacement creates a new record/new SHS and links replaces_health_check_record_id.

## 6. Core Relationships

patients 1 -> N encounters

patients 1 -> N health_check_records

companies 1 -> N company_employees

companies 1 -> N health_check_batches

health_check_batches 1 -> N health_check_batch_services

health_check_batches 1 -> N health_check_batch_employees

health_check_batch_employees 1 -> 1 active health_check_records

company_employees N -> 0..1 patients before preparation; linked Patient is required before HealthCheckRecord/Encounter preparation completes

health_check_batch_employees N -> N health_check_batch_services through health_check_batch_employee_services

health_check_records N -> 1 patients

health_check_records 1 -> 1 encounters -> N generated_documents (resolved by encounter_id; no duplicate health_check_record_id FK on generated_documents)

document_templates 1 -> N document_template_versions

document_template_versions 1 -> N document_template_fields

services 1 -> N service_template_mappings

document_templates 1 -> N service_template_mappings

generated_documents 1 -> N generated_document_service_requests

service_requests 1 -> N generated_document_service_requests

health_check_batches 1 -> N health_check_import_jobs

health_check_import_jobs 1 -> N health_check_import_rows

health_check_batches 1 -> N encounters 1 -> N order_rounds

order_rounds 1 -> N service_requests

service_requests 1 -> 1 payment_authorizations

encounters 1 -> N invoices

invoices 1 -> N invoice_items

invoices 1 -> N payments

service_requests 1 -> N versioned lab/diagnostic results

## 7. Corporate Health Check Transaction Boundaries

#### 7.1 Create Company + Batch

## 1. Create/reuse Company.

## 2. Create HealthCheckBatch in DRAFT.

## 3. Select services from services into health_check_batch_services.

## 4. Snapshot retail/reference price into base_price_snapshot.

## 5. Save negotiated_unit_price.

## 6. Write audit/outbox.

## 7. Commit.

#### 7.2 Confirm Employee Import

## 1. Authorize CLINIC_MANAGER and lock the validated EMPLOYEE_LIST import job. FRONT_DESK cannot execute any import stage.

## 2. Validate roster fields, exact CCCD/employee-code matching and duplicates. Do not create Patient or Encounter during import.

## 3. Create/update CompanyEmployee roster with nullable patient_id; preserve an existing Patient link and flag identity conflicts instead of silently relinking.

## 4. Create/update HealthCheckBatchEmployee with employment snapshots and validated administrative_snapshot_json containing the full confirmed row. Do not duplicate patient_id.

## 5. Do not import blocking-error rows.

## 6. Write row-resolution/audit state.

## 7. Commit idempotently.

#### 7.3 Prepare Health-Check Visit and Print Master Form Before Check-In

## 1. For the selected employee and planned examination date, authorize preparation and validate adult eligibility. Exact lookup Patient by roster CCCD, create when absent and link CompanyEmployee. Resolve an existing valid prepared visit or create HEALTH_CHECK Encounter PREPARED and HealthCheckRecord with mandatory Patient/Encounter in an idempotent command.

## 2. Copy the batch employee administrative snapshot into HealthCheckRecord typed fields, fill missing optional data from Patient/batch context, resolve identity conflicts and generate immutable SHS once. Store planned_examination_date; actual_examination_date and check-in timestamps remain NULL.

## 3. Resolve the batch master template version shared by all employees in the batch.

## 4. Render only Mẫu số 03 with SHS text + Code 128 barcode. Do not create HealthCheckBatchEmployeeService, ServiceRequest or specialist documents yet.

## 5. Persist the prepared records and generated-document metadata with audit; record PRINT_REQUESTED only when the user requests printing. Do not place the employee on a doctor worklist or record attendance because a document was generated. Retry/reprint reuses the same visit/SHS.

7.3A Check-In at the Clinic or Company Examination Site

## 1. Authorized reception staff scans/enters the existing SHS and verifies identity. Scanning to read the record is not check-in.

## 2. Check record/code validity, actual examination date and adult eligibility; refuse canceled/replaced records or identity conflicts.

## 3. Atomically and idempotently change Encounter PREPARED to IN_PROGRESS, set checked_in_at/checked_in_by_user_id/started_at and HealthCheckRecord.actual_examination_date; update Encounter Assignment for the authorized reception flow.

## 4. Reuse Patient, Encounter, HealthCheckRecord, SHS and printed Form 03. Do not generate a new form unless needed for a recorded correction/reprint; retain the same SHS for the same visit.

## 5. At a COMPANY site, keep department ownership but allow room_id NULL and use the desk/zone label in the active assignment. Prepared employees who have not attended are excluded from actionable worklists and attended metrics.

#### 7.4 Doctor Selects Employee Services

## 1. Doctor opens the checked-in employee HealthCheckRecord/Encounter; PREPARED encounters cannot begin clinical execution.

## 2. Load HealthCheckBatchService rows belonging to the employee's batch as the only selectable scope.

## 3. Doctor selects the applicable subset; Front Desk and Patient have read-only visibility.

## 4. Validate every selected service belongs to the batch and reject any out-of-scope service.

## 5. In one transaction create OrderRound source_type=HEALTH_CHECK_PACKAGE with ordered_by_staff_id=Doctor, create one ServiceRequest per selected service, and create one HealthCheckBatchEmployeeService linked to each ServiceRequest.

## 6. Copy the current negotiated_unit_price to assignment and corporate ServiceRequest snapshots under the same concurrency policy used by repricing. Create PaymentAuthorization=NOT_REQUIRED. Unselected services create no assignment/ServiceRequest and package services create no individual invoice.

## 7. Build the specialist print plan from the batch-service template version frozen at READY; reprint uses the generated document original version. Current active mappings must not silently replace frozen versions.

## 8. Write audit/outbox and commit idempotently.

#### 7.5 Confirm Result Import

## 1. Lock validated RESULTS import job.

## 2. Resolve exact batch employee and batch service.

## 3. Reject service outside batch scope.

## 4. Reject overwrite of protected/final result without correction flow.

## 5. Persist imported clinical data into the shared result domain for resolved_service_request_id: LabResult/LabResultValue or DiagnosticReport. Corporate wrapper does not store medical result state/data.

## 6. Do not store line_amount; corporate totals are derived from billable rows and unit_price_snapshot.

## 7. Write audit/outbox.

## 8. Commit idempotently.

#### 7.6 Generate Corporate Reports + Finalize Batch

## 1. Read billable health_check_batch_employee_services for the batch.

## 2. Detail report = GROUP BY employee.

## 3. Service summary report = GROUP BY batch service.

## 4. Both reports derive Grand Total from SUM(unit_price_snapshot) over the same billable wrapper rows.

## 5. No reconciliation table/status is stored; consistency is enforced by query/service implementation and automated tests.

## 6. Batch may transition to FINALIZED according to normal authorization/business rules.

## 7. Commit with audit.

#### 7.7 Reprice One Batch Service for All Employees

## 1. Authorize the price-adjustment command and require a reason. Reopen FINALIZED/CLOSED under explicit permission before changing price; retain historical exported reports.

## 2. Serialize with concurrent employee service assignment for the same batch service. Within one transaction update negotiated_unit_price, all linked employee-service unit_price_snapshot values (including completed/billable rows), and the linked HEALTH_CHECK_PACKAGE ServiceRequest unit_price_snapshot values.

## 3. Preserve base_price_snapshot, retail service_prices, other batches, billable flags and medical results. New assignments read the new common price. No employee-specific price mutation is allowed.

## 4. Write audit with previous/new values, actor, time and reason, then commit atomically. On failure roll back every price copy.

## 5. Recompute current detail/summary reports over the same billable dataset: SUM(unit_price_snapshot) = billable quantity × negotiated_unit_price. Re-finalize the batch under the existing authorization flow when required.

## 8. Outpatient Transaction Boundaries

#### 8.1 Front Desk Reception

Front Desk resolves/creates Patient, creates Encounter and Encounter Assignment, creates the initial consultation invoice, and collects payment/authorization before Doctor Worklist.

#### 8.2 Doctor Diagnostic Order

Doctor creates OrderRound/ServiceRequests. System snapshots service data/price and creates required payment authorization plus receivable. Front Desk performs payment.

#### 8.3 Diagnostic Payment

Payment confirmation authorizes eligible ServiceRequests. Authorized requests become actionable in the relevant specialty worklist; no Journey state is updated.

#### 8.4 Final Result

Result is versioned and linked to the ServiceRequest. When a service is performed and its required result is recorded, ServiceRequest becomes COMPLETED. When all required requests are complete, the Encounter becomes ready for doctor review.

#### 8.5 Complete Encounter

Doctor finalizes diagnosis/prescription/follow-up and completes the Encounter.

## 9. Index Strategy

Patient lookup:

• patients(patient_code)

• patients(full_name_normalized, date_of_birth)

Corporate/company:

• companies(company_code)

• companies(tax_code) filtered non-null

• company_employees(company_id, employee_code)

• health_check_batches(company_id, status, start_date)

• health_check_batch_employees(health_check_batch_id, status)

• health_check_records(shs_code) UNIQUE

Corporate reports:

• health_check_batch_employee_services(health_check_batch_employee_id, billable)

• health_check_batch_employee_services(health_check_batch_service_id, billable)

These two indexes support both employee-detail and service-summary reports from the same source rows.

Template rendering:

• document_template_fields(document_template_version_id, display_order)

• service_template_mappings(service_id, is_active)

• service_template_mappings(document_template_id, is_active)

• generated_documents(encounter_id, print_sequence, status)

• generated_document_service_requests(generated_document_id)

• generated_document_service_requests(service_request_id)

Doctor / CLS progress:

• service_requests(encounter/order round, performing_room_id, status, ordered_at)

• encounter_assignments(doctor_staff_id, ended_at)

• encounters(status, started_at)

Diagnostics:

• service_requests(performing_department_id, performing_room_id, status, priority, ordered_at)

• payment_authorizations(status, service_request_id)

• lab_results(service_request_id, version_number)

• diagnostic_reports(service_request_id, version_number)

SMS:

• notifications(status, next_retry_at)

• notifications(patient_id, created_at DESC)

Reliability:

• integration_messages(integration_endpoint_id, status, received_at)

• idempotency_keys(scope, idempotency_key)

• outbox_events(status, created_at)

10. Implementation Priority

Phase A — Shared Foundation

• patients

• companies / company_employees

• services / service_prices / lab_panels / analytes / lab_panel_items / analyte_reference_ranges

• departments / rooms / staff / staff_department_assignments

• users / roles / permissions

• document_templates / document_template_versions / document_template_fields / service_template_mappings

• file_attachments

• audit_logs / idempotency_keys

Phase B — Encounter + Ordering Core

• appointments

• encounters / encounter_assignments

• CLS progress is a read model from service_requests + results; no separate persisted progress state is required.

• vital_signs / clinical_notes / encounter_diagnoses

• order_rounds / service_requests / payment_authorizations

Phase C — Corporate Health Check

• health_check_batches / health_check_batch_services

• health_check_batch_employees

• health_check_records + SHS + administrative snapshot

• PREPARED Encounter + manager-only roster import + preparation/check-in idempotency + clinic/company examination-site routing

• Doctor-selected health_check_batch_employee_services

• health_check_import_jobs / health_check_import_rows

• generated_documents / generated_document_service_requests

• bulk Mẫu số 03 rendering + SHS barcode

• specialist print plan after Doctor selection

• MVP employee-detail and service-summary reports; detailed clinical-result reporting deferred

Phase D — Outpatient Billing + Diagnostic Results

• invoices / invoice_items / invoice_adjustments / payments

• specimens / specimen_service_requests

• lab_results / lab_result_values

• imaging_studies / diagnostic_reports

Phase E — Prescription, SMS & Portal

• medications / prescriptions / prescription_items

• notifications / notification_attempts

• Patient Portal authorization

Phase F — Integration Reliability

• integration_endpoints

• external_code_mappings

• integration_messages

• outbox_events

11. Removed / Replaced From v1.1

Removed as obsolete target concepts:

• Separate Cashier / Diagnostic Billing role or table.

• BILLING department as a required department type.

• Multi-channel outbound notification model; baseline table is SMS-specific.

• Queue/QueueTicket/STT target tables.

• Pharmacy inventory/dispensing target tables.

• Per-document health-check business code/barcode table.

• Old report_templates / report_template_versions naming.

Replaced:

• report_templates -> document_templates.

• report_template_versions -> document_template_versions.

• Split template responsibilities into document_template_fields for placeholders and service_template_mappings for Service → logical template mapping.

• generated_documents stores shared Encounter document instances; generated_document_service_requests stores the many-to-many ServiceRequest membership of specialist forms.

• Added the full corporate health-check aggregate and import/reporting model.

• Corporate price moved to health_check_batch_services snapshot instead of reusing retail service_prices.

12. DDL Freeze Checks

Before generating production DDL, use these baseline decisions:

• SQL types in this document remain SQL Server-compatible until the implementation stack explicitly chooses another DB engine.

• patient_code, encounter_code and shs_code are opaque unique business codes; final display format is an implementation/configuration concern, not a relational-model blocker.

• Employee import is CLINIC_MANAGER-only and does not create Patient. CompanyEmployee.patient_id is nullable until preparation. Preparation before printing exact-lookups/creates Patient and provisions a PREPARED Encounter plus HealthCheckRecord/SHS; check-in reuses them.

• HealthCheckBatchService is batch scope only. Front Desk/Patient cannot assign employee services; Doctor selects a per-employee subset in Clinical Workspace.

• HealthCheckBatchEmployeeService and its ServiceRequest are created together only after Doctor confirmation; service_request_id is NOT NULL and selected doctor is derived from OrderRound.ordered_by_staff_id.

• Every new HEALTH_CHECK visit receives one administrative snapshot at preparation, before or at check-in. Name, DOB, sex and CCCD are mandatory; other fields follow business nullability. Reprint uses the stored snapshot. A new print/check-in command does not itself mean a new visit.

• READY freezes normal scope/template edits. Uniform batch repricing remains available with permission/reason/audit and atomically updates all related assignment/ServiceRequest price copies; finalized/closed batches must be reopened under permission before adjustment.

• Result-import mapping is adapter/schema driven per service/result type and always resolves to shared ServiceRequest/result tables.

• Patient Portal authentication is outside the database-model freeze for the current corporate-first phase.

• LIS/analyzer/PACS integrations are optional adapters; manual entry/import remains the baseline fallback and the core schema does not depend on vendor capability.

• encounters persist reason + priority; current room/doctor is owned by encounter_assignments, with at most one active assignment per Encounter.

• appointments.source_encounter_id is optional and traces follow-up appointments back to the creating Encounter.

• vital_signs stores encounter_id only; HealthCheckRecord context is resolved through the one-to-one encounter relation.

• clinical_notes MVP uses DRAFT/FINAL + audit; explicit note-version chains are deferred.

• users enforces exactly one principal relation according to principal_type (STAFF or PATIENT).

• LAB services may reference one services.lab_panel_id in MVP; lab_panel_items expands the panel into analytes.

• Corporate HEALTH_CHECK_PACKAGE OrderRounds are Doctor-confirmed, require ordered_by_staff_id, create PaymentAuthorization=NOT_REQUIRED, and do not create individual invoices for package services.

• Corporate result-detail/data-mart reporting is explicitly deferred; core clinical results remain in LabResult/LabResultValue/DiagnosticReport.

• Corporate sites and pre-printed visits are supported; neither implies offline synchronization. Confirm connectivity requirements before deploying an examination site.

13. Contracts Remaining Open for Their Respective Features

The following are not decided by the preparation/import/pricing changes: required-work and override-completion policy; RESULTS-import target state and verify/finalize permissions; record replacement after clinical results exist; exact clinical-result versions/payload retained for reprint; partial-refund allocation; offline capture/synchronization. Resolve these contracts before implementing those behaviors. Do not infer medical approval from employee-import authority.
