# Medical Terminology Refactor Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task-by-task. The authoritative product requirements are in `C:\Users\PC\Downloads\ngoc-khanh-clinic-medical-terminology-refactor-plan.md`.

**Goal:** Align the terminology-bearing backend and sibling frontend contracts to canonical medical English without changing clinic behavior.

**Architecture:** Keep the accepted DDD modular-monolith bounded contexts and MyBatis/SQL Server persistence. Rename the Health Examination vertical slice cohesively, preserve the existing `healthcheck` package module identifier, and use an append-only SQL Server migration for persisted schema names. Apply frontend changes in its own repository while preserving unrelated user changes.

**Tech Stack:** Java 25, Spring Boot 4, MyBatis, SQL Server, Flyway, JUnit 5, AssertJ, Next.js 16, TypeScript, Vitest.

**Spec:** `C:\Users\PC\Downloads\ngoc-khanh-clinic-medical-terminology-refactor-plan.md`

## Global Constraints

- Do not use blind global search/replace for semantic result/imaging names.
- Preserve business behavior and existing domain invariants.
- Do not edit already-applied Flyway migrations.
- Do not introduce JPA/Hibernate, FHIR infrastructure, a terminology server, or a new queue state machine.
- `healthcheck` remains the accepted backend module identifier; Health Examination is the canonical business term.
- `lab_*` remains unchanged until a semantic database decision is supported by an explicit migration.
- No public API exists in the backend checkout; do not invent one.

## Review Focus

- A rename must preserve every domain method and aggregate invariant; owned by the health-examination source/test rename task.
- SQL Server migration must preserve FKs, indexes, constraints and existing data; owned by the database migration task.
- `LabResultRecord` versus `LabResultValueRecord` must not collapse distinct semantics; owned by diagnostics review.
- `clsx` and official Vietnamese form wording must not trigger the terminology guard; owned by guard tests.
- Frontend route/type names must stay synchronized with public module exports; the canonical entry point and route are now in place, with the physical legacy folder retained only as an ACL-blocked compatibility location.

### Tasks

1. Phase 0 inventories and canonical glossary — complete in the repository docs.
2. Health Examination Java vertical rename — rename domain, persistence records, repositories, tests and cross-module property names.
3. SQL Server/Flyway cut-over — add a forward migration and update migration/record contract tests.
4. Documentation synchronization — update backend architecture/context references; update baseline Markdown only through its source-document workflow if source DOCX files are available.
5. Terminology guard — add a testable repository script with narrow exclusions and CI wiring appropriate to the backend checkout.
6. Frontend cut-over — complete in the sibling repository; retain and document the legacy compatibility location until its directory ACL permits a physical move.
7. Full verification — run backend and, if modified, frontend checks; report exact blockers.

The plan is intentionally staged so each task can be compiled/tested before the next rename surface is expanded.
