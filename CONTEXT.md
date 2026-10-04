# Ngọc Khánh Clinic Health Examination

Current terminology follows the owner-selected clean-slate contract (ADR-0013),
with Participant retained as the business term.

**Organization** is a customer with an internal code, organization type, general
contact channels and one contact person. It owns health-examination batches.

**Health Examination Batch** is a campaign with at least one concrete BatchDay,
a site, maximum service scope and reference/negotiated prices. Date bounds derive
from the days. Its lifecycle is DRAFT, READY, FINALIZED, CLOSED.

**Batch Participant** is a batch-scoped roster snapshot identified by exact CCCD
within the batch. Its planned day belongs to that batch. Roster, attendance and
service reconciliation have independent state. It is not a Patient automatically;
there is no organization-level Participant aggregate.

**Participant Service** is staff reconciliation of an actual performed item within
batch scope, with a historical negotiated-price snapshot. It is independent of a
Doctor's order and can link a ServiceRequest for detailed results.

**Health Examination Record** is a visit root with one mrn/SHS across its forms.
Administrative snapshots and clinical record versions are separate typed models;
issued content and its template-version representation are immutable.

**Roster Import** validates a complete standard-format file before staging, captures
selected day IDs and reviewed allocations, and confirms new participants atomically.
It does not create Patients/Encounters or update existing roster members. Confirmation
retries return the persisted result and concurrent changes produce version conflicts.
Generic staging belongs to integration; roster semantics belong to healthexamination.
