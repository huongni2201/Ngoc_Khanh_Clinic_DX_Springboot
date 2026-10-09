# Participant import, template and list

Owner decision 2026-10-06; workbook columns and generated codes updated 2026-10-08.
Base path: `/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants`.
Responses use [the common envelope](../../PROJECT_RULES.md#envelope-errors-and-pagination);
failures use `result: "NG"`, and null `message`/`data` are omitted.

## Permissions

| Route | Permission |
|---|---|
| `GET` list | `PARTICIPANT_VIEW` |
| `GET /import-template` | `PARTICIPANT_TEMPLATE_DOWNLOAD` |
| `POST /imports` | `PARTICIPANT_IMPORT` |

SRS Permission Matrix codes (ADR-0015), granted to CLINIC_MANAGER by Flyway V002.
The local seed additionally grants every permission to TEST; it creates no ADMIN role.
The legacy codes `HEALTH_EXAMINATION_PARTICIPANT_READ` and
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

The Participants headers are Vietnamese, in this default order:

`STT`, `Họ Và Tên`, `Ngày Sinh`, `Giới Tính`, `CCCD`, `Ngày Cấp CCCD`,
`Nơi Cấp CCCD`, `Dân Tộc`, `Số Điện Thoại`, `Email`, `Chỗ Ở`, `Nơi Làm Việc`,
`Đơn Vị/Phòng Ban`, `Chức Vụ`, `Ngày Khám`, `Ghi Chú`.

Data starts at row 2. Required values are name, date of birth, sex, CCCD,
department, position and examination date. Other data fields are optional; their
length/date constraints follow [the roster field contract](participant-manual-crud.md).

Headers may be reordered; comparison ignores case and excess whitespace but
preserves Vietnamese accents. Old machine-key headers are rejected. `STT` is
ignored, and a row containing only STT is empty. The six personal-detail fields
are optional; empty values become null. Machine keys remain in validation errors.
Participant codes are generated as `<batch code>-<4-digit running number>`,
counting existing Participants including cancelled rows, and stay fixed on update.
The workbook has no participant-code input column.

Personal text fields follow the lengths in the [manual Participant contract](participant-manual-crud.md),
including address up to 1000 and note up to 2000 characters. The default import
cell budget is 2000 characters (`clinic.participant-import.max-cell-chars`);
an explicitly lower budget also restricts these fields. The Excel reader checks
the address/note limits and keeps the 500-character limit for other text cells.

## `POST /imports`

Multipart `file` (`.xlsx`, up to 5 MiB, up to 1000 data rows) and `rowVersion` (the batch version the
template was made for); header `Idempotency-Key` (UUID). Returns 201
`{importJobId, batchId, totalRows, createdCount, completedAt}`.

Rules: data cells are text (STT is ignored); dates as ISO `yyyy-MM-dd`; free column order; no macros, merged cells or
external links; add-only; all rows or none; the first error is reported as `Row N: ...` (English).
Repeating the same request with the same key and file returns the stored result;
the same key with another file is a 409. A completed identical replay precedes
batch lifecycle and expected-version checks, so later state changes do not turn
that replay into a new import. The idempotency receipt is kept at least 24 hours.

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
