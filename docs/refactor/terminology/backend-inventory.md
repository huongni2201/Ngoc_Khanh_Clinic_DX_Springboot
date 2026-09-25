# Backend Terminology Inventory

Audit date: 2026-09-25

## Active Java source

The `healthcheck` bounded context contains the complete Health Check vertical
slice. The following symbols are terminology-bearing and must move together:

| Current symbol family | Files / scope | Semantic responsibility | Canonical target | Breaking? |
| --- | --- | --- | --- | --- |
| `HealthCheckBatch` | `src/main/java/com/ngockhanh/clinic/healthcheck/domain/aggregate/HealthCheckBatch.java`, repository and tests | Corporate campaign aggregate | `HealthExaminationBatch` | Yes |
| `HealthCheckBatchService` | domain entity, persistence record, aggregate references and tests | Service in batch scope | `HealthExaminationBatchService` | Yes |
| `HealthCheckBatchEmployee` | domain aggregate, persistence record, repository and tests | Employee participation in one campaign | `HealthExaminationBatchEmployee` | Yes |
| `HealthCheckBatchEmployeeService` | domain entity, persistence record and tests | Physician-confirmed employee service assignment | `HealthExaminationBatchEmployeeService` | Yes |
| `HealthCheckRecord` | domain aggregate, persistence record, repository, order/audit records and tests | Visit-specific record and SHS owner | `HealthExaminationRecord` | Yes |
| `HealthCheckRecordStatus` | domain enum and tests | Record lifecycle | `HealthExaminationRecordStatus` | Yes |
| `HealthCheckImportJob` | domain aggregate, persistence record, repository and tests | Roster/result import lifecycle | `HealthExaminationImportJob` | Yes |
| `HealthCheckImportRow` | domain entity, persistence record and tests | One import row | `HealthExaminationImportRow` | Yes |
| `healthCheckBatch*`, `healthCheckImportJobId`, `healthCheckReasonSnapshot`, `replacesHealthCheckRecordId` | healthcheck records/value object | Java property representation of the same concepts | `healthExamination*`, `replacesHealthExaminationRecordId` | Yes |
| `healthCheckEligible` | `catalog/.../ServiceRecord.java` | Whether catalog service is available to the health-examination flow | `healthExaminationEligible` | Yes |
| `isMasterHealthCheckForm` | `document/.../DocumentTemplateRecord.java` | Mẫu số 03 master-form flag | `isMasterHealthExaminationForm` | Yes |
| `healthCheckRecordId` | `clinical/.../OrderRoundRecord.java`, `shared/.../AuditLogRecord.java` | Relation to examination record | `healthExaminationRecordId` | Yes |

## Current non-occurrences

- No Java `FRONT_DESK`/`FrontDesk` role implementation exists in this
  checkout.
- No Java `DoctorWorklist`, `DiagnosticResult`, `ImageResult`,
  `ImagingResult`, `XrayResult`, `CLSResult`, `CLSOrder` or ECG-imaging model
  exists.
- No REST controller or versioned API endpoint exists in `src/main/java`;
  `GlobalExceptionHandler` is the only HTTP advice class.
- No JavaScript/TypeScript frontend source exists inside this repository; the
  sibling frontend is tracked separately in the frontend inventory.

## Diagnostic semantic review

- `LabPanelRecord`, `LabResultRecord` and `LabResultValueRecord` are existing
  persistence records with distinct responsibilities. They are not blindly
  renamed: a panel groups analytes, a result is a result/report record, and a
  result value is an analyte row.
- `ImagingStudyRecord` and `DiagnosticReportRecord` already express the
  required study/report distinction and remain canonical.
- `SpecimenRecord` and `AnalyteRecord` already use canonical terminology.
- No ECG-specific implementation exists, so no imaging-to-functional-
  diagnostics move is required in this backend checkout.

