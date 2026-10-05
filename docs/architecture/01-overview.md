# Clean-slate backend architecture

The owner selected the clean-slate database/application contract on 2026-10-04.
This documentation describes the current system directly; historical architecture
and decision records are not implementation authority.

## Sources and stack

Use `src/main/resources/db/migration/V001__create_clean_slate_schema.sql` for the
physical schema and the supplied clean-slate design for business workflows. Keep
Participant terminology and one public SQL schema. These documents carry that
mapping into the current Java/API contracts.

Java 25, Spring Boot 4, Spring Framework 7, PostgreSQL 18, MyBatis, Spring Modulith,
Maven and Flyway form one deployable modular monolith. No ORM or microservice
split is introduced. Every business datum has one owning module.

## Layers

```text
HTTP API -> application use cases -> domain
infrastructure -> domain repository ports / application ports
```

Each context uses `api`, `application`, `domain`, `infrastructure` only where
needed. Root package is `com.ngockhanh.clinic`; Java module name is
`healthexamination`, without underscores. API requests map to application
commands/queries. Use cases return application responses; controllers add status
and the shared envelope. Infrastructure implements persistence and external ports.

Domain has no Spring, MyBatis, JDBC, HTTP, Jackson or external SDK dependency.
Application owns transaction scope, authorization orchestration, audit and
cross-aggregate coordination. Controllers own transport validation, never SQL or
transactions. Infrastructure cannot reach another context's internal tables,
mappers, records or aggregates; use published application contracts instead.

## Implemented migration scope

Existing account authentication and organization/batch workflows
use the clean-slate schema. Infrastructure represents all 64 schema tables in
owning modules. Record presence does not claim all clinic workflows or HTTP APIs
are implemented. New clinical, payment, release or rendering operations require
explicit use-case contracts and tests, not generated CRUD scaffolding.

Derive operational progress and totals from Encounter, ServiceRequest,
ServiceAuthorization, ResultVersion and roster data. No queue-stage, report
summary, printer or generated-document schema is introduced.

See [module contracts](02-module-contracts.md), [domain workflows](03-domain-and-workflows.md),
[persistence](04-persistence.md), [API/security](05-api-and-security.md), and
[verification/operations](06-testing-and-operations.md).
