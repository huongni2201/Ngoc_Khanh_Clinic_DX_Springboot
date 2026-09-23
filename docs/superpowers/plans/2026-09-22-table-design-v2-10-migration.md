# Table Design v2.10 Flyway Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a SQL Server-compatible Flyway baseline migration that creates the 67-table schema defined by `table-design-v2.10.md`, then prove it applies successfully through SQL Server Testcontainers.

**Architecture:** Keep schema ownership in Flyway under `src/main/resources/db/migration`. The migration creates tables in dependency order, defers forward/cyclic foreign keys with `ALTER TABLE`, and defines database invariants, indexes, and rowversion columns from the latest table-design document. Persistence records remain separate from the schema and continue to use `com.ngockhanh.clinic`.

**Tech Stack:** Java 25, Maven, Spring Boot 4.1.1, Flyway, SQL Server 2022+, JUnit 5, AssertJ, Testcontainers SQL Server.

**Spec:** `docs/baseline/table-design-v2.10.md` (version 2.10, baseline 22/09/2026).

## Global Constraints

- Use `com.ngockhanh.clinic`; do not reintroduce `com.nkc`.
- Use SQL Server 2022-compatible DDL only; do not use H2.
- Use Flyway under `src/main/resources/db/migration/`.
- Create a new migration; do not modify an already-applied migration.
- Use plural English snake_case table/column names.
- Use `bigint IDENTITY` primary keys, `uniqueidentifier` public IDs, `datetime2(3)`, `decimal(18,2)`, `nvarchar`, `varchar`, and `rowversion) exactly where the spec defines them.
- Preserve clinical and financial history; do not add hard-delete cascades.
- Do not invent tables, fields, statuses, roles, or relationships outside the latest docs.
- Validate persistence with SQL Server Testcontainers, not H2.

## Review Focus

- Forward and cyclic foreign keys must be created without relying on table order; the migration must complete on a clean SQL Server database.
- `rowversion` columns must be declared as SQL Server concurrency tokens and must not be treated as application-managed timestamps.
- The health-check one-to-one/active-record relationships must be protected by filtered or unique indexes where the docs require them.
- Payment authorization must remain one-to-one with a service request and must reference invoice items without breaking creation order.
- The migration must contain all 67 latest-schema tables and no legacy MVP table/column such as `enterprises` or `identity_number`.

---

### Task 1: Add a migration contract test

**Files:**
- Create: `src/test/java/com/ngockhanh/clinic/infrastructure/migration/TableDesignV210MigrationContractTest.java`
- Read: `docs/baseline/table-design-v2.10.md`
- Read: `src/main/resources/db/migration/V001__create_table_design_v2_10.sql`

**Interfaces:**
- Consumes: the SQL text of `V001__create_table_design_v2_10.sql).
- Produces: deterministic checks for the exact latest table inventory and legacy-schema exclusion.

- [ ] **Step 1: Write the failing test**

Create a JUnit 5 test that reads the migration from the classpath, extracts names from `CREATE TABLE dbo.<name>`, and compares them with this exact set:

```java
private static final Set<String> EXPECTED_TABLES = Set.of(
        "patients", "patient_allergies", "patient_conditions",
        "departments", "rooms", "staff", "staff_department_assignments",
        "diagnosis_catalog", "services", "service_prices", "medications",
        "document_templates", "document_template_versions", "document_template_fields",
        "service_template_mappings", "generated_documents", "generated_document_service_requests",
        "appointments", "encounters", "encounter_assignments", "journeys", "journey_events",
        "vital_signs", "clinical_notes", "encounter_diagnoses",
        "order_rounds", "service_requests",
        "payment_authorizations", "invoices", "invoice_items", "invoice_adjustments", "payments",
        "companies", "company_employees", "health_check_batches",
        "health_check_batch_services", "health_check_batch_employees", "health_check_records",
        "health_check_batch_employee_services", "health_check_import_jobs", "health_check_import_rows",
        "specimens", "specimen_service_requests", "lab_panels", "analytes", "lab_panel_items",
        "analyte_reference_ranges", "lab_results", "lab_result_values", "imaging_studies",
        "diagnostic_reports", "file_attachments",
        "prescriptions", "prescription_items",
        "users", "roles", "permissions", "user_roles", "role_permissions",
        "notifications", "notification_attempts",
        "audit_logs",
        "integration_endpoints", "external_code_mappings", "integration_messages",
        "idempotency_keys", "outbox_events"
);
```

Also assert that the migration contains no `enterprises`, `identity_number`, `user_accounts`, or `nkc` identifier.

- [ ] **Step 2: Run the contract test to verify it fails**

Run:

```powershell
.\mvnw.cmd -Dmaven.repo.local=D:\workspace\Ngoc_Khanh_Clinic_DX\ngoc_khanh_clinic_backend\.m2 -Dtest=TableDesignV210MigrationContractTest test
```

Expected: FAIL because `V001__create_table_design_v2_10.sql` does not exist yet.

- [ ] **Step 3: Keep the test focused**

Use `ClassLoader.getResourceAsStream("db/migration/V001__create_table_design_v2_10.sql")`; do not access a developer-local filesystem path. Normalize SQL identifiers to lowercase before comparison.

- [ ] **Step 4: Re-run after the migration task**

The test must pass with exactly 67 tables and no legacy identifiers.

---

### Task 2: Create the SQL Server Flyway migration

**Files:**
- Create: `src/main/resources/db/migration/V001__create_table_design_v2_10.sql`
- Source: `docs/baseline/table-design-v2.10.md`

**Interfaces:**
- Consumes: all field definitions, constraints, indexes, and core relationships in table-design v2.10.
- Produces: a clean-database SQL Server schema for all 67 tables.

- [ ] **Step 1: Define schema creation order**

Create tables in dependency groups, keeping all columns present before adding forward references:

1. Patient/organization/catalog foundations: `patients`, `departments`, `rooms`, `staff`, `staff_department_assignments`, `diagnosis_catalog`, `lab_panels`, `analytes`, `medications`, `file_attachments`, `companies`, `company_employees`.
2. Document/catalog: `document_templates`, `document_template_versions`, `document_template_fields`, `services`, `service_prices`, `service_template_mappings`.
3. Encounter/clinical: `appointments`, `encounters`, `encounter_assignments`, `journeys`, `journey_events`, `vital_signs`, `clinical_notes`, `encounter_diagnoses`, `health_check_batches`, `health_check_batch_services`, `health_check_batch_employees`, `health_check_records`, `order_rounds`, `service_requests`.
4. Billing/documents/results: `invoices`, `invoice_items`, `invoice_adjustments`, `payments`, `payment_authorizations`, `generated_documents`, `generated_document_service_requests`, `health_check_batch_employee_services`.
5. Diagnostics: `specimens`, `specimen_service_requests`, `lab_panel_items`, `analyte_reference_ranges`, `lab_results`, `lab_result_values`, `imaging_studies`, `diagnostic_reports`.
6. Identity/notification/integration: `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `notifications`, `notification_attempts`, `audit_logs`, `integration_endpoints`, `external_code_mappings`, `integration_messages`, `idempotency_keys`, `outbox_events`, plus `health_check_import_jobs`, `health_check_import_rows`, `prescriptions`, and `prescription_items`.

- [ ] **Step 2: Add every table and column from the document**

For each table, reproduce the exact column order and SQL type from section 4. Preserve explicit `NOT NULL`, `UNIQUE`, and documented defaults. Use `dbo` as the schema and `id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_<table> PRIMARY KEY` as the standard primary key shape.

Use Java-record mappings only as a cross-check; the migration source remains the table-design document.

- [ ] **Step 3: Add foreign keys and uniqueness constraints**

Add the documented ownership relationships, including patient/encounter, company/employee/batch, document template/version/field, service/template, encounter/journey/order/service request, invoice/payment, health-check import, specimen/result, imaging/report, prescription, notification, identity RBAC, audit, and integration mappings.

Use named constraints such as `FK_<table>_<column>` and `UQ_<table>_<columns>`. Defer any FK that references a table created later until an `ALTER TABLE ... ADD CONSTRAINT` block after all tables exist.

Do not add cascade deletes for clinical, financial, audit, or integration history.

- [ ] **Step 4: Add documented indexes and active-record uniqueness**

Implement the indexes named or required by the docs, including unique identificationNumber/patient code, operational encounter/journey/service-request lookups, batch and employee lookups, result/version lookups, and integration idempotency/outbox lookups.

Use a filtered unique index for the documented active health-check record relationship:

```sql
CREATE UNIQUE INDEX UX_health_check_records_active_batch_employee
    ON dbo.health_check_records(health_check_batch_employee_id)
    WHERE health_check_batch_employee_id IS NOT NULL
      AND status NOT IN ('CANCELED');
```

Only add a status predicate when it matches the exact status vocabulary in the latest document; otherwise use the documented uniqueness rule without inventing a new status.

- [ ] **Step 5: Add SQL Server checks that are explicit in the docs**

Add check constraints only for documented invariant values and numeric domains. Do not infer undocumented enum values. In particular, preserve the docs' requirements for identificationNumber non-null/unique, positive monetary quantities where specified, and valid age/reference-range bounds where specified.

- [ ] **Step 6: Run SQL formatting/static checks**

Run the migration contract test and inspect the generated SQL for `GO`, H2-only syntax, unbound placeholders, and legacy identifiers:

```powershell
rg -n "\bGO\b|AUTO_INCREMENT|SERIAL|identity_number|enterprises|user_accounts|\bnkc\b" src/main/resources/db/migration/V001__create_table_design_v2_10.sql
```

Expected: no matches.

---

### Task 3: Add SQL Server Testcontainers migration verification

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/ngockhanh/clinic/infrastructure/migration/SqlServerMigrationIntegrationTest.java`

**Interfaces:**
- Consumes: `V001__create_table_design_v2_10.sql) through Flyway.
- Produces: an automated clean-database migration check using SQL Server.

- [ ] **Step 1: Add only the required test dependencies**

Add test-scoped `org.testcontainers:junit-jupiter` and `org.testcontainers:mssqlserver`. Do not add H2 or another persistence framework.

- [ ] **Step 2: Write the integration test**

Use `MSSQLServerContainer<>("2022-latest")`, `@Testcontainers`, and `@DynamicPropertySource` to provide `spring.datasource.url`, `spring.datasource.username`, and `spring.datasource.password`. Run Flyway programmatically against the container and assert:

```java
assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
assertThat(jdbcTemplate.queryForObject(
        "select count(*) from sys.tables where schema_id = schema_id('dbo')",
        Integer.class
)).isEqualTo(67);
```

Also query `sys.columns` for `patients.identificationNumber`, `patients.row_version`, `health_check_records.shs_code`, `service_requests.unit_price_snapshot`, and `outbox_events.payload_json` to verify the representative SQL Server types.

- [ ] **Step 3: Run with Docker available**

Run:

```powershell
.\mvnw.cmd -Dmaven.repo.local=D:\workspace\Ngoc_Khanh_Clinic_DX\ngoc_khanh_clinic_backend\.m2 -Dtest=SqlServerMigrationIntegrationTest test
```

Expected: the clean SQL Server container applies one migration and reports 67 user tables.

If Docker is unavailable, report that the SQL Server integration check was not run; do not substitute H2.

---

### Task 4: Make the application context test use the real test database

**Files:**
- Modify: `src/test/java/com/ngockhanh/clinic/NgocKhanhClinicBackendApplicationTests.java`
- Create: `src/test/resources/application-test.yaml` only if the test needs non-secret Flyway/test settings.

**Interfaces:**
- Consumes: SQL Server Testcontainers configuration from Task 3.
- Produces: a context-load test that initializes Flyway against SQL Server instead of failing with “Failed to determine a suitable driver class”.

- [ ] **Step 1: Add the SQL Server container to the context test**

Annotate the test with `@Testcontainers) and `@ActiveProfiles("test")), expose the container JDBC properties with `@DynamicPropertySource`, and keep credentials limited to the container defaults.

- [ ] **Step 2: Run the context test**

Run:

```powershell
.\mvnw.cmd -Dmaven.repo.local=D:\workspace\Ngoc_Khanh_Clinic_DX\ngoc_khanh_clinic_backend\.m2 -Dtest=NgocKhanhClinicBackendApplicationTests test
```

Expected: Spring context starts, Flyway applies the migration, and `contextLoads` passes.

---

### Task 5: Verify the complete change

**Files:**
- No new files; verify Tasks 1–4 together.

- [ ] **Step 1: Run focused tests**

```powershell
.\mvnw.cmd -Dmaven.repo.local=D:\workspace\Ngoc_Khanh_Clinic_DX\ngoc_khanh_clinic_backend\.m2 -Dtest=PersistenceRecordContractTest,TableDesignV210MigrationContractTest,SqlServerMigrationIntegrationTest test
```

- [ ] **Step 2: Run all tests**

```powershell
.\mvnw.cmd -Dmaven.repo.local=D:\workspace\Ngoc_Khanh_Clinic_DX\ngoc_khanh_clinic_backend\.m2 test
```

- [ ] **Step 3: Run package verification**

```powershell
.\mvnw.cmd -Dmaven.repo.local=D:\workspace\Ngoc_Khanh_Clinic_DX\ngoc_khanh_clinic_backend\.m2 verify
```

- [ ] **Step 4: Confirm no legacy package/schema identifiers**

```powershell
rg -n "com\.nkc\.clinic|<groupId>com\.nkc</groupId>|identity_number|enterprises|user_accounts" src pom.xml
```

Expected: no matches.

- [ ] **Step 5: Review the final diff**

Confirm the final report includes the migration file, affected modules, test results, Docker availability, and any remaining mapper/repository work.

