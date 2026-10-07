# Participant manual add, edit, cancel and reactivate

Owner decision 2026-10-07. Plans: `docs/plans/20261007-participant-manual-crud.md` and
`docs/plans/20261007-participant-reactivate.md`.
Base path: `/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants`.
Responses use the common envelope `{result, code, message, data}`; failures use `result: "NG"`.
The import, template and list contract stays in [participant import and list](participant-import-and-list.md).

## Permission

Owner amendment 2026-10-07: use separate endpoint permissions under ADR-0015.
Every route requires a STAFF account and its own permission:

| Route | Permission |
|---|---|
| POST collection (manual add) | `PARTICIPANT_CREATE` |
| GET /{participantId} | `PARTICIPANT_VIEW` |
| PUT /{participantId} | `PARTICIPANT_UPDATE` |
| DELETE /{participantId} (cancel) | `PARTICIPANT_REMOVE` |
| POST /{participantId}/reactivate | `PARTICIPANT_REACTIVATE` |

The consolidated V002 seed grants VIEW, UPDATE, REMOVE, CREATE and REACTIVATE only
to CLINIC_MANAGER. The legacy `HEALTH_EXAMINATION_PARTICIPANT_MANAGE` grant no longer
opens these routes. Permissions are a login snapshot: sign in again after migration.
`GET /import-template` is matched before `GET /{participantId}` and requires
`PARTICIPANT_TEMPLATE_DOWNLOAD`. The list and import keep their dedicated permissions.

## Rules

- Only a DRAFT or READY batch that is not soft-deleted, of an ACTIVE organization, accepts a change
  (409 `Batch does not accept Participant changes` otherwise).
- `batchDayId` must be a day of the same batch (409 `Examination day is not a day of this batch`).
- The CCCD is unique per batch across every roster status. A CANCELLED Participant keeps its CCCD
  reserved, so the same CCCD cannot be added again (409 `Participant identity already exists in this batch`);
  reactivate it instead of adding it again. The same holds for an Excel import (add-only, 409).
- The CCCD can change only while the Participant has no linked Patient (`patient_id IS NULL`); after that,
  a different CCCD is 409 `Identification number is locked after visit preparation`.
- A CANCELLED Participant cannot be edited or cancelled again (409 `Participant is cancelled`); it can only
  be reactivated.
- There is no physical delete. Create has no `Idempotency-Key`; a repeated
  submit is stopped by the duplicate CCCD rule.
- Manual changes do not change the batch `rowVersion`. They lock the batch, then the Participant, and
  write the audit event in the same transaction.

## `POST /` - add one

Body (same fields as an import row): `participantCode?` (max 500), `fullName` (max 200),
`dateOfBirth` (ISO date, not in the future), `sex` (`MALE|FEMALE|OTHER|UNKNOWN`),
`identificationNumber` (digits only, 1-20), `phone?`, `email?`, `departmentName`, `positionName`
(max 500 each), `batchDayId`. Blank optional text is stored as null.
Returns `201` with the detail below and `Cache-Control: no-store`.

## `GET /{participantId}` - detail

Returns the list item plus `identificationNumber` (full), `phone`, `email`, `patientLinked`,
`source` (`MANUAL|IMPORT`), `createdAt`, `updatedAt`. This is the only response that carries the
full CCCD, so it needs PARTICIPANT_VIEW and is `no-store`. It is never logged or audited.

## `PUT /{participantId}` - edit

Same body as add plus `rowVersion` (required, >= 0). Replaces every editable field. A stale
`rowVersion` is 409 `Record was changed by another request`. Returns `200` with the detail.

## `DELETE /{participantId}?rowVersion={n}` - cancel

Sets `roster_status` to CANCELLED. The version travels in the query because a DELETE has no body.
Returns `204`. Refused with 409 `Participant cannot be cancelled after preparation or attendance`
when `preparedAt` is set, attendance is ATTENDED or reconciliation is RECONCILED.

## `POST /{participantId}/reactivate` - reactivate

Returns the cancelled Participant to the active roster: the same row (same `id`, import provenance,
attendance and reconciliation) becomes ACTIVE again; nothing is inserted and the unique CCCD rule is
untouched. Body: `rowVersion` (required, >= 0) and `batchDayId?` (a day of the batch; omitted keeps the
day the Participant had). Roster fields are not editable here; edit them afterwards with `PUT`.
Returns `200` with the detail (new `rowVersion`) and `Cache-Control: no-store`.

| Status | Safe message |
|---|---|
| 409 | `Participant is not cancelled` (already ACTIVE) |
| 409 | `Participant cannot be reactivated after preparation or attendance` (defensive guard) |
| 409 | `Examination day is not a day of this batch` / `Batch does not accept Participant changes` |
| 409 | `Record was changed by another request` (stale `rowVersion`) |

## Status codes

| Status | Cause |
|---|---|
| 400 | Invalid body or query (missing fields, length, CCCD not digits, bad date, missing `rowVersion`) |
| 401 / 403 | Not signed in / missing endpoint permission |
| 404 | Unknown organization, batch (or soft-deleted) or Participant |
| 409 | Any rule above, or a stale `rowVersion` |

## Logging and audit

Actions `CREATE_BATCH_PARTICIPANT`, `UPDATE_BATCH_PARTICIPANT`, `CANCEL_BATCH_PARTICIPANT`,
`REACTIVATE_BATCH_PARTICIPANT` (changed fields `rosterStatus`, plus `batchDayId` when the day changes) on entity
`HEALTH_EXAMINATION_BATCH_PARTICIPANT`. Audit snapshots hold `organizationId`, `batchId`, `rowVersion`,
`source` and the names of the changed fields only; never the CCCD, name, phone or email.
