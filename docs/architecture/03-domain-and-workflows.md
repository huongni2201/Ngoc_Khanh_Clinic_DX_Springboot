# Domain model and supported workflows

## Domain design

Aggregates own consistency boundaries and mutation methods. Entities retain
identity; value objects are immutable. Creation and restoration enforce
invariants. Preserve separate request, command/query, domain, persistence and
integration models where their responsibilities differ. Stable contracts are
not Map<String,Object>. Use typed staging payloads serialized only in adapters.

Pass Clock-derived instants from application into domain operations. Use UUIDv7
when allocating persisted identity; exact CCCD is the mandatory patient identity.
Patient/clinical/financial history is not hard-deleted. No fuzzy identity merge
or age-eligibility rejection is part of the implemented roster/record workflow.

### Patient identity

CCCD uses the technical field `identification_number`, stored as text. Exact lookup
precedes Patient creation; `patients.identification_number` is unique. The current
baseline has no passport/identity-type abstraction or fuzzy merge by name/phone.
Patient contacts/addresses/merge-history tables require an explicit new contract.

## Account authentication

Account owns username/password_hash and exactly one STAFF or PATIENT owner.
StaffMember is clinic staff, separate from an organization Participant. Role
assignments are account/role pairs with grantedBy/grantedAt, without invented
assignment IDs, time windows or location scopes. Login requires active account,
valid credentials and a valid owner; roles can be empty. Authentication itself
does not grant clinical or portal access.

Username and password are compared exactly as submitted (no trimming; username is
case-sensitive). The session protocol is described in
[ADR-0014](../adr/0014-session-cookie-redis-login.md); refresh-token storage
fields do not invent a new endpoint.

## Organization and Batch

Organization has an optional unique tax code, general contact channels and one
named contact; it has no separate business code. A Batch owns at least one unique
BatchDay and an ordered maximum service scope. Date bounds derive from its days.
Site types are CLINIC and ORGANIZATION_SITE; states are DRAFT, READY, FINALIZED,
CLOSED. Draft configuration updates preserve retained day/service IDs and reject
removal of referenced days/services; only a DRAFT batch can be edited.

Batch "delete" is a soft delete (`deleted_at`): only a DRAFT batch with no Participant
(any roster status) and no import history can be deleted. The row, its days and
services and its unique `batch_code` are kept, and a deleted batch is invisible to get,
list, update and further delete. Participant writes cannot touch a deleted batch.
Import history is asked through the integration module's `BatchHistoryQuery`.

Organization "delete" is deactivation: `ACTIVE` to `INACTIVE` through
`Organization.deactivate()`, guarded by the expected row version and audited in the same
transaction. The row and its batches are preserved (batch FK is `ON DELETE RESTRICT`) and
batch workflows are unchanged. Inactive organizations are hidden from organization list
and get; updating one is not blocked by this workflow.

On service addition, capture catalog reference price; negotiated price belongs
to the Batch. Ordinary edits preserve existing performed-item price snapshots.
Retroactive repricing is a separate authorized, audited business operation.

## Batch Participant

Participant is a batch-scoped identity/employment snapshot, not an organization
master aggregate or automatically a Patient. A planned BatchDay must belong to
the same Batch. Roster, attendance and service reconciliation are independent:

- roster: ACTIVE/CANCELLED;
- attendance: UNCONFIRMED/ATTENDED/ABSENT; ATTENDED requires actual date;
- reconciliation: PENDING/RECONCILED; zero items is meaningful only after reconciliation.

Roster addition must never create Patient or Encounter. Authorized visit
preparation links or creates Patient by exact CCCD and prepares the visit. Moving
a planned day preserves prepared links and actual attendance. Sensitive changes
use expected versions and audit in the application transaction.

ParticipantService records staff reconciliation of actual performed items within
batch scope; it is independent of Doctor orders. New performed items use active
batch services and their negotiated price. Existing identity, historical price
and established ServiceRequest links cannot change through reconciliation. Keep
unchecked rows with is_performed=false; save header/items atomically with versions.

## Participant Excel import and list

The multi-step import workflow (template, upload, preview, confirm, cancel) was removed
on 2026-10-05 and stays removed. On 2026-10-06 the owner restored a single-step flow
for one Organization/Batch; the HTTP contract is
[participant import and list](../api/participant-import-and-list.md).

- Add-only: existing Participants are never updated or cancelled. A CCCD already in the
  batch (any roster status) is rejected.
- All-or-nothing in one transaction: batch lock, organization/state recheck, idempotency
  reserve or replay, expected `rowVersion` check, dedupe, VALIDATED import job and rows,
  Participant inserts in chunks, job confirmed, key completed, audit.
- Only DRAFT/READY batches of an ACTIVE organization accept an import.
- The list masks the CCCD; the full number is never returned and never logged.
- Existing participant provenance and import history remain in the schema.

## Record history and other contexts

HealthExaminationRecord is one visit root with stable mrn/SHS shared across forms.
Its PREPARED/IN_PROGRESS/COMPLETED/ISSUED/CANCELLED lifecycle is separate from the
Batch. Administrative snapshots and clinical record versions have typed separate
tables; final/issued content is immutable and corrections retain history.

Clinical assessments and diagnostics results have separate version roots.
Prescriptions have logical roots, official versions and version items. Billing
ServiceAuthorization gates service execution; each Encounter can contain multiple
OrderRounds. Encounter, ServiceRequest and Result retain their own lifecycles;
derive diagnostic progress and Doctor worklists from those records, OrderRound,
ServiceAuthorization and performing location. Doctor review readiness follows
completion of required requests/results; no reception/exam/return queue tickets
are introduced. Doctor creates orders; Front Desk owns baseline collection.
Billable diagnostics execute only with the documented payment authorization or
explicit exemption. Financial history is preserved.

Final results and issued prescriptions are versioned/corrected, not overwritten
or hard-deleted. Finalization enforces permissions; results remain traceable to
their ServiceRequest/Encounter, with explicit partial/final states. Attachments
do not replace structured results where structured data exists. Inventory and
dispensing are outside the current baseline.

Read current status/constraints from the clean-slate schema; do not
recreate a parallel queue model.

Portal releases reference exact official result/document versions and remain
independent of SMS/email outcomes. Document renders from source plus template
versions; issued representations retain those references. Backend render audit
records DOCUMENT_RENDERED, not proof that a browser print dialog printed paper.
Notification/outbox use deduplication and leases for durable post-commit delivery.
These storage contracts do not claim unsupported workflows have public endpoints.
