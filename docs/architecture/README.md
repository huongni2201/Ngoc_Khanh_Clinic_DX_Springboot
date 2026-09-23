# Backend Architecture Documentation

Project: Ngọc Khánh Clinic Digital Transformation  
Architecture: DDD Modular Monolith + Clean Architecture + Ports/Adapters  
Runtime: Java 25, Spring Boot 4.x, SQL Server, MyBatis, Flyway, Spring Modulith

## Source-of-truth order

When documents conflict, follow this order:

1. `PROJECT_RULES.md`
2. Accepted ADRs
3. `docs/architecture/*`
4. Generated Markdown baseline mirrors in `docs/baseline/` for affected requirements, use cases, and schema contracts
5. Corresponding FINAL DOCX when a mirror is missing or formatting/visual reference matters
6. Existing implementation

Architecture documents do not override business rules.
Markdown mirrors are generated from the FINAL DOCX files by `scripts/docs/sync_baseline_markdown.py`; update the DOCX source and regenerate the mirrors.

## Architecture documents

- `01-system-architecture.md` — overall backend architecture
- `02-module-boundaries.md` — bounded contexts and dependency rules
- `03-domain-modeling-rules.md` — Aggregate/Entity/Value Object rules
- `04-application-and-ports.md` — use cases, ports and transaction boundaries
- `05-persistence-and-database.md` — SQL Server, MyBatis, Flyway, UUID, schema rules
- `06-integration-and-events.md` — integrations, outbox, notifications and external systems
- `07-security-and-audit.md` — authentication, authorization and audit rules
- `08-testing-and-quality-gates.md` — unit, integration, module and migration testing
- `09-runtime-and-deployment.md` — configuration and deployment assumptions
- `10-architecture-decisions-summary.md` — current architecture decisions

## Core principles

- Package by bounded context, not by technical layer globally.
- Domain model must not depend on Spring, MyBatis, SQL, HTTP or infrastructure.
- Application layer owns use-case orchestration and transaction boundaries.
- Infrastructure implements ports.
- No direct cross-module access to another module's mapper or persistence record.
- Persist only business state that cannot be safely derived.
- Operational progress is derived from Encounter and related records, not stored as a parallel stage model.
- Schema changes are append-only through Flyway migrations.
- UUID v7 is the persisted identifier strategy.
- CCCD is the patient business identity in the current MVP.
