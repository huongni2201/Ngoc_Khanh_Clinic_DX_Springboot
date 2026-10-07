# Examination details (Excel) and payment report (Word)

Owner decision 2026-10-07. Plan: `docs/plans/20261007-examination-detail-excel-and-report-word.md`.
Base path: `/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}`.
Responses use the common envelope `{result, code, message, data}`; failures use `result: "NG"`.
An Organization/Batch pair that does not match, or a soft-deleted batch, is 404.

## Permissions

Flyway V002 adds three permissions and grants them to CLINIC_MANAGER (and to the local ADMIN role by the local seed).
Permissions are a login snapshot: sign in again after the migration.

| Permission | Routes |
|---|---|
| `HEALTH_EXAMINATION_SERVICE_READ` | `GET /examination-details`, `GET /examination-details/summary`, `GET /examination-details/export` |
| `HEALTH_EXAMINATION_SERVICE_RECONCILE` | `POST /examination-details/imports` |
| `HEALTH_EXAMINATION_REPORT_READ` | `GET /reports/payment-summary`, `GET /reports/payment-summary/docx` |

## Routes

| Route | Success | Notes |
|---|---|---|
| `GET /examination-details` | 200 page of rows | `page`, `size`, `sortKey` (`id`, `participantCode`, `fullName`, `examinationDate`), `sortBy`, `searchKey` (code, name, department; never the CCCD), `rosterStatus` (default ACTIVE), `attendanceStatus`, `reconciliationStatus`. `no-store`. |
| `GET /examination-details/summary` | 200 | `registered`, `unconfirmed`, `attended`, `absent`, `reconciled`, `pendingReconciliation`; ACTIVE roster only. |
| `GET /examination-details/export` | 200 `.xlsx` | The whole ACTIVE roster (not the UI filter). It is also the import template. `filename="chi-tiet-kham-<batchCode>.xlsx"`, `no-store`. |
| `POST /examination-details/imports` | 201 | multipart `file` (`.xlsx`, <= 5 MiB) and required header `Idempotency-Key` (UUID). |
| `GET /reports/payment-summary` | 200 | One line per (batch service, price snapshot). `provisional` is true until the batch is FINALIZED/CLOSED. |
| `GET /reports/payment-summary/docx` | 200 `.docx` | `filename="bao-cao-thanh-toan-<batchCode>.docx"`, `no-store`. Generated on demand, never stored. Same figures as the JSON report, no per-person data. |

List row: `id`, `participantCode`, `fullName`, `identificationNumberMasked`, `departmentName`, `positionName`,
`examinationDate`, `attendanceStatus`, `actualExaminationDate`, `reconciliationStatus`,
`performedBatchServiceIds`, `rowVersion`. The columns of the matrix are `services` of the batch detail.
The backend adds no screen status; the frontend derives the labels from attendance + reconciliation.

Import result: `importJobId`, `batchId`, `totalRows`, `updatedParticipants`, `unchangedParticipants`,
`performedItems`, `completedAt`. A replay with the same key and the same file returns the stored 201 body.

Report: `batchId`, `batchCode`, `batchName`, `batchStatus`, `provisional`, `registeredCount`, `attendedCount`,
`reconciledCount`, `items[]` (`batchServiceId`, `serviceCode`, `serviceName`, `displayOrder`, `unitPrice`,
`examinedCount`, `amount`), `totalAmount`, `generatedAt`. Money is a decimal number. `amount = unitPrice x examinedCount`.

## Excel layout (templateVersion 1)

Sheet `ChiTietKham` (data) and `HuongDan` (instructions and metadata, informational only). Row 1 has the visible
labels, row 2 the hidden machine keys, data starts at row 3. Hidden columns A `participant_id` and B `row_version`
are required on import. Other participant columns are read-only context and are ignored on import. `actual_examination_date`
is optional text `yyyy-MM-dd`. One column `svc:<batch_service_id>` per batch service holds `X` or blank. The set of
`svc:` keys must equal the batch services exactly, otherwise the template is stale (400).
Limits: 10,000 data rows (the export refuses, with 400, a batch with more active Participants so every export can be imported again), 100 service columns, plus the OOXML package guard shared with the Participant import
(no XLS/XLSM/VBA, no encryption, no external links, bounded unzip, no formula evaluation).

## Reconciliation rules (overwrite by file)

- Only rows in the file are touched; Participants not in the file stay as they are.
- `X` marks the service performed and the Participant ATTENDED (actual date from the file, else the date already recorded,
  else the planned examination date). The actual date must be between the batch start date and today (business time,
  Asia/Ho_Chi_Minh); a blank actual date on a not-yet-attended Participant whose planned date is still in the future is
  rejected (400) and the row must state the actual date. Blank marks it not performed and keeps the row, its price snapshot and `service_request_id`.
- A new `X` takes the current negotiated price as the snapshot. A Participant with no X and no stored rows is skipped.
- A row identical to the stored state is `unchanged` and writes nothing.
- Each affected Participant becomes RECONCILED. The batch `rowVersion` does not change.
- All or nothing in one transaction: batch lock, state recheck, idempotency reserve or replay, row checks, writes, import job
  (`HEALTH_EXAMINATION_SERVICE_RECONCILIATION`, schema V001), audit `IMPORT_EXAMINATION_DETAILS`.
- Only a DRAFT or READY batch of an ACTIVE organization accepts an import.

Audit actions: `EXPORT_EXAMINATION_DETAILS`, `EXPORT_PAYMENT_REPORT`, `IMPORT_EXAMINATION_DETAILS`. Audit and logs hold
identifiers and counts only, never CCCD, names or cell values.

## Errors

| Case | HTTP | Message |
|---|---|---|
| Missing/empty/non-OOXML file, bad layout, invalid cell, bad date, duplicate `participant_id` | 400 | `Row 7: column "<service>" accepts only X or blank` |
| Stale template (service columns differ from the batch) | 400 | `The file does not match this batch; export it again` |
| `participant_id` not in this batch | 409 | `Row 5: participant is not in this batch` |
| Participant CANCELLED | 409 | `Row 5: participant is cancelled` |
| `row_version` differs | 409 | `Row 9: participant was changed after export; export again` |
| New X for a service that is no longer offered | 409 | `Row 12: a marked service is no longer offered in this batch` |
| Batch FINALIZED/CLOSED or organization INACTIVE | 409 | `Batch does not accept examination detail changes` |
| Idempotency key used for another file or still running | 409 | `Idempotency key was used for another request` |
| Not logged in / missing permission | 401 / 403 | Common envelope |
| Too large / wrong media type | 413 / 415 | Existing import handlers |

The first error is reported in a deterministic check order with the real Excel row number.
