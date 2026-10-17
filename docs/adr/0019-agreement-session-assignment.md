# ADR-0019: Agreement, session and assignment replace the batch

## Status

Proposed — implemented on the owner's request on 2026-10-16 following
[the registration, scheduling and results plan](../plans/20261016-corporate-examination-registration-scheduling-and-results.md).
The defaults D01–D10 of that plan were applied without a formal clinic sign-off; changing
one of them is a follow-up change, not a rollback. This record supersedes the Batch parts of
[ADR-0013](0013-clean-slate-application-contract.md) (BatchDay, batch lifecycle, per-day
attendance) and the batch route names of [ADR-0015](0015-endpoint-permission-rbac.md). The
rest of both records stands.

## Context

The batch tied three different things into one object: what was agreed with the customer
(services and prices), when and where people are examined (days and one site), and whether
a person came. A customer changes its dates after the roster exists, sends people in several
waves and sometimes a person comes on another day. The batch could not express that without
editing the agreement, and attendance lived on the Participant as one value with one date.

## Decision

- **Agreement** (`health_examination_agreements`) holds the code, name, optional validity
  period and the service lines with negotiated prices. Status is DRAFT → ACTIVE → ENDED.
  It has no day, site or attendance.
- **Service line** change is additive: a price change retires the line (`active = false`)
  and adds a new one. Rows already recorded keep their price snapshot. A service has at most
  one active line per agreement (partial unique index). *Amended 2026-10-16:* the services are
  edited only while the agreement is a DRAFT (PUT) and are fixed once it is ACTIVE; the add and
  retire endpoints and their permissions were removed. Lines retired earlier stay as history.
- **Session** (`health_examination_sessions`) is a date, an activity (`ALL`, `SAMPLING`,
  `EXAMINATION`) and a site (`CLINIC` or `ORGANIZATION_SITE` with an address) of one
  organization. It has no capacity and no shifts. It may be reserved to one agreement or
  shared by the agreements of the organization. There is at most one open session per
  organization, date, site and activity; a cancelled session frees the slot.
- **Assignment** (`health_examination_session_assignments`) puts a Participant in a session
  with status `ASSIGNED`, `ATTENDED`, `NO_SHOW`, `MOVED` or `CANCELLED`. A Participant is in
  a session at most once while the assignment is `ASSIGNED` or `ATTENDED`. Moving creates the
  new assignment first and marks the old one `MOVED` pointing at it, so history is kept.
- **Attendance is derived** from the assignments and no longer stored on the Participant:
  `ATTENDED` if any assignment attended; `NO_SHOW` if one was missed and none is still
  expected; otherwise `UNCONFIRMED`. Recording `ATTENDED` needs the session date to be today
  or earlier in Vietnam time (Asia/Ho_Chi_Minh).
- **Service reconciliation** is independent of attendance: the services performed are
  recorded for the Participant against the agreement lines, by hand or by Excel, and the
  payment report counts them. A report may be limited to a session date range.
- **Participant** gains `organization_id` (with composite keys), and loses the planned day,
  `attendance_status`, `actual_examination_date` and the attendance audit columns. Identity
  stays unique by exact CCCD within the agreement.

## Migration

The change first shipped as `V008__agreement_session_assignment.sql`, which renamed the tables and permission codes
(`BATCH_*` → `AGREEMENT_*`, `START` → `ACTIVATE`, `CLOSE` → `END`) and rewrites the routes
(`/health-examination-batches/{batchId}` → `/agreements/{agreementId}`). Existing data is
kept with a best-effort backfill: each batch day becomes a session (days of the same
organization, date and site share one), each Participant gets one assignment on its planned
day, `ATTENDED` keeps its recorded date, `ABSENT` becomes `NO_SHOW`, and a cancelled
Participant becomes `CANCELLED`. Status mapping: `READY` → `ACTIVE`; `FINALIZED` and `CLOSED`
→ `ENDED`. The plan proposed V004 for this change; it first shipped as V008 and was folded into V001 and V002 by the 2026-10-16 consolidation.
An applied migration is never edited.

## Consequences

- API names, permission codes and the screens change; there is no compatibility alias.
  See [agreements](../api/organizations-and-agreements.md) and
  [sessions and assignments](../api/sessions-and-assignments.md).
- Twelve permissions are added and granted to CLINIC_MANAGER; the local seed grants all.
- Identity is unique per agreement across every roster status, including cancelled rows.
  Reactivation keeps the row. This is stricter than the plan's wording and is kept.
- The Word payment report and the Excel detail workbook (template version 2) show validity,
  session and visit counts and the next/last session dates (information only; the file no
  longer carries an actual examination date).
- The printed form shows the date of the named session, else the Participant's next expected
  session, else the printing date.
- Capacity, shifts, notifications and appointment links stay out of scope.
