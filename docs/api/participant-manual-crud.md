# Participant manual add, edit, cancel and reactivate

Owner decision 2026-10-07; personal fields and generated codes updated 2026-10-08; scheduling moved to [sessions and assignments](sessions-and-assignments.md) by [ADR-0019](../adr/0019-agreement-session-assignment.md) on 2026-10-16.
Base path: `/api/v1/organizations/{organizationId}/agreements/{agreementId}/participants`.
Responses use [the common envelope](../../PROJECT_RULES.md#envelope-errors-and-pagination);
failures use `result: "NG"`, and null `message`/`data` are omitted.
The import, template and list contract stays in [participant import and list](participant-import-and-list.md).

## Permission

Owner amendment 2026-10-07: use separate endpoint permissions under ADR-0015.
Every route requires a STAFF account and its own permission:

| Route | Permission |
|---|---|
| POST collection (manual add) | `PARTICIPANT_CREATE` |
| GET /{participantId} | `PARTICIPANT_DETAIL_VIEW` |
| PUT /{participantId} | `PARTICIPANT_UPDATE` |
| DELETE /{participantId} (cancel) | `PARTICIPANT_REMOVE` |
| POST /{participantId}/reactivate | `PARTICIPANT_REACTIVATE` |

The consolidated V002 seed grants DETAIL_VIEW, UPDATE, REMOVE, CREATE and REACTIVATE
to CLINIC_MANAGER. Permissions are a login snapshot: sign in again after migration.
`GET /import-template` is matched before `GET /{participantId}` and requires
`PARTICIPANT_TEMPLATE_DOWNLOAD`. The list and import keep their dedicated permissions.

## Rules

- Only a DRAFT or ACTIVE agreement that is not soft-deleted, of an ACTIVE organization, accepts a change
  (409 `Agreement does not accept Participant changes` otherwise).
- A Participant has no examination day: it is put in sessions afterwards through assignments.
- The CCCD is unique per agreement across every roster status. A CANCELLED Participant keeps its CCCD
  reserved, so the same CCCD cannot be added again (409 `Participant identity already exists in this agreement`);
  reactivate it instead of adding it again. The same holds for an Excel import (add-only, 409).
- The CCCD can change only while the Participant has no linked Patient (`patient_id IS NULL`); after that,
  a different CCCD is 409 `Identification number is locked after visit preparation`.
- A CANCELLED Participant cannot be edited or cancelled again (409 `Participant is cancelled`); it can only
  be reactivated.
- There is no physical delete. Create has no `Idempotency-Key`; a repeated
  submit is stopped by the duplicate CCCD rule.
- Manual changes do not change the agreement `rowVersion`. They lock the agreement, then the Participant, and
  write the audit event in the same transaction.

## `POST /` - add one

Body (same fields as an import row): `fullName` (max 200),
`dateOfBirth` (ISO date, not in the future), `sex` (`MALE|FEMALE|OTHER|UNKNOWN`),
`identificationNumber` (digits only, 1-20), `identificationIssueDate?` (ISO date),
`identificationIssuePlace?`, `ethnicity?`, `phone?`, `email?` (max 500 each), `address?` (max 1000),
`workplace?` (max 500), `departmentName`, `positionName` (max 500 each), `note?` (max 2000). Blank optional text is stored as null. There is no `participantCode` in the body: the
system generates it as `<agreement code>-<4-digit running number>` (for example `KSK-2026-001-0001`) and it never
changes on update.
Returns `201` with the detail below and `Cache-Control: no-store`.

## `GET /{participantId}` - detail

Returns the list item plus `identificationNumber` (full), `identificationIssueDate`,
`identificationIssuePlace`, `ethnicity`, `phone`, `email`, `address`, `workplace`, `note`, `patientLinked`,
`source` (`MANUAL|IMPORT`), `assignments[]` (every assignment of the Participant with its session: `assignmentId`, `sessionId`, `sessionDate`, `activity`, `siteType`, `siteAddress`, `sessionStatus`, `status`, `replacedByAssignmentId`, `attendedAt`, `note`, `rowVersion`; newest session first), `createdAt`, `updatedAt`. Detail, create, update and reactivate
return this same shape with the full CCCD and `Cache-Control: no-store`, each under
its route's permission. List responses carry only the masked CCCD. Response payloads
are never logged or included in audit metadata.

## `PUT /{participantId}` - edit

Same body as add plus `rowVersion` (required, >= 0). Replaces every editable field. A stale
`rowVersion` is 409 `Record was changed by another request`. Returns `200` with the detail.

## `DELETE /{participantId}?rowVersion={n}` - cancel

Sets `roster_status` to CANCELLED. The version travels in the query because a DELETE has no body.
Returns `204`. Refused with 409 `Participant cannot be cancelled after attendance` when the Participant attended a session, and with the domain's preparation/reconciliation message when `preparedAt` is set or services are RECONCILED. Cancelling releases (sets `CANCELLED`) every assignment that is still `ASSIGNED`; attended and past assignments stay as history.

## `POST /{participantId}/reactivate` - reactivate

Returns the cancelled Participant to the active roster: the same row (same `id`, import provenance
and reconciliation) becomes ACTIVE again; nothing is inserted and the unique CCCD rule is
untouched. Released assignments are not restored: schedule the Participant again with a new assignment. Body: `rowVersion` (required, >= 0). Roster fields are not editable here; edit them afterwards with `PUT`.
Returns `200` with the detail (new `rowVersion`) and `Cache-Control: no-store`.

| Status | Safe message |
|---|---|
| 409 | `Participant is not cancelled` (already ACTIVE) |
| 409 | `Participant cannot be reactivated after preparation or attendance` (defensive guard) |
| 409 | `Agreement does not accept Participant changes` |
| 409 | `Record was changed by another request` (stale `rowVersion`) |

## Status codes

| Status | Cause |
|---|---|
| 400 | Invalid body or query (missing fields, length, CCCD not digits, bad date, missing `rowVersion`) |
| 401 / 403 | Not signed in / missing endpoint permission |
| 404 | Unknown organization, agreement (or soft-deleted) or Participant |
| 409 | Any rule above, or a stale `rowVersion` |

## Logging and audit

Actions `CREATE_PARTICIPANT`, `UPDATE_PARTICIPANT`, `CANCEL_PARTICIPANT`,
`REACTIVATE_PARTICIPANT` (changed field `rosterStatus`) on entity
`HEALTH_EXAMINATION_PARTICIPANT`. Audit snapshots hold `organizationId`, `agreementId`, `rowVersion`,
`source` and the names of the changed fields only; never the CCCD, name, phone or email.
