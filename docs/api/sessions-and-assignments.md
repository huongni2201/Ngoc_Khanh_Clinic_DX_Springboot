# Session and assignment API

Owning module: `healthexamination`. Decision: [ADR-0019](../adr/0019-agreement-session-assignment.md).
Responses use [the common envelope and pagination](../../PROJECT_RULES.md#envelope-errors-and-pagination).
Route IDs are UUIDs. Every change takes the last read `rowVersion` (integer >= 0); a stale
version is 409. Each write and its audit event share one transaction. All routes need a STAFF
account holding the permission; use cases check the same permission again.

## Session

Base path: `/api/v1/organizations/{organizationId}/sessions`.

| Method/path | Success | Permission |
|---|---|---|
| GET collection | 200 page of sessions | `HEALTH_EXAMINATION_SESSION_VIEW` |
| GET `/{sessionId}` | 200 session | `HEALTH_EXAMINATION_SESSION_DETAIL_VIEW` |
| POST collection | 201 session | `HEALTH_EXAMINATION_SESSION_CREATE` |
| PUT `/{sessionId}` | 200 session | `HEALTH_EXAMINATION_SESSION_UPDATE` |
| POST `/{sessionId}/cancel` body `{"rowVersion": n}` | 200 session | `HEALTH_EXAMINATION_SESSION_CANCEL` |

Create fields: `sessionDate` (required ISO date), `activity` (`ALL`, `SAMPLING`,
`EXAMINATION`), `siteType` (`CLINIC` or `ORGANIZATION_SITE`), `siteAddress` (required and
non-blank for `ORGANIZATION_SITE`, ignored for `CLINIC`) and optional `agreementId`, which
reserves the session to one ACTIVE or DRAFT agreement of the organization. PUT takes the same
fields except `agreementId`, plus `rowVersion`, and only changes an open session.

At most one open session exists per organization, date, site and activity; a second create is
409. A session has no capacity and no shifts.

The response has `id`, `organizationId`, `agreementId`, `sessionDate`, `activity`, `siteType`,
`siteAddress`, `status` (`OPEN`, `CANCELLED`), `assignedCount`, `attendedCount`, `noShowCount`,
`createdAt`, `updatedAt` and `rowVersion`.

GET collection accepts `page`, `size`, `searchKey`, `agreementId`, `from`, `to` (ISO dates,
both inclusive), `activity`, `status`, `sortKey` (`id`, `sessionDate`, `activity`, `status`,
`createdAt`) and `sortBy`.

Cancelling is refused with 409 while Participants are still assigned (`ASSIGNED` or
`ATTENDED`); move or cancel them first. A cancelled session frees its slot.

## Assignment

Base path: `/api/v1/organizations/{organizationId}/sessions/{sessionId}/assignments`.

| Method/path | Success | Permission |
|---|---|---|
| GET collection | 200 page of rows | `HEALTH_EXAMINATION_ASSIGNMENT_VIEW` |
| POST collection `{"participantIds": [...]}` | 201 result | `HEALTH_EXAMINATION_ASSIGNMENT_CREATE` |
| POST `/move` `{"targetSessionId"}` | 200 result | `HEALTH_EXAMINATION_ASSIGNMENT_MOVE_ALL` |
| POST `/{assignmentId}/move` `{"targetSessionId","rowVersion"}` | 200 assignment | `HEALTH_EXAMINATION_ASSIGNMENT_MOVE` |
| POST `/{assignmentId}/cancel` `{"rowVersion","note"}` | 200 assignment | `HEALTH_EXAMINATION_ASSIGNMENT_CANCEL` |
| POST `/{assignmentId}/attendance` `{"status","rowVersion","note"}` | 200 assignment | `HEALTH_EXAMINATION_ASSIGNMENT_ATTENDANCE_RECORD` |

- **Assign** takes 1 to 1000 distinct Participants. A Participant already in the session is
  listed in `skippedParticipantIds` and is not an error. A cancelled Participant, or one whose
  agreement does not accept changes (ENDED), is 409; an unknown Participant is 404 and a session
  reserved to another agreement refuses the Participant; in both cases nothing is written.
- **Move one** creates the assignment in the target session and marks the old one `MOVED`
  with `replacedByAssignmentId`; the response is the new assignment. Moving to a session the
  Participant is already in is 409.
- **Move all** moves every `ASSIGNED` Participant of the session to the target. Participants
  already in the target are skipped. The result has `movedCount` and `skippedParticipantIds`.
- **Attendance** `status` is `ATTENDED`, `NO_SHOW` or `ASSIGNED` (to undo a mistake).
  `ATTENDED` needs the session date to be today or earlier in Asia/Ho_Chi_Minh, otherwise 409.
  Repeating the same status is idempotent.
- **Cancel** sets `CANCELLED`; the note records why.

GET collection accepts `page`, `size`, `searchKey`, `status`, `sortKey` (`id`, `fullName`,
`participantCode`, `departmentName`, `status`) and `sortBy`. Rows carry the assignment
(`assignmentId`, `status`, `attendedAt`, `note`, `assignmentRowVersion`) and the Participant
(`participantId`, `participantCode`, `fullName`, masked identification number, department,
position, `rosterStatus`).

Assignment `status`: `ASSIGNED`, `ATTENDED`, `NO_SHOW`, `MOVED`, `CANCELLED`. A Participant is
live in a session (`ASSIGNED` or `ATTENDED`) at most once.

## Audit

Actions: `CREATE_HEALTH_EXAMINATION_SESSION`, `UPDATE_HEALTH_EXAMINATION_SESSION`,
`CANCEL_HEALTH_EXAMINATION_SESSION`, `ASSIGN_PARTICIPANTS_TO_SESSION`,
`MOVE_SESSION_ASSIGNMENT`, `MOVE_SESSION_ASSIGNMENTS`, `CANCEL_SESSION_ASSIGNMENT`,
`RECORD_SESSION_ATTENDANCE`. Payloads hold identifiers, statuses and counts, never names or
identification numbers.
