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
- SQL Server
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
SQL Server                SMS / LIS / PACS / etc.
```

## 3. Dependency direction

Allowed:

```text
infrastructure -> application -> domain
```

Application may depend on domain and public ports/contracts.

Domain must depend on none of:

```text
Spring
MyBatis
JDBC
SQL Server
HTTP
Jackson
external SDKs
```

## 4. Module organization

Each bounded context should follow:

```text
<context>/
├── domain/
│   ├── aggregate/
│   ├── entity/
│   ├── valueobject/
│   ├── event/
│   ├── service/
│   └── exception/
├── application/
│   ├── usecase/
│   ├── command/
│   ├── query/
│   ├── port/
│   └── dto/
└── infrastructure/
    ├── web/
    ├── persistence/
    ├── integration/
    └── configuration/
```

Do not force empty directories. Create only what the module needs.

## 5. Current bounded contexts

```text
identity
patient
catalog
encounter
clinical
billing
diagnostics
healthcheck
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
doctor worklist position
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

Health-check corporate workflow adds:

```text
Company
  -> HealthCheckBatch
      -> HealthCheckBatchService
      -> HealthCheckBatchEmployee
          -> HealthCheckBatchEmployeeService
          -> HealthCheckRecord
          -> Encounter
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
H2 for SQL Server behavior testing
```

Any future change requires an ADR.
