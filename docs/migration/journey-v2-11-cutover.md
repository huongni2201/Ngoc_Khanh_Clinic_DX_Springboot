# Journey v2.11 Cut-over Procedure

V002 removes the legacy `journeys` and `journey_events` tables and intentionally stops when either table still contains rows. Do not bypass that guard or change V002 to drop populated tables unconditionally.

## Before applying V002

1. Schedule a maintenance window and take a verified database backup.
2. Run [`precheck_v2_11_journey.sql`](../../scripts/db/precheck_v2_11_journey.sql) using a read-only account. Record table existence and row counts in the change record; the script does not return patient-level data or modify the database.
3. If either table contains rows, export the legacy records to an approved, access-controlled archive. Protect the export as sensitive healthcare data, restrict access, and record its retention owner and location.
4. Review the export with the clinical and operations owners. Determine whether any operational state still needs reconciliation.
5. Verify that active work can be derived from Encounter, EncounterAssignment, ServiceRequest, PaymentAuthorization, and Result data. Do not recreate Journey status or stage fields as a substitute.
6. Obtain explicit approval for archiving and clearing legacy rows. Preserve the export and approval evidence according to the clinic retention policy.
7. Clear legacy rows only after the review and approval. Use the approved operational procedure; this document and the precheck script do not delete data.

## Apply and verify

1. Re-run the precheck and confirm that both tables are empty (or absent).
2. Apply V002 through the normal Flyway deployment process.
3. Verify Flyway completed successfully and that `dbo.journeys` and `dbo.journey_events` are absent.
4. Run backend verification and the operational smoke checks for Encounter, ServiceRequest, payment authorization, and result-driven worklists.
5. Attach the backup reference, precheck output, review approval, Flyway result, and smoke-check outcome to the deployment record.

If any precondition fails, stop the cut-over and reconcile the data before retrying. Do not edit applied Flyway history to force the migration through.
