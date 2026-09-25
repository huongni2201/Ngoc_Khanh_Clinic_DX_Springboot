# Frontend Terminology Inventory

Audit date: 2026-09-25

The frontend is a sibling repository at
`D:\workspace\Ngoc_Khanh_Clinic_DX\ngoc_khanh_clinic_frontend`. The terminology
cut-over has been applied there while preserving unrelated user changes.

## Inventory

| File / scope | Current term or symbol | Semantic responsibility | Target | Breaking? | Dependency / action |
| --- | --- | --- | --- | --- | --- |
| `src/modules/health-check-batches/**` | legacy physical module location | Corporate health-examination batch UI/API/types | canonical public entry `@/modules/health-examinations`; symbols `HealthExaminationBatch*` | Compatibility | The directory move was denied by the existing Windows ACL; canonical entry points and all symbols/imports are active. |
| `src/app/enterprises/[enterpriseId]/health-examination-batches/[batchId]/page.tsx` | new canonical route | Batch detail route composition | `/health-examination-batches` | Yes | Added alongside the legacy route for a safe cut-over. |
| `src/app/enterprises/[enterpriseId]/exam-batches/[batchId]/page.tsx` | legacy route | Existing bookmarked route | compatibility route | Compatibility | Kept temporarily; all new links/tests target the canonical route. |
| `src/modules/health-check-batches/types/index.ts` | `ExamBatch`, `ExamBatchItem`, `CreateExamBatchRequest` | Batch and batch-service UI models | `HealthExaminationBatch`, corresponding request names | Yes | Renamed consumers and fixtures. |
| `src/modules/health-check-batches/api/index.ts` | `examBatchesStore`, `fetchExamBatch*`, `createExamBatch` | In-memory module API/mock adapter | health-examination batch names | Yes | Public symbols and query keys use the canonical names. |
| `src/modules/companies/components/*health-examination-batch*` and tests | `HealthExaminationBatch*` | Company page batch workflow | canonical health-examination names | Yes | Renamed files, symbols, imports and route links. |
| `src/modules/patients/types/encounter.ts` | `LabResultDetail`, `ImagingResultDetail` | Full laboratory report detail and X-ray imaging report detail | `LaboratoryReportDetail`; `DiagnosticReportDetail` | Yes | Semantic mapping preserved report vs. diagnostic-imaging responsibilities. |
| `src/modules/patients/components/encounter/*` | `CLS` labels and result cards | Laboratory, diagnostic imaging and umbrella diagnostic-services UI | Laboratory / Diagnostic Imaging / Diagnostic Services | UI copy | Removed technical `CLS` labels. |
| `src/modules/appointments/{api,types,schemas,components}` | `doctorId`, `doctorName` | Assigned clinical professional | `physicianId`, `physicianName` | Yes | Updated types, filter params, fixtures, forms and tests. |
| `src/modules/reception/{api,types,components}` | `doctorId`, `doctorName` | Assigned clinical professional | `physicianId`, `physicianName` | Yes | Preserved Reception function while renaming actor fields. |
| `src/modules/reception/**` | `Reception*` | Reception work area | preserve `Reception*`; role copy is Receptionist | No / UI | `Reception` is the department/function, `Receptionist` is the actor. |
| `src/app/**`, `PROJECT_RULES.md`, `docs/architecture/FRONTEND_ARCHITECTURE.md` | `/health-check*`, `HealthCheckBatch`, Health Check | Routes, architecture and domain documentation | `/health-examinations*`, `HealthExaminationBatch`, Health Examination | Yes for routes/docs | Update source docs and route links together. |

## Exclusions / false positives

- `clsx` in `pnpm-lock.yaml` and UI utility code is a third-party package
  name, not the Vietnamese diagnostic-services concept; it must not be
  renamed by the terminology guard.
- Generic English `examination` in appointment and clinical UI is not itself
  deprecated.
- No active frontend `FRONT_DESK` role literal, `DoctorWorklist`, or ECG
  imaging model was found under `src/` during this audit.

## Required frontend follow-up

1. Move the legacy physical `src/modules/health-check-batches` directory when
   its Windows ACL permits it; the canonical public entry point is already
   available.
2. Run the frontend terminology guard, lint and build in CI.
3. `pnpm typecheck` and `pnpm test` have been run successfully after the
   cut-over; the final lint/build check remains.
