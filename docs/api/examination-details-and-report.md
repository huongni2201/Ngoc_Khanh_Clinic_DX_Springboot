# Examination details (Excel) and payment report (Word)

Owner decision 2026-10-07, amended by [ADR-0019](../adr/0019-agreement-session-assignment.md) (attendance now comes from session assignments). This document is the current HTTP contract.
Base path: `/api/v1/organizations/{organizationId}/agreements/{agreementId}`.
JSON responses use [the common envelope](../../PROJECT_RULES.md#envelope-errors-and-pagination);
failures use `result: "NG"`, and null `message`/`data` are omitted. Downloads return file bytes.
An Organization/Agreement pair that does not match, or a soft-deleted agreement, is 404.

## Permissions

Flyway V002 seeds the permissions below and grants them to CLINIC_MANAGER; the template download and the manual
record permissions go to every role that holds `HEALTH_EXAMINATION_SERVICE_RECONCILE` (today CLINIC_MANAGER).
The local seed additionally grants every permission to TEST;
it still requires a STAFF account and the matching stored endpoint permission.
Permissions are a login snapshot: sign in again after the migration.

| Permission | Routes |
|---|---|
| `HEALTH_EXAMINATION_SERVICE_READ` | `GET /examination-details` |
| `HEALTH_EXAMINATION_SERVICE_SUMMARY_READ` | `GET /examination-details/summary` |
| `HEALTH_EXAMINATION_SERVICE_EXPORT` | `GET /examination-details/export` |
| `HEALTH_EXAMINATION_SERVICE_RECONCILE` | `POST /examination-details/imports` |
| `HEALTH_EXAMINATION_SERVICE_TEMPLATE_DOWNLOAD` | `GET /examination-details/import-template` |
| `HEALTH_EXAMINATION_SERVICE_RECORD` | `PUT /examination-details/{participantId}` |
| `HEALTH_EXAMINATION_REPORT_READ` | `GET /reports/payment-summary` |
| `HEALTH_EXAMINATION_REPORT_EXPORT` | `GET /reports/payment-summary/docx` |

## Routes

| Route | Success | Notes |
|---|---|---|
| `GET /examination-details` | 200 page of rows | `page`, `size`, `sortKey` (`id`, `participantCode`, `fullName`, `nextSessionDate`), `sortBy`, `searchKey` (code, name, department; never the CCCD), `rosterStatus` (default ACTIVE), `attendanceStatus`, `reconciliationStatus`. `no-store`. |
| `GET /examination-details/summary` | 200 | `registered`, `unconfirmed`, `attended`, `noShow`, `unscheduled`, `reconciled`, `pendingReconciliation`; ACTIVE roster only. `unscheduled` counts Participants with no `ASSIGNED`/`ATTENDED` assignment; `pendingReconciliation` counts attended Participants whose services are not reconciled. |
| `GET /examination-details/export` | 200 `.xlsx` | The whole ACTIVE roster (not the UI filter). It is also the import template. `filename="chi-tiet-kham-<agreementCode>.xlsx"`, `no-store`. |
| `GET /examination-details/import-template` | 200 `.xlsx` | Same workbook as the export (the whole ACTIVE roster with the services already recorded), so a blank cell keeps meaning "not examined". `filename="mau-nhap-chi-tiet-kham-<agreementCode>.xlsx"`, `no-store`. Own permission so a role that may import can fetch the file. Not limited by agreement status. |
| `PUT /examination-details/{participantId}` | 200 row | Manual entry for one Participant, see below. |
| `POST /examination-details/imports` | 201 | multipart `file` (`.xlsx`, <= 5 MiB) and required header `Idempotency-Key` (UUID). |
| `GET /reports/payment-summary` | 200 | One line per (agreement service, price snapshot). Optional `from` and `to` (ISO dates, inclusive) limit it to the Participants expected or attended in a session of that range; `from` after `to` is 400. `provisional` is true until the agreement is ENDED. |
| `GET /reports/payment-summary/docx` | 200 `.docx` | Takes the same `from`/`to`. `filename="bao-cao-thanh-toan-<agreementCode>.docx"`, `no-store`. Generated on demand, never stored. Same figures as the JSON report, no per-person data. |

List row: `id`, `participantCode`, `fullName`, `identificationNumberMasked`, `departmentName`, `positionName`,
`nextSessionDate`, `attendanceStatus`, `lastAttendedDate`, `reconciliationStatus`,
`performedAgreementServiceIds`, `rowVersion`. `attendanceStatus` (`UNCONFIRMED`, `ATTENDED`, `NO_SHOW`) is derived from the session assignments; `nextSessionDate` is the earliest session the Participant is still expected at and `lastAttendedDate` the latest one it attended. The columns of the matrix are `services` of the agreement detail.
The backend adds no screen status; the frontend derives the labels from attendance + reconciliation.

Import result: `importJobId`, `agreementId`, `totalRows`, `updatedParticipants`, `unchangedParticipants`,
`performedItems`, `completedAt`. A replay with the same key and the same file returns the stored 201 body.

Report: `agreementId`, `agreementCode`, `agreementName`, `agreementStatus`, `validFrom`, `validTo`, `rangeFrom`, `rangeTo`, `provisional`, `registeredCount`, `attendedCount`,
`reconciledCount`, `sessionCount`, `visitCount` (attended assignments, so one person who came twice counts twice), `items[]` (`agreementServiceId`, `serviceCode`, `serviceName`, `displayOrder`, `unitPrice`,
`examinedCount`, `amount`), `totalAmount`, `generatedAt`. Money is a decimal number. `amount = unitPrice x examinedCount`.

## Excel layout (templateVersion 2)

Sheet `ChiTietKham` (data) and `HuongDan` (instructions and metadata, informational only). Row 1 has the visible
labels, row 2 the hidden machine keys, data starts at row 3. Hidden columns A `participant_id` and B `row_version`
are required on import. Other participant columns, including the informational `next_session_date` and `last_attended_date`, are read-only context and are ignored on import: the file carries no attendance and no actual date. One column `svc:<agreement_service_id>` per agreement service holds `X` or blank. The set of
`svc:` keys must equal the agreement services exactly, otherwise the template is stale (400). A file of an older template version is rejected.
Limits: 10,000 data rows (the export refuses, with 400, an agreement with more active Participants so every export can be imported again), 100 service columns, plus the OOXML package guard shared with the Participant import
(no XLS/XLSM/VBA, no encryption, no external links, bounded unzip, no formula evaluation).

## Reconciliation rules (overwrite by file)

- Only rows in the file are touched; Participants not in the file stay as they are.
- `X` marks the service performed; blank marks it not performed and keeps the row, its price snapshot and `service_request_id`. Reconciling services does not change attendance: attendance is recorded on the [session assignments](sessions-and-assignments.md).
- A new `X` takes the current negotiated price as the snapshot. A Participant with no X and no stored rows is skipped.
- A row identical to the stored state is `unchanged` and writes nothing.
- Each affected Participant becomes RECONCILED. The agreement `rowVersion` does not change.
- All or nothing in one transaction: agreement lock, state recheck, idempotency reserve or replay, row checks, writes, import job
  (`HEALTH_EXAMINATION_SERVICE_RECONCILIATION`, schema V001), audit `IMPORT_EXAMINATION_DETAILS`.
- Only a DRAFT or ACTIVE agreement of an ACTIVE organization accepts an import.

Audit actions: `EXPORT_EXAMINATION_DETAILS`, `DOWNLOAD_EXAMINATION_DETAIL_TEMPLATE`, `EXPORT_PAYMENT_REPORT`, `IMPORT_EXAMINATION_DETAILS`, `RECORD_EXAMINATION_DETAIL`. Audit and logs hold
identifiers and counts only, never CCCD, names or cell values.

## Manual entry for one Participant

`PUT /examination-details/{participantId}`, JSON body:

```json
{ "performedAgreementServiceIds": ["<agreementServiceId>"], "rowVersion": 3 }
```

Response 200: the row of the list (same shape, identification number masked) as stored afterwards. `no-store`.

- `performedAgreementServiceIds` lists the services performed, without duplicates (else 400), all of them agreement services (else 400). An empty list clears the ticks. It goes through the same reconciler as one Excel row: an unticked service keeps its row with `performed = false`, a new one takes the current negotiated price, a service that is no longer offered cannot be newly ticked (409). Attendance is not part of the request.
- `rowVersion` must match, else 409 `Participant was changed by another request; reload and try again`. Exception (retry):
  when the stored state already equals the request, the stored row is returned with 200 and nothing is written. A request
  equal to the stored state also writes nothing and does not raise `rowVersion`. No idempotency key is needed.
- A CANCELLED Participant is 409 (`Participant is cancelled`); a Participant outside the agreement is 404; only a DRAFT or
  ACTIVE agreement of an ACTIVE organization accepts the entry (409 `Agreement does not accept examination detail changes`).
- The agreement is locked like an import, so a manual entry and an import never interleave. A manual entry raises the
  Participant `rowVersion`, so a workbook downloaded earlier is rejected on import (`participant was changed after
  export; export again`) and must be downloaded again.

## Errors

| Case | HTTP | Message |
|---|---|---|
| Missing/empty/non-OOXML file, bad layout, invalid cell, bad date, duplicate `participant_id` | 400 | `Row 7: column "<service>" accepts only X or blank` |
| Stale template (service columns differ from the agreement) | 400 | `The file does not match this agreement; export it again` |
| `participant_id` not in this agreement | 409 | `Row 5: participant is not in this agreement` |
| Participant CANCELLED | 409 | `Row 5: participant is cancelled` |
| `row_version` differs | 409 | `Row 9: participant was changed after export; export again` |
| New X for a service that is no longer offered | 409 | `Row 12: a marked service is no longer offered in this agreement` |
| Agreement ENDED or organization INACTIVE | 409 | `Agreement does not accept examination detail changes` |
| Idempotency key used for another file or still running | 409 | `Idempotency key was used for another request` |
| Not logged in / missing permission | 401 / 403 | Common envelope |
| Too large / wrong media type | 413 / 415 | Existing import handlers |

The first error is reported in a deterministic check order with the real Excel row number.
