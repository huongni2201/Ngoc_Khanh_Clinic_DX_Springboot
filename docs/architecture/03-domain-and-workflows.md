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

Organization has a unique code, type, general contact channels and one named
contact; optional tax code is not unique. A Batch owns at least one unique
BatchDay and an ordered maximum service scope. Date bounds derive from its days.
Site types are CLINIC and ORGANIZATION_SITE; states are DRAFT, READY, FINALIZED,
CLOSED. Draft configuration updates preserve retained day/service IDs and reject
removal of referenced days/services. No batch deletion endpoint is implemented.

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

## Removed roster import

The owner removed the backend Excel roster import workflow on 2026-10-05.
No template, upload, preview, confirm or cancel use case remains. See
[the API removal contract](../api/clean-slate-migration.md#removed-excel-roster-import).
Existing participant provenance and import history remain in the unchanged schema.

## Record history and other contexts

HealthExaminationRecord is one visit root with stable mrn/SHS shared across forms.
Its PREPARED/IN_PROGRESS/COMPLETED/ISSUED/CANCELLED lifecycle is separate from the
Batch. Administrative snapshots and clinical record versions have typed separate
tables; final/issued content is immutable and corrections retain history.

Clinical assessments and diagnostics results have separate version roots.
Prescriptions have logical roots, official versions and version items. Billing
ServiceAuthorization gates service execution; each Encounter can contain multiple
OrderRounds. Read current status/constraints from the clean-slate schema; do not
recreate a parallel queue model.

Portal releases reference exact official result/document versions and remain
independent of SMS/email outcomes. Document renders from source plus template
versions; issued representations retain those references. Backend render audit
records DOCUMENT_RENDERED, not proof that a browser print dialog printed paper.
Notification/outbox use deduplication and leases for durable post-commit delivery.
These storage contracts do not claim unsupported workflows have public endpoints.
