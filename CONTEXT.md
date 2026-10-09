# Ngọc Khánh Clinic Health Examination

Current terminology follows the owner-selected clean-slate contract (ADR-0013),
with Participant retained as the business term.

**Organization** is a customer identified by UUID with an optional unique tax code,
general contact channels (phone optional) and one contact person. It owns
health-examination batches and has no separate business code or organization type.

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

**Roster Import** is a single-step, add-only, all-or-nothing Excel operation, with
template download and Participant list. Preview/confirm/cancel endpoints are absent.
Import jobs, rows and Participant provenance are retained. See
[ADR-0013](docs/adr/0013-clean-slate-application-contract.md#roster-import-scope).

This file is a terminology summary. Read [current workflows](docs/architecture/03-domain-and-workflows.md)
for invariants and [the API inventory](docs/api/clean-slate-migration.md) for
implemented routes; storage/domain models do not imply public endpoints.
