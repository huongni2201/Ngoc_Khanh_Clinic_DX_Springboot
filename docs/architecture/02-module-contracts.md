# Module ownership and published contracts

The application has fourteen business contexts, supporting audit and shared
technical code. Spring Modulith verifies the exact inventory and dependencies.

| Context | Owned tables/concepts |
|---|---|
| accesscontrol | accounts, staff_members, roles, permissions, role_permissions |
| patient | patients, patient_allergies, patient_conditions |
| catalog | departments, rooms, specialties, services, medicines, lab_tests, lab_analytes, lab_test_analytes |
| encounter | encounters, including assigned room/doctor and independent visit lifecycle |
| clinical | vital_signs, encounter_assessments, encounter_assessment_versions, diagnoses, order_rounds, service_requests |
| diagnostics | result_series, result_versions, lab_result_items |
| billing | invoices, invoice_lines, payments, refunds, service_authorizations |
| prescription | prescriptions, prescription_versions, prescription_items |
| appointment | appointments |
| healthexamination | organizations, health_examination_agreements/agreement_services/participants/sessions/session_assignments, health_examination_participant_services, health_examination_records/snapshots/versions/version_items |
| document | files, templates, template_versions, service_template_mappings, issued_representations |
| integration | import_jobs, import_rows, system_connections, external_code_mappings, health_data_submissions, idempotency_keys, outbox_events |
| portal | result_releases, document_releases |
| notification | notification_batches, notifications, notification_attempts |
| audit | append-only audit_events |
| shared | generic ID, errors, HTTP envelope, pagination and technical support; no business/audit persistence |

## Communication

- `accesscontrol::access` publishes the authenticated principal (`UserPrincipal`).
  Session revocation is not published yet; see [ADR-0014](../adr/0014-session-cookie-redis-login.md).
- `catalog` publishes service lookup with current unit price through its query contract.
  Its five management resources use internal read ports and persistence projections;
  command use cases enforce staff/action permissions, expected versions, parent/dependency
  guards and transactional audit. See [catalog management](../api/catalog-management.md).
- `audit::recording` publishes `AuditWriter`. Audit owns its adapters and
  depends on the published shared ID generator, without accesscontrol persistence access.
- `document::master-health-examination-template` exposes effective template-version
  lookup without leaking table records; no current issuance use case calls it.
- `document::contract` publishes `DocumentTypeContract` and `PlaceholderSpec`;
  `document::rendering` publishes `DocumentRenderer` (`render(documentType, values)` →
  PDF bytes plus template version). `healthexamination` depends on `document`, never the
  reverse: it implements the contract (`PeriodicFormBlankContract`) and the document module
  receives every contract through `List<DocumentTypeContract>` injection. Rendering holds no
  database transaction; the loader reads the source in its own short read-only transaction.
  Gotenberg, the DOCX engine and storage adapters stay in `document` infrastructure.
- Bulk printing: `document::rendering` also publishes `RenderJobs` (enqueue/lookup by requester),
  `DocumentValuesSource` (the owning module supplies record values for a job; `healthexamination`
  implements `ExaminationFormValuesSource`) and `RenderSession`. The generic job tables, worker,
  merge and storage live in `document`; `healthexamination` only validates the agreement and enqueues.

`integration::participant-imports` publishes the single-step roster import contract
(`ParticipantImportStore`: idempotency reservation, a VALIDATED job with its rows,
then confirmation) and service-reconciliation import storage.
`integration::agreement-history` publishes agreement-history queries. `catalog::agreement-services` publishes the service lookup used by agreements.
Integration owns the import tables. The Excel reader and template writer
(Apache POI) live in `healthexamination` infrastructure, so POI never reaches
domain or application code. Foreign keys
preserve relational integrity without granting Java modules cross-context access.

Use direct public application contracts for synchronous coordination, in-process
events where decoupling helps, and transactional outbox for durable external work.
Never share MyBatis mappers or repositories between contexts.
