# Catalog Query Package Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Place the published Catalog service query under `application/query` and move its SQL to XML without changing its signature or behavior.

**Architecture:** Keep `catalog::batch-services` as the public Spring Modulith interface. Relocate its Java contract and package annotation, move the query statement into the Catalog XML mapper, then update the existing MyBatis adapter, mapper, health-examination caller, and tests. Keep the current projection and all existing user edits.

**Tech Stack:** Java 25, Spring Boot 4.1.1, MyBatis, Spring Modulith, Maven, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-01-catalog-query-architecture-design.md`

## Global Constraints

- Keep DDD modular-monolith ownership and MyBatis persistence.
- Preserve `@NamedInterface("batch-services")` and `ServiceCatalogQuery.findByIds(Set<UUID>)`.
- Keep SQL out of mapper annotations, including for simple statements; put it in `src/main/resources/mapper/catalog/ServiceCatalogMapper.xml`.
- Preserve the current query fields and runtime behavior; do not change REST APIs or database schema.
- Do not add empty `api` or `domain` packages or speculative Catalog CRUD.
- Preserve all pre-existing working-tree changes, especially the Catalog mapper and adapter diffs.

## Review Focus

- The new package still publishes the `batch-services` named interface — verify with `ModuleVerificationTest`.
- `BatchDraftEditor` uses the moved contract without changing service eligibility or snapshot behavior — verify with `BatchDraftEditorTest` and `HealthExaminationBatchUseCasesTest`.
- XML namespace and statement ID match the mapper interface, and the `foreach` binds each requested UUID — verify through the XML mapper test and bound parameters.
- The XML result mapping still populates the nested `Service` record — verify with the existing PostgreSQL integration path in `HealthExaminationBatchCrudIntegrationTest`.
- No production or test caller retains the old fully qualified name — verify with repository search and full Maven tests.
- The query projection and unrelated dirty files remain unchanged — verify the final scoped diff and working-tree status.

---

### Task 1: Relocate the published query contract

**Files:**
- Move: `src/main/java/com/ngockhanh/clinic/catalog/application/ServiceCatalogQuery.java` to `src/main/java/com/ngockhanh/clinic/catalog/application/query/ServiceCatalogQuery.java`
- Move: `src/main/java/com/ngockhanh/clinic/catalog/application/package-info.java` to `src/main/java/com/ngockhanh/clinic/catalog/application/query/package-info.java`
- Create: `src/main/resources/mapper/catalog/ServiceCatalogMapper.xml`
- Modify: `src/main/java/com/ngockhanh/clinic/catalog/infrastructure/persistence/mapper/ServiceCatalogMapper.java`
- Modify: `src/main/java/com/ngockhanh/clinic/catalog/infrastructure/persistence/repository/MyBatisServiceCatalogQuery.java`
- Modify: `src/main/java/com/ngockhanh/clinic/healthexamination/application/usecase/BatchDraftEditor.java`
- Modify: `src/test/java/com/ngockhanh/clinic/healthexamination/application/usecase/BatchDraftEditorTest.java`
- Modify: `src/test/java/com/ngockhanh/clinic/healthexamination/application/usecase/HealthExaminationBatchUseCasesTest.java`
- Modify: `src/test/java/com/ngockhanh/clinic/healthexamination/infrastructure/persistence/HealthExaminationBatchMapperTest.java`

**Interfaces:**
- Consumes: `ServiceCatalogQuery.findByIds(Set<UUID>)` and nested `Service` record, unchanged.
- Produces: `com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery`, published through `@NamedInterface("batch-services")`; mapper XML statement `ServiceCatalogMapper.findByIds` returns the same nested `Service` projection.

- [x] Change `HealthExaminationBatchMapperTest.projectsOnlyTheCatalogFieldsRequiredByBatchConfiguration` to parse `mapper/catalog/ServiceCatalogMapper.xml` and assert the selected fields plus one bound placeholder per requested ID. Assert the XML resource exists before parsing.
- [x] Run the targeted mapper test to verify RED because the Catalog XML resource does not yet exist.
- [x] Add `ServiceCatalogMapper.xml` with namespace `com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper.ServiceCatalogMapper`, a `findByIds` select, the existing projection, and `<foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>`.
- [x] Move the contract and package annotation; change the Java package declaration and retain the named-interface value exactly. Remove `@Select` and SQL imports from the mapper interface, keeping `@Mapper`, `@Param`, and the existing method signature.
- [x] Update the imports in the adapter, mapper, `BatchDraftEditor`, and both listed batch tests. Do not change business logic.
- [x] Run targeted verification:

  ```powershell
  .\mvnw.cmd '-Dtest=HealthExaminationBatchMapperTest,BatchDraftEditorTest,HealthExaminationBatchUseCasesTest,ModuleVerificationTest' test
  .\mvnw.cmd '-Dtest=HealthExaminationBatchCrudIntegrationTest#addsRemovesServicesAndRollsBackConflictingUpdate' test
  ```

  Expected: all selected tests pass, including XML parsing, XML result mapping, and `ApplicationModules.verify()`. The second command requires Docker/Testcontainers.

  Observed: the first command passed 19 tests; the PostgreSQL 18 second command passed 1 test.

- [x] Format changed Java files with the project formatter and inspect the scoped diff; expected production Java changes are package declarations/imports and removal of annotation SQL, with the same select now in XML.
- [x] Run the repository-required full checks:

  ```powershell
  .\mvnw.cmd test
  .\mvnw.cmd verify
  ```

  Observed: both commands ran 214 tests and failed on the same duplicate
  `db/local/R__batch_mock_actor.sql` Flyway fixture (0 assertion failures,
  1 error). The fixture remains unchanged.
