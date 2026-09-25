# Ngọc Khánh Clinic Medical Terminology

This glossary is the canonical terminology map for the current refactor. It
separates the business concept from its representation in UI, Java, API and
SQL. User-facing Vietnamese labels for legal/official forms remain unchanged;
technical identifiers use the canonical English terms below.

| Concept ID | Canonical English | UI English | Backend Domain | API | Database | Allowed Alias | Deprecated Terms | Source / Rationale |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ACT-REC | Receptionist | Receptionist | `Receptionist` / `RECEPTIONIST` | `RECEPTIONIST` | `RECEPTIONIST` role code | Reception | Front Desk, `FRONT_DESK`, `FrontDesk` | Business actor; Reception is the department. |
| DEP-REC | Reception | Reception | `RECEPTION` department | `RECEPTION` | `RECEPTION` department code | — | `RECEPTIONIST` as department | Actor and functional area are different concepts. |
| ACT-PHY | Physician | Physician | `Physician`, `physicianId` | `physicianId` | `physician_staff_id` when the staff member is specifically a physician | Doctor in official/legal wording | `DoctorWorklist`, Doctor Worklist, actor-only `Doctor` in technical names | Canonical clinical-professional term; generic `staff_id` remains generic. |
| PAT | Patient | Patient | `Patient` | `patient` | `patients` | — | Customer | Existing domain term. |
| ENC | Encounter | Encounter | `Encounter` | `encounter` | `encounters` | — | VisitSession, Medical Exam Session | Existing encounter-centric workflow. |
| SR | Service Request | Service Request / Ordered Services | `ServiceRequest` | `serviceRequest` | `service_requests` | Diagnostic Services for the UI umbrella | `CLSOrder`, DiagnosticOrderItem, ExamRequestItem | Explicit order concept; derived progress remains outside the model. |
| HE | Health Examination | Health Examination | `HealthExamination*` | `healthExamination` | `health_examination_*` | SHS remains a documented code alias | Health Check, `HealthCheck*`, `health_check*`, `health-check*` | Plan-mandated P0 vertical rename. |
| HE-BATCH | Health Examination Batch | Health Examination Batch | `HealthExaminationBatch` | `healthExaminationBatch` | `health_examination_batches` | — | `HealthCheckBatch` | Corporate examination campaign aggregate. |
| HE-REC | Health Examination Record | Health Examination Record | `HealthExaminationRecord` | `healthExaminationRecord` | `health_examination_records` | SHS | `HealthCheckRecord` | Visit-specific administrative snapshot and SHS owner. |
| DIAG | Diagnostic Services | Diagnostic Services | umbrella/read-model term only | `diagnosticServices` | no parallel workflow table | — | `CLS`, `Cls`, `cls` as a technical domain concept | Umbrella only; use the specific service domain below. |
| LAB | Laboratory Testing | Laboratory | `Laboratory*`, `LabResult` only where the existing semantic contract is exact | `laboratory` | existing `lab_*` tables remain until a semantic schema decision | Lab in user-facing Vietnamese context | `LabWorklist`, `LabCatalog`, `LabTechnician` as unreviewed technical names | Existing records already distinguish panels, analytes, specimens and results. |
| LAB-PANEL | Laboratory Panel | Laboratory Panel | `LaboratoryPanel` target; current `LabPanelRecord` is persistence-only | `laboratoryPanel` | `lab_panels` pending deliberate DB cut-over | Lab Panel | — | A panel groups analytes; no blind result rename. |
| LAB-OBS | Laboratory Result / Observation | Laboratory Result | measured value/report distinction to be confirmed | `laboratoryResult` or `observation` by semantic responsibility | `lab_results` / `lab_result_values` | — | generic `Result`, `DiagnosticResult` | `LabResultRecord` is a full result record; `LabResultValueRecord` is a value row. |
| IMG | Diagnostic Imaging | Diagnostic Imaging | `ImagingStudy`, `DiagnosticReport` | `imagingStudy`, `diagnosticReport` | `imaging_studies`, `diagnostic_reports` | X-ray, Ultrasound | ImageResult, ImagingResult, XrayResult | Aligns with the existing study/report split. |
| FD | Functional Diagnostics | Functional Diagnostics | functional-diagnostics concepts | `functionalDiagnostics` | no separate queue-state table | ECG | ECG Imaging, ECG Imaging Study | ECG is not Diagnostic Imaging. |
| ECG | Electrocardiography / Electrocardiogram / ECG Interpretation | ECG | procedure, record and interpretation are distinct concepts | `electrocardiography`, `electrocardiogram`, `ecgInterpretation` | preserve existing schema until an ECG model exists | ECG | ECG Imaging | Use only the minimum concept required by the MVP. |
| RX | Prescription | Prescription | `Prescription`, `PrescriptionItem` | `prescription` | `prescriptions`, `prescription_items` | Medication | MedicationRequest as an internal replacement | Keep the NKC business concept; FHIR mapping is external only. |
| APT | Appointment | Appointment / Follow-up Appointment | `Appointment` | `appointment` | `appointments` | Follow-up Appointment | Revisit, Return Visit when scheduling is meant | Appointment is scheduling; Encounter is an actual interaction. |
| DOC-HE | Health Examination Form | Health Examination Form | `isMasterHealthExaminationForm` | `healthExaminationForm` | `is_master_health_examination_form` | Mẫu số 03 (official form label) | `isMasterHealthCheckForm`, `health_check_form` | Technical key is language-neutral; legal label remains Vietnamese. |

## Decisions and preserved representations

- `healthcheck` remains the existing bounded-context package name in this
  repository because it is an accepted module identifier in the backend
  architecture. The business/domain symbols and SQL identifiers are renamed
  to `HealthExamination`; moving the entire module would be a separate module
  boundary change, not a terminology-only change.
- `lab_*` is preserved in the current database until the result/panel schema
  has an explicit semantic migration. The Java persistence records are not
  treated as API/domain models.
- `doctor_staff_id` and `ordered_by_staff_id` are reviewed separately from
  generic `staff_id`: they identify physician assignments in the current
  schema, but changing them requires synchronized baseline and migration
  contract updates.
- `CCCD` remains the business/document label. The technical patient identity
  name remains `identification_number` per ADR-0003.
- `SHS`, `ECG`, `PACS`, `RIS`, `FHIR`, `LOINC` and `DICOM` are allowed domain
  references/aliases; they do not authorize adding new integration
  infrastructure.
