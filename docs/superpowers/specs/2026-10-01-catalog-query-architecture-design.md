# Catalog Query Architecture Design

**Date:** 2026-10-01  
**Status:** Approved for implementation

## Context

The Catalog module currently publishes `ServiceCatalogQuery` from
`com.ngockhanh.clinic.catalog.application` as the Spring Modulith named
interface `batch-services`. `healthexamination` uses this query in
`BatchDraftEditor` to load service code, name, active state, and health-check
eligibility. The implementation is already separated into a MyBatis adapter and
mapper under `catalog.infrastructure.persistence`, but the mapper SQL is still
embedded in a Java annotation.

Catalog currently has no REST endpoint or Catalog-owned domain invariant in this
flow. The service eligibility rule is enforced by the health-examination use
case. Project architecture documents organize application queries under
`application/query` and require layers only when the module needs them.

## Decision

Move `ServiceCatalogQuery` and its `package-info.java` into
`catalog.application.query`. Keep the named-interface value `batch-services`,
the query method, and its nested `Service` read model unchanged. Move the mapper
SQL into `src/main/resources/mapper/catalog/ServiceCatalogMapper.xml`, including
this simple query, and leave the Java mapper interface with only its MyBatis
annotations and method signature. Update the MyBatis adapter, mapper,
health-examination consumer, and affected tests to use the new Java package.

The query flow remains:

```text
healthexamination BatchDraftEditor
  -> catalog.application.query.ServiceCatalogQuery
  -> catalog.infrastructure.persistence.repository.MyBatisServiceCatalogQuery
  -> catalog.infrastructure.persistence.mapper.ServiceCatalogMapper
  -> mapper/catalog/ServiceCatalogMapper.xml
  -> services table
```

Keep Catalog's current persistence mapper, adapter, and table records in
`infrastructure.persistence`. Preserve the current SQL projection and behavior,
including the user's existing working-tree edits in `ServiceCatalogMapper` and
`MyBatisServiceCatalogQuery`.

## Scope

In scope:

- Relocate the published query contract and named-interface annotation.
- Move the query statement into the Catalog XML mapper, even though the SQL is
  small.
- Update imports at the existing consumer, adapter, mapper, and test call sites.
- Verify the Spring Modulith named interface and existing batch-draft behavior.
- Verify the XML mapper builds a safely parameterized `IN` query.

Out of scope:

- Adding Catalog REST endpoints, CRUD use cases, domain aggregates, or repository
  ports for catalog tables that have no current use case.
- Changing service selection rules, query fields, SQL behavior, schema, or
  persistence records.
- Adding a database migration or changing the HTTP API.

## Compatibility and consequences

- `catalog::batch-services` remains the published Spring Modulith interface.
- The Java package of `ServiceCatalogQuery` changes; the current in-repository
  consumer and tests will be updated in the same change.
- There is no Catalog HTTP API or schema change.
- No ADR is required because module ownership, module dependencies, and the
  published named-interface identifier remain unchanged.
- `api` and `domain` packages remain absent until a Catalog use case requires
  them; empty layers would add no behavior or boundary.

## Verification

- Existing batch-draft use-case tests continue to cover service eligibility and
  snapshot behavior.
- The mapper test parses `mapper/catalog/ServiceCatalogMapper.xml` and checks the
  service projection and bound `IN` parameters.
- `ModuleVerificationTest` continues to verify the named interface and module
  dependency.
- The health-examination batch-create PostgreSQL integration path verifies the
  Document template lookup and Shared audit insert after their SQL moves.
- The mapper-shape test parses all Catalog, Document, and Shared mapper XML
  files touched by this change.
- Run the Maven test and verify lifecycle, then format the changed Java files
  using the project formatter.

## Follow-up scope: Document and Shared

A follow-up audit found the same two patterns elsewhere: the Document module
publishes `MasterHealthExaminationTemplateQuery` from the `application` root
and embeds its lookup SQL in `@Select`; the Shared module embeds the
`AuditLogMapper.insert` statement in `@Insert`. Apply the same package and SQL
placement rules:

- Move `MasterHealthExaminationTemplateQuery` and its named-interface package
  annotation into `document.application.query`, preserving the named-interface
  identifier `master-health-examination-template` and the method contract.
- Move the Document lookup SQL into
  `src/main/resources/mapper/document/MasterHealthExaminationTemplateMapper.xml`.
- Move the Shared audit insert SQL into
  `src/main/resources/mapper/shared/AuditLogMapper.xml`; leave the distinct,
  already-XML `AuditMapper` auth-audit path unchanged.
- Do not add empty API or domain packages to Document; this is still a query-only
  integration for the current use case.

The existing health-examination batch-create integration path exercises both
Document template lookup and Shared audit persistence against PostgreSQL 18.
The mapper-shape test will parse both new XML files and check their bound
statements.

## Acceptance criteria

1. `ServiceCatalogQuery` is in `catalog.application.query` and remains published
   as `batch-services`.
2. The `healthexamination` consumer compiles against the moved contract.
3. `ServiceCatalogMapper.java` contains no SQL annotation; the XML statement
   preserves the query projection, parameter binding, and behavior.
4. The module verification and relevant Maven checks pass.
5. Existing user changes outside this relocation remain intact.
6. `MasterHealthExaminationTemplateQuery` is in `document.application.query` and
   remains published as `master-health-examination-template`.
7. Document and Shared mapper interfaces contain no SQL annotations; their XML
   statements preserve current filters, columns, and bound parameters.
