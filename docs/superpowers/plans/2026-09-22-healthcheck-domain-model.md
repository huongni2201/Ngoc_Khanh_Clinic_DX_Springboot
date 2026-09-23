# Health Check Domain Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and unit test the framework-free `healthcheck` domain model for corporate adult health checks.

**Architecture:** Keep six aggregate roots separate by consistency boundary: Company, CompanyEmployee, HealthCheckBatch, HealthCheckBatchEmployee, HealthCheckRecord, and HealthCheckImportJob. Domain ports expose aggregate persistence contracts; application orchestration across Patient, Encounter, ServiceRequest, security, and audit remains for later work.

**Tech Stack:** Java 25, Spring Boot 4.x project, Maven, JUnit 5, AssertJ; no Spring or MyBatis dependency in domain classes.

**Spec:** `docs/superpowers/specs/2026-09-22-healthcheck-domain-model-design.md`.

## Implemented Package Layout

This plan originally used `healthcheck.domain.model`. The implementation has since been refactored to the following package layout; the historical file paths in individual plan tasks should be read as superseded:

```text
healthcheck/domain/
├── aggregate/    aggregate roots and child entities
├── valueobject/  immutable domain values
├── enums/        state and type enums
├── port/
└── exception/
```

Child entities stay in `aggregate` with their aggregate roots so their package-private state transitions cannot be invoked outside the aggregate.

## Global Constraints

- Business baseline: `docs/baseline/requirement-v2.4.md`, `docs/baseline/use-case-v2.5.md`, `docs/baseline/table-design-v2.10.md`.
- Package root: `com.ngockhanh.clinic`; bounded context: `healthcheck`.
- Existing `healthcheck.infrastructure.persistence.record` classes are persistence models and are not edited or imported into the domain.
- The repository has unrelated uncommitted changes; do not stage or commit them.
- No API, Flyway migration, mapper, application service, or new dependency in this plan.
- Treat uniqueness across stored aggregates as a repository/application responsibility backed by documented SQL constraints; domain protects duplicates within a loaded aggregate.
- Use `./mvnw.cmd` on Windows; run focused tests, then `test` and `verify` before reporting completion.

## Review Focus

- Missing or malformed identificationNumber in an imported row must fail without numeric coercion; Task 1 pins this in `IdentificationNumberTest`. The baseline does not specify a complete identificationNumber syntax algorithm, so do not invent a checksum or fixed format here.
- A birthday one day after actual check-in must be rejected even if the planned date is the 18th birthday; Task 3 pins this in `HealthCheckRecordTest`. Leap-day interpretation needs a confirmed business rule.
- A Batch Employee from another batch cannot receive a service even if its numeric service ID matches; Task 4 pins this in `HealthCheckBatchEmployeeTest`.
- Repeated assignment of the same batch service cannot create another ServiceRequest reference; Task 4 pins this in `HealthCheckBatchEmployeeTest`.
- Repeated check-in must preserve SHS and the first actual examination date; Task 3 pins this in `HealthCheckRecordTest`.

---

### Task 1: Shared domain values and Company roster

**Files:**
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/IdentificationNumber.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/Company.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/CompanyEmployee.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/exception/HealthCheckDomainException.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/exception/PatientRelinkForbidden.java`
- Create: `src/test/java/com/ngockhanh/clinic/healthcheck/domain/model/IdentificationNumberTest.java`
- Create: `src/test/java/com/ngockhanh/clinic/healthcheck/domain/model/CompanyEmployeeTest.java`

**Interfaces:**
- `IdentificationNumber.of(String value): IdentificationNumber`, `value(): String`.
- `CompanyEmployee.linkPatient(long patientId): void`; same-ID retry is harmless, different-ID relink throws `PatientRelinkForbidden`.
- `Company` holds one primary contact and validates required code, name, contact name, and phone.

- [ ] Write `IdentificationNumberTest`: `IdentificationNumber.of("012345678901").value()` remains a String with leading zero; null, blank, non-digits, and values longer than the schema's `varchar(20)` are rejected. Do not add unmentioned checksum or exact-length rules.
- [ ] Run `./mvnw.cmd -Dtest=IdentificationNumberTest test`; expect red because the type does not exist.
- [ ] Implement `IdentificationNumber` and minimal domain exceptions with no logging of identificationNumber values.
- [ ] Write `CompanyEmployeeTest`: unlinked roster member accepts one Patient ID, same-ID repeat preserves it, different-ID repeat throws `PatientRelinkForbidden`; construction does not require a Patient ID.
- [ ] Implement `Company` and `CompanyEmployee` with immutable company identity and explicit roster update method that preserves an existing Patient link.
- [ ] Run `./mvnw.cmd -Dtest=IdentificationNumberTest,CompanyEmployeeTest test`; expect green.

### Task 2: Batch scope and price

**Files:**
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckBatch.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckBatchService.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/BatchStatus.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/ExaminationSite.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/Money.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/exception/BatchConfigurationLocked.java`
- Create: `src/test/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckBatchTest.java`

**Interfaces:**
- `HealthCheckBatch.addService(HealthCheckBatchService service): void` rejects duplicate `serviceId` and non-DRAFT status.
- `HealthCheckBatch.markReady(): void` validates planned site and template versions needed for the selected services.
- `HealthCheckBatchService.reprice(Money price): void` is reachable only through a batch-level command with a reason and permissible batch state; no employee-level price command exists.
- `Money` uses `BigDecimal` with explicit `VND` currency and rejects negative amounts.

- [ ] Write `HealthCheckBatchTest` for DRAFT add, duplicate service, READY lock, missing COMPANY address, and common batch-service price. Include a test that FINALIZED/CLOSED cannot reprice without authorized reopen state; the domain does not grant authority itself.
- [ ] Run `./mvnw.cmd -Dtest=HealthCheckBatchTest test`; expect red.
- [ ] Implement the batch and value types using only statuses and site values listed in table-design-v2.10. Keep template version IDs as references; do not query the document module here.
- [ ] Run `./mvnw.cmd -Dtest=HealthCheckBatchTest test`; expect green.

### Task 3: SHS and administrative visit snapshot

**Files:**
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckRecord.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/AdministrativeSnapshot.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/ShsCode.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckRecordStatus.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/exception/AdultEligibilityViolation.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/exception/ImmutableHealthCheckSnapshot.java`
- Create: `src/test/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckRecordTest.java`

**Interfaces:**
- `HealthCheckRecord.prepare(ShsCode shs, long patientId, long encounterId, AdministrativeSnapshot snapshot, LocalDate plannedDate, long masterTemplateVersionId): HealthCheckRecord`.
- `HealthCheckRecord.checkIn(LocalDate actualDate): void` checks age and preserves existing identity on same-date retry; conflicting repeat is rejected.
- `HealthCheckRecord.snapshot(): AdministrativeSnapshot` returns the stored immutable value; no mutator changes issued print data.

- [ ] Write `HealthCheckRecordTest` for missing required snapshot fields, age 17 on planned date, turning 18 on planned date, underage actual check-in, immutable SHS/snapshot, and repeated check-in. Defer leap-day interpretation until the business rule is confirmed.
- [ ] Run `./mvnw.cmd -Dtest=HealthCheckRecordTest test`; expect red.
- [ ] Implement typed snapshot fields from table-design-v2.10 and age calculation using dates, without reading current Patient fields at reprint time.
- [ ] Run `./mvnw.cmd -Dtest=HealthCheckRecordTest test`; expect green.

### Task 4: Batch employee and doctor-selected assignment

**Files:**
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckBatchEmployee.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckBatchEmployeeService.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/BatchEmployeeStatus.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/exception/ServiceOutsideBatchScope.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/exception/DuplicateEmployeeServiceAssignment.java`
- Create: `src/test/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckBatchEmployeeTest.java`

**Interfaces:**
- `HealthCheckBatchEmployee.assign(HealthCheckBatchService service, long serviceRequestId): void` checks matching batch ID and duplicate batch-service ID, copies the current negotiated price, and requires a ServiceRequest ID.
- `HealthCheckBatchEmployee.markBillable(long serviceRequestId, boolean completedSuccessfully): void` rejects false completion evidence.
- `HealthCheckBatchEmployeeService.reprice(Money newPrice): void` is called only by the later uniform-repricing orchestration, not a public employee price override.

- [ ] Write `HealthCheckBatchEmployeeTest` for distinct employee subsets, foreign-batch service, duplicate assignment, required ServiceRequest reference, initial non-billable state, successful completion, and canceled/unperformed non-billable state.
- [ ] Run `./mvnw.cmd -Dtest=HealthCheckBatchEmployeeTest test`; expect red.
- [ ] Implement the aggregate and child entity; do not store medical result or execution status in this module.
- [ ] Run `./mvnw.cmd -Dtest=HealthCheckBatchEmployeeTest test`; expect green.

### Task 5: Import lifecycle and repository ports

**Files:**
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckImportJob.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckImportRow.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/ImportType.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/model/ImportStatus.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/port/CompanyRepository.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/port/CompanyEmployeeRepository.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/port/HealthCheckBatchRepository.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/port/HealthCheckBatchEmployeeRepository.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/port/HealthCheckRecordRepository.java`
- Create: `src/main/java/com/ngockhanh/clinic/healthcheck/domain/port/HealthCheckImportJobRepository.java`
- Create: `src/test/java/com/ngockhanh/clinic/healthcheck/domain/model/HealthCheckImportJobTest.java`

**Interfaces:**
- Each repository has `Optional<Aggregate> findById(long id)` and `void save(Aggregate aggregate)`; add only exact lookup methods required by documented uniqueness or idempotency, such as `findByShsCode(ShsCode code)` and `findByBatchAndEmployee(long batchId, long companyEmployeeId)`.
- `HealthCheckImportJob.validateRows(List<HealthCheckImportRow> rows): void` preserves row numbers and structured error codes.
- `HealthCheckImportJob.confirm(): void` requires validated rows and changes only import state; roster persistence belongs to a later application command.

- [ ] Write `HealthCheckImportJobTest` for duplicate row numbers, invalid row blocking confirmation, valid employee-list confirmation, and no Patient/Encounter reference or creation behavior in the import aggregate.
- [ ] Run `./mvnw.cmd -Dtest=HealthCheckImportJobTest test`; expect red.
- [ ] Implement import model and repository interfaces with domain types only. Do not define result-finalization permissions while the baseline marks that contract unresolved.
- [ ] Run `./mvnw.cmd -Dtest=HealthCheckImportJobTest test`; expect green.

### Task 6: Verification and boundary review

**Files:**
- Review: all `src/main/java/com/ngockhanh/clinic/healthcheck/domain/**`
- Review: all `src/test/java/com/ngockhanh/clinic/healthcheck/domain/**`

**Interfaces:** No new interface; verify the published domain surface.

- [ ] Run `./mvnw.cmd -Dtest=IdentificationNumberTest,CompanyEmployeeTest,HealthCheckBatchTest,HealthCheckRecordTest,HealthCheckBatchEmployeeTest,HealthCheckImportJobTest test`; expect all focused tests green.
- [ ] Run `./mvnw.cmd test`; record the actual result and any Docker/Testcontainers limitation.
- [ ] Run `./mvnw.cmd verify`; record the actual result and any Docker/Testcontainers limitation.
- [ ] Check domain imports with `rg -n "org\.springframework|org\.apache\.ibatis|infrastructure|\.api\." src/main/java/com/ngockhanh/clinic/healthcheck/domain`; expect no matches.
- [ ] Review every planned invariant against the spec and report any deferred application-level test separately.
