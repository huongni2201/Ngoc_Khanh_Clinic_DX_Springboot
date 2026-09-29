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

Structured controller input follows `api/request -> application/command` or
`api/request -> application/query`. Simple path identifiers can remain scalar.
Use cases return payload DTOs in `application/response`; controllers consume them
directly and add only HTTP status/envelopes. Map domain data in application, e.g.
with a `from(domain)` factory on an existing response. Void commands need no DTO.

Controllers and use cases use `@Slf4j`: DEBUG for request/read handling and INFO
for meaningful mutations. Log IDs/counts, never full objects or search text that
may contain patient information. A transactional log cannot claim commit success
before commit and does not replace audit.

Prefer `@RequiredArgsConstructor` for final injected dependencies outside domain.
Use Lombok for useful DTO boilerplate; Java records need no generated constructor.
Avoid generated `toString()` exposing sensitive data. Reuse existing files/folders.

In `healthexamination`, organization creation now returns `OrganizationResponse`
as the response envelope payload (including `id`), rather than a bare UUID. GET
and PUT also receive this payload directly from their use cases. HTTP routes and
status codes remain unchanged.

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
HealthExaminationBatchParticipantService.unit_price_snapshot
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
