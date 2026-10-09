# ADR-0012: Align modules with the clean-slate bounded contexts

## Status

Accepted — 2026-10-03, following the owner's request to align module names,
create missing modules, and remove surplus modules for the new database.

## Context

The clean-slate design defines fourteen business contexts, supporting audit and
shared technical code. Each persisted concept needs one owning bounded context
and deliberate public contracts for cross-module communication.

The owner retained Participant terminology and selected one database with the
public application schema. SQL schema names do not define Java package names.

## Decision

- Keep the canonical root package `com.ngockhanh.clinic` and these modules:
  `accesscontrol`, `patient`, `catalog`, `encounter`, `clinical`, `billing`,
  `diagnostics`, `healthexamination`, `document`, `prescription`,
  `notification`, `integration`, `appointment`, `portal`, `audit`, `shared`.
- Keep `healthexamination`; Java packages do not contain underscores.
- Appointment owns its persistence; audit owns its contracts, adapters, mappers
  and XML resources. Portal implementation follows supported release use cases;
  do not generate empty layer trees.
- Publish only `AuditWriter` through `audit::recording`, in
  `audit.application.port.out`, for business, authentication and session events.
  Consumers use this public recording boundary; the supported authentication/session
  protocol is described in the current API/security architecture.
- Audit may depend on `shared::id-generator`. Shared must not depend on audit or
  contain audit persistence. Retain shared for generic technical contracts.
- Do not introduce a reporting module. Reports remain queries owned by their
  business modules. None of the existing top-level modules is surplus.
- Verify both the exact module inventory and module boundaries with Spring
  Modulith. Check that audit and appointment types have their documented owner.

## Consequences

Owner amendment — 2026-10-05: one audit contract handles all flows through one
persistence adapter and insert method.
Occurrence time, correlation context, before/after metadata and caller-owned
transaction/compensation behavior remain specific to each flow.

Consumers, MyBatis namespaces, mapper scan configuration, and tests use the owning
packages. The audit persistence adapter writes the clean-slate `audit_events` shape;
[ADR-0013](0013-clean-slate-application-contract.md) extends the clean-slate
contract through the remaining records, SQL, domain, use cases and APIs.
This ownership decision requires no database migration.

Adding modules does not claim that all of their database contracts or use cases
are implemented.

The current inventory includes the 2026-10-05 rename of `identity` to
`accesscontrol` ("User & Access Control"); see
[ADR-0014](0014-session-cookie-redis-login.md).

## References

- [Schema baseline](../../src/main/resources/db/migration/V001__create_schema.sql)
- Supplied clean-slate database design and the owner's Participant terminology
- [Current module contracts](../architecture/02-module-contracts.md)
