# Database Terminology Rename Map

Audit date: 2026-09-26

The terminology rename was initially planned as a forward-only migration for
SQL Server. Since the backend had not been deployed, V006 and V007 are now
PostgreSQL 17 migrations in the clean-install sequence. The SQL renames tables,
columns, constraints and indexes without changing their data or business rules.

## Tables

| Current | Target |
| --- | --- |
| `health_check_batches` | `health_examination_batches` |
| `health_check_batch_services` | `health_examination_batch_services` |
| `health_check_batch_employees` | `health_examination_batch_employees` |
| `health_check_batch_employee_services` | `health_examination_batch_employee_services` |
| `health_check_records` | `health_examination_records` |
| `health_check_import_jobs` | `health_examination_import_jobs` |
| `health_check_import_rows` | `health_examination_import_rows` |

## Columns and constraint/index identifiers

Replace the `health_check` prefix with `health_examination` in the affected
foreign keys, snapshots and references:

- `health_check_batch_id`, `health_check_batch_employee_id`,
  `health_check_batch_service_id`, `health_check_import_job_id`,
  `health_check_record_id`;
- `health_check_reason_snapshot`, `health_check_eligible`,
  `is_master_health_check_form`, `replaces_health_check_record_id`;
- `PK_`, `FK_`, `UQ_`, `CK_`, `UX_` and `IX_` identifiers that include one of
  the renamed table/column tokens.

Physician-specific columns are separately tracked and are not changed by the
health-examination table rename:

- `doctor_staff_id` → `physician_staff_id` where the column is specifically a
  physician assignment (`appointments`, `encounter_assignments`);
- `ordered_by_staff_id` remains generic because the baseline models the
  ordering actor as staff and the current Java record has no physician-only
  contract.

## Semantic exclusions

- Existing `lab_*` tables remain unchanged in this pass. The plan requires a
  semantic decision before changing panel/result/report names.
- `identification_number` remains unchanged per ADR-0003.
- `service_requests`, `imaging_studies`, `diagnostic_reports`, `specimens`,
  `analytes`, `prescriptions` and `appointments` are already canonical.
