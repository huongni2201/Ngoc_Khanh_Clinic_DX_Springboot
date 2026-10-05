# ADR-0012: Align modules with the clean-slate bounded contexts

## Status

Accepted — 2026-10-03, following the owner's request to align module names,
create missing modules, and remove surplus modules for the new database.

## Context

The clean-slate design defines fourteen business contexts and the supporting
audit context. The Java application currently has twelve business contexts and
the technical shared module. Appointment persistence belongs to encounter and
audit contracts and adapters belong to shared.

The owner retained Participant terminology and selected one database with the
public application schema. SQL schema names do not define Java package names.

## Decision

- Keep the canonical root package `com.ngockhanh.clinic` and these modules:
  `identity`, `patient`, `catalog`, `encounter`, `clinical`, `billing`,
  `diagnostics`, `healthexamination`, `document`, `prescription`,
  `notification`, `integration`, `appointment`, `portal`, `audit`, `shared`.
- Keep `healthexamination`; Java packages do not contain underscores.
- Add `appointment`, `portal`, and `audit`. Move the existing AppointmentRecord
  into appointment, and move audit contracts, adapters, mappers, and their XML
  resources into audit. Declare portal with module metadata until a supported
  portal use case requires implementation; do not generate empty layer trees.
- Publish AuthAudit and AuditWriter through `audit::recording`, in
  `audit.application.port`. Consumers use the public audit recording boundary; the supported authentication/session protocol is described in the current API/security architecture.
- Audit may depend on `shared::id-generator`. Shared must not depend on audit or
  contain audit persistence. Retain shared for generic technical contracts.
- Do not introduce a reporting module. Reports remain queries owned by their
  business modules. None of the existing top-level modules is surplus.
- Verify both the exact module inventory and module boundaries with Spring
  Modulith. Check that audit and appointment types have their documented owner.

## Consequences

Consumers, MyBatis namespaces, mapper scan configuration, and tests use the new
packages. HTTP routes and domain rules do not change in this package migration.
The audit persistence adapter now writes the clean-slate `audit_events` shape;
[ADR-0013](0013-clean-slate-application-contract.md) extends the clean-slate
contract through the remaining records, SQL, domain, use cases and APIs.
No database migration is added by this decision.

Adding modules does not claim that all of their database contracts or use cases
are implemented.

## References

- `src/main/resources/db/migration/V001__create_clean_slate_schema.sql`
- Supplied clean-slate database design and the owner's Participant terminology
- `docs/architecture/02-module-contracts.md`
