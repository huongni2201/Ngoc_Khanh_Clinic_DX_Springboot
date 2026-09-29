# 01 — System Architecture

## 1. Architectural style

The backend is a **DDD modular monolith**.

It combines:

- Domain-Driven Design
- Modular Monolith
- Clean Architecture dependency direction
- Ports and Adapters
- Spring Modulith
- MyBatis persistence
- PostgreSQL 18
- Flyway migrations

The system is deployed as one Spring Boot application but organized into isolated business modules.

## 2. High-level structure

```text
Client / Frontend
       |
       v
HTTP / REST adapters
       |
       v
Application Use Cases
       |
       v
Domain Model
       |
       +-------------------------+
       |                         |
       v                         v
Persistence Ports          Integration Ports
       |                         |
       v                         v
MyBatis Adapters            External Adapters
       |                         |
       v                         v
PostgreSQL 18             SMS / LIS / PACS / etc.
```

## 3. Dependency direction

Allowed:

```text
api -> application -> domain
infrastructure -> domain (repository adapters)
infrastructure -> application (application-port adapters)
```

Application may depend on domain and public ports/contracts.

Domain must depend on none of:

```text
Spring
MyBatis
JDBC
PostgreSQL
HTTP
Jackson
external SDKs
```

## 4. Module organization

Each bounded context should follow:

```text
<context>/
├── api/
│   ├── controller/
│   └── request/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── enums/
│   ├── valueobject/
│   ├── repository/
│   └── exception/
├── application/
│   ├── usecase/
│   ├── command/
│   ├── query/
│   ├── port/
│   └── response/
└── infrastructure/
    └── persistence/
        ├── repository/
        ├── mapper/
        ├── record/
        └── converter/
```

This structure follows `healthexamination`. Add domain events/services and infrastructure integration/configuration only when required. Do not force empty directories.

Structured HTTP input maps from `api/request` to application commands/queries.
Use cases return `application/response` payloads; controllers add HTTP status and
the shared envelope. Domain repository ports are implemented by MyBatis adapters.
SQL XML files live under `src/main/resources/mapper/health-examination/` for this module.

## 5. Current bounded contexts

```text
identity
patient
catalog
encounter
clinical
billing
diagnostics
healthexamination
document
prescription
notification
integration
shared
```

## 6. Persisted state policy

Persist state when:

- it is legally or operationally required;
- it cannot be safely reconstructed;
- it represents a business fact;
- it is needed for concurrency, audit or integration.

Do not persist derived workflow state only for UI convenience.

Examples that should be derived:

```text
physician worklist position
waiting-for-conclusion state
diagnostic progress
duplicate operational stage state
```

These are derived from Encounter, assignments, ServiceRequest, payment authorization and result state.

## 7. Core workflow model

The operational model is **Encounter-centric** and **ServiceRequest-driven**.

Main concepts:

```text
Patient
  -> Encounter
      -> EncounterAssignment
      -> OrderRound
          -> ServiceRequest
              -> payment authorization
              -> diagnostic execution
              -> result/report
```

Health-examination corporate workflow adds:

```text
Organization
  -> HealthExaminationParticipant
  -> HealthExaminationBatch
      -> HealthExaminationBatchService
      -> HealthExaminationBatchParticipant
          -> HealthExaminationBatchParticipantService
          -> prepared Patient + Encounter + HealthExaminationRecord/SHS
```

## 8. Non-goals

The current architecture does not use:

```text
microservices
JPA/Hibernate
event sourcing
persisted queue-stage state machine
generic repository abstraction for every entity
CQRS split databases
H2 as a substitute for database integration tests
```

Any future change requires an ADR.
