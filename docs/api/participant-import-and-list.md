# Participant import, template and list

Owner decision 2026-10-06. Plan: `docs/plans/20261006-participant-excel-import-template-list.md`.
Base path: `/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants`.
Responses use the common envelope `{result, code, message, data}`; failures use `result: "NG"`.

## Permissions

| Route | Permission |
|---|---|
| `GET` list | `PARTICIPANT_VIEW` |
| `GET /import-template` | `PARTICIPANT_TEMPLATE_DOWNLOAD` |
| `POST /imports` | `PARTICIPANT_IMPORT` |

SRS Permission Matrix codes (ADR-0015), granted to CLINIC_MANAGER by Flyway V002 (and to the
local ADMIN role by the local seed). The legacy codes `HEALTH_EXAMINATION_PARTICIPANT_READ` and
`HEALTH_EXAMINATION_PARTICIPANT_IMPORT` are superseded and no longer checked. Permissions are a
login snapshot: sign in again after the migration.

## `GET /` - list

Query: `page` (1-based), `size` (max 100), `searchKey` (max 100; matches name, code, department,
position), `sortKey` (`id`, `fullName`, `participantCode`, `examinationDate`, `createdAt`),
`sortBy` (`ASC`|`DESC`), optional `rosterStatus`, `attendanceStatus`, `reconciliationStatus`,
`batchDayId`, `identificationNumber`.

Item: `id, batchId, batchDayId, examinationDate, participantCode, fullName, dateOfBirth, sex,
identificationNumberMasked, departmentName, positionName, rosterStatus, attendanceStatus,
reconciliationStatus, actualExaminationDate, preparedAt, rowVersion`. The CCCD is always masked.

## `GET /import-template`

Returns an `.xlsx` (`Content-Disposition: attachment`). The Instructions sheet (A1:B3) records the
template version, batch id and batch `rowVersion`; the Participants sheet lists the columns and the
batch's examination days. The template is only valid for that batch version.

## `POST /imports`

Multipart `file` (`.xlsx`, up to 5 MiB, up to 1000 data rows) and `rowVersion` (the batch version the
template was made for); header `Idempotency-Key` (UUID). Returns 201
`{importJobId, batchId, totalRows, createdCount, completedAt}`.

Rules: text cells only; dates as ISO `yyyy-MM-dd`; free column order; no macros, merged cells or
external links; add-only; all rows or none; the first error is reported as `Row N: ...` (English).
Repeating a request with the same key and file returns the stored result; the same key with another
file is a 409. The idempotency receipt is kept at least 24 hours.

| Status | Meaning |
|---|---|
| 400 | Invalid file or row, or the workbook does not match the batch version |
| 401 / 403 | Not signed in / missing permission |
| 404 | Organization or batch not found |
| 409 | Stale `rowVersion`, batch not DRAFT/READY, organization not ACTIVE, duplicate CCCD, key reused for another request or still in progress |
| 413 | Upload larger than allowed |
| 415 | Not an `.xlsx` file |

CORS exposes `Content-Disposition` and `Retry-After` and allows `Idempotency-Key`.

## Logging and audit

Logs and audit metadata never contain CCCD numbers or cell values; they carry ids and counts only.
