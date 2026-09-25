# 04 — Application Layer and Ports

## 1. Role

Application layer orchestrates a business use case.

It is responsible for:

```text
authorization coordination
transaction boundary
loading aggregates
calling domain methods
cross-aggregate consistency
saving changes
audit
outbox/event creation
return DTO mapping
```

It must not contain SQL.

## 2. Use case design

Prefer explicit use cases:

```text
CreateEncounterUseCase
AssignEncounterPhysicianUseCase
CreateOrderRoundUseCase
ReleaseLabResultToPatientUseCase
RepriceHealthExaminationBatchServiceUseCase
CheckInHealthExaminationUseCase
```

Avoid generic command services such as:

```text
ClinicService.update(anything)
CommonCrudService
GenericEntityService
```

## 3. Commands

Commands represent intent.

Example:

```java
public record RepriceHealthExaminationBatchServiceCommand(
    UUID batchId,
    UUID batchServiceId,
    BigDecimal newPrice,
    String reason,
    UUID actorUserId
) {}
```

## 4. Ports

### Persistence port

Application/domain-facing interface:

```text
HealthExaminationBatchRepository
EncounterRepository
PatientRepository
```

### Query port

Read use cases may use dedicated read models:

```text
PhysicianWorklistQuery
PatientTimelineQuery
EncounterDiagnosticProgressQuery
```

### Integration port

Examples:

```text
SmsGateway
FileStoragePort
LabIntegrationPort
AuditPort
OutboxPort
```

## 5. Transactions

Transaction boundaries belong at the application use-case level.

Examples requiring one transaction:

### Corporate repricing

Update together:

```text
HealthExaminationBatchService.negotiated_unit_price
HealthExaminationBatchEmployeeService.unit_price_snapshot
corporate ServiceRequest.unit_price_snapshot
audit
outbox
```

### Result release

Update:

```text
exact result/report version
audit
outbox/notification request
```

SMS delivery itself may occur after commit.

## 6. Idempotency

Use idempotent commands for operations likely to be retried.

Examples:

```text
check-in
result release
external integration message processing
notification creation
import confirmation
```

## 7. Read models

Do not force complex screens to load entire aggregates.

Use dedicated read queries for:

```text
patient quick view
patient timeline
physician worklist
diagnostic progress
corporate batch summary
reporting
```

Read model optimization must not become a second business source-of-truth.
