# Ngọc Khánh Clinic Health Examination

Current terminology follows the owner-selected clean-slate contract (ADR-0013) as amended by
ADR-0019, with Participant retained as the business term.

**Organization** is a customer identified by UUID with an optional unique tax code,
general contact channels (phone optional) and one contact person. It owns
agreements and sessions and has no separate business code or organization type.

**Agreement** (Thỏa thuận) is what was agreed with an Organization: the services with
reference and negotiated prices and an optional validity period. Its lifecycle is DRAFT,
ACTIVE, ENDED. It has no day, site or attendance. Its services are edited only while it is a
draft and are fixed once it is active.

**Session** (Lịch khám) is a date, an activity (all, sampling, examination) and a site (the
clinic or an organization site) of one Organization. It has no capacity or shift and may serve
one Agreement or be shared. It replaces the former BatchDay.

**Assignment** (Phân lịch) puts a Participant in a Session: ASSIGNED, ATTENDED, NO_SHOW, MOVED or
CANCELLED. Attendance shown for a Participant is derived from its assignments; there is no
attendance value or actual date on the Participant.

**Participant** is an agreement-scoped roster snapshot identified by exact CCCD within the
agreement. Roster, scheduling and service reconciliation have independent state. It is not a
Patient automatically; there is no organization-level Participant aggregate.

**Participant Service** is staff reconciliation of an actual performed item within
agreement scope, with a historical negotiated-price snapshot. It is independent of a
Doctor's order and can link a ServiceRequest for detailed results.

**Health Examination Record** is a visit root with one mrn/SHS across its forms.
Administrative snapshots and clinical record versions are separate typed models;
issued content and its template-version representation are immutable.

**Roster Import** is a single-step, add-only, all-or-nothing Excel operation, with
template download and Participant list. Preview/confirm/cancel endpoints are absent.
Import jobs, rows and Participant provenance are retained. See
[ADR-0013](docs/adr/0013-clean-slate-application-contract.md#roster-import-scope).

The word "batch" survives only in history and in the notification module's delivery batches.

This file is a terminology summary. Read [current workflows](docs/architecture/03-domain-and-workflows.md)
for invariants and [the API inventory](docs/api/clean-slate-migration.md) for
implemented routes; storage/domain models do not imply public endpoints.
