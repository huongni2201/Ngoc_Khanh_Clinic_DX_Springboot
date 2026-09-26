# 10 — Architecture Decisions Summary

This file summarizes accepted architecture direction. Detailed decisions that need history/trade-offs should live in ADRs.

## ADR-level decisions currently in force

### 1. DDD modular monolith

Status: Accepted

Use one deployable backend with strong bounded-context boundaries.

Reason:

```text
clinic scale
lower operational complexity
strong transactional requirements
easier deployment/support
```

### 2. MyBatis instead of JPA/Hibernate

Status: Accepted

Reason:

```text
explicit SQL
predictable, explicitly versioned SQL behavior
clear persistence mapping
schema-driven control
```

### 3. PostgreSQL 17

Status: Accepted

See [ADR-0004](../adr/0004-postgresql-17.md). PostgreSQL 17 is the primary database; persistence and migration tests use PostgreSQL semantics. This supersedes the earlier SQL Server baseline without changing the MyBatis decision.

### 4. Flyway append-only migration history

Status: Accepted

Never rewrite deployed migration history.

### 5. UUID v7 identifiers

Status: Accepted

Persisted primary/relationship identifiers use UUID v7/`uuid`.

### 6. CCCD-only patient identity for current MVP

Status: Accepted

No passport/identity type/fuzzy merge in the current baseline.

### 7. Encounter-centric workflow

Status: Accepted

Operational workflow derives from:

```text
Encounter
EncounterAssignment
OrderRound
ServiceRequest
PaymentAuthorization
Result/Report
```

### 8. No persisted operational queue state

Status: Accepted

Physician worklists and progress are derived read models.

### 9. Result release independent from notification delivery

Status: Accepted

Exact result/report version has its own patient-release state.

SMS is a delivery channel, not visibility state.

### 10. Corporate price snapshots

Status: Accepted

Corporate batch repricing updates all linked snapshots atomically without changing retail catalog price.

### 11. Issued prescription immutability

Status: Accepted

Correction creates a new version; an issued prescription is not overwritten.

## Decisions requiring ADR if changed

Create a new ADR before changing any of:

```text
modular monolith -> microservices
MyBatis -> ORM
PostgreSQL 17 -> another primary DB
UUID v7 -> another ID strategy
CCCD-only identity
separate queue-state persistence
result release semantics
corporate repricing model
prescription versioning
```
