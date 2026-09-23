# 02 — Module Boundaries

## 1. Goal

Bounded contexts must remain independently understandable and testable.

One module must not reach into another module's persistence internals.

## 2. Allowed dependencies

A module may depend on:

- its own domain/application/infrastructure;
- `shared` primitives that are truly generic;
- another module's explicitly published application contract;
- integration/domain events intended for cross-module communication.

## 3. Forbidden dependencies

Never import another module's:

```text
MyBatis mapper
persistence record
repository adapter
SQL-specific DTO
internal aggregate implementation
private application service
```

Forbidden example:

```text
healthcheck -> billing.infrastructure.persistence.PaymentMapper
```

Preferred:

```text
healthcheck -> billing.application.port.PaymentAuthorizationQuery
```

or published event/contract.

## 4. Recommended responsibilities

### identity

Owns:

```text
users
roles
permissions
user_roles
role_permissions
authentication principal mapping
```

### patient

Owns:

```text
Patient
CCCD identity rule
patient allergies
patient conditions
patient profile
```

### catalog

Owns:

```text
departments
rooms
staff catalog view
services
service prices
medications
diagnosis catalog
```

### encounter

Owns:

```text
Encounter
EncounterAssignment
appointment
visit lifecycle
```

### clinical

Owns:

```text
vital signs
clinical notes
encounter diagnoses
doctor clinical workflow contracts
```

### billing

Owns:

```text
PaymentAuthorization
Invoice
InvoiceItem
Payment
billing state and payment rules
```

### diagnostics

Owns:

```text
Specimen
LabResult
LabResultValue
ImagingStudy
DiagnosticReport
result finalization
result release
```

### healthcheck

Owns:

```text
Company
CompanyEmployee
HealthCheckBatch
HealthCheckBatchService
HealthCheckBatchEmployee
HealthCheckBatchEmployeeService
HealthCheckRecord
HealthCheckImportJob
HealthCheckImportRow
corporate pricing rules
```

### document

Owns:

```text
DocumentTemplate
DocumentTemplateVersion
DocumentTemplateField
GeneratedDocument
rendering metadata
```

### prescription

Owns:

```text
Prescription
PrescriptionItem
prescription version chain
```

### notification

Owns:

```text
Notification
NotificationAttempt
SMS delivery orchestration
```

### integration

Owns:

```text
IntegrationEndpoint
ExternalCodeMapping
IntegrationMessage
IdempotencyKey
OutboxEvent
external connector infrastructure
```

## 5. Shared module

`shared` must stay small.

Allowed:

```text
base domain exception
clock abstraction
UUID generator abstraction
common pagination model
generic money/value primitives only if semantically shared
```

Avoid putting clinic-specific business rules into `shared`.

## 6. Cross-module communication

Use one of:

1. Published application port.
2. Published query contract.
3. Domain/integration event.
4. Stable module API.

Choose synchronous calls for immediate consistency needs.

Choose events/outbox when eventual processing is acceptable.

## 7. Spring Modulith verification

Maintain a module verification test.

It must fail when:

- forbidden module dependencies appear;
- cycles are introduced;
- internal types are accessed across boundaries.
