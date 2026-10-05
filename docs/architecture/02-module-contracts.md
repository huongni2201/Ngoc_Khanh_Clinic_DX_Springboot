# Module ownership and published contracts

The application has fourteen business contexts, supporting audit and shared
technical code. Spring Modulith verifies the exact inventory and dependencies.

| Context | Owned tables/concepts |
|---|---|
| identity | accounts, staff_members, roles, permissions, account_roles, role_permissions |
| patient | patients, patient_allergies, patient_conditions |
| catalog | departments, rooms, specialties, services, medicines, lab_tests, lab_analytes, lab_test_analytes |
| encounter | encounters, including assigned room/doctor and independent visit lifecycle |
| clinical | vital_signs, encounter_assessments, encounter_assessment_versions, diagnoses, order_rounds, service_requests |
| diagnostics | result_series, result_versions, lab_result_items |
| billing | invoices, invoice_lines, payments, refunds, service_authorizations |
| prescription | prescriptions, prescription_versions, prescription_items |
| appointment | appointments |
| healthexamination | organizations, health_examination_batches/days/services/participants, health_examination_participant_services, health_examination_records/snapshots/versions/version_items |
| document | files, templates, template_versions, service_template_mappings, issued_representations |
| integration | import_jobs, import_rows, system_connections, external_code_mappings, health_data_submissions, idempotency_keys, outbox_events |
| portal | result_releases, document_releases |
| notification | notification_batches, notifications, notification_attempts |
| audit | append-only audit_events |
| shared | generic ID, errors, HTTP envelope, pagination and technical support; no business/audit persistence |

## Communication

- `identity::access` publishes the authenticated principal. `identity::sessions`
  publishes session revocation; revoke snapshots after account/grant changes commit.
- `catalog` publishes service lookup with current unit price through its query contract.
- `audit::recording` publishes AuthAudit/AuditWriter. Audit owns its adapters and
  depends on the published shared ID generator, without identity persistence access.
- Document query contracts expose template lookup without leaking table records.

The Excel roster import contract and its runtime staging adapters were removed
on 2026-10-05. Integration retains ownership of import tables and schema records
for historical data; it no longer publishes `integration::imports`. Foreign keys
preserve relational integrity without granting Java modules cross-context access.

Use direct public application contracts for synchronous coordination, in-process
events where decoupling helps, and transactional outbox for durable external work.
Never share MyBatis mappers or repositories between contexts.
