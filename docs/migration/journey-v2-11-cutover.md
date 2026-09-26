# Journey v2.11 Fresh-Install Migration Note

The backend has not been deployed. V002 is part of the PostgreSQL 17 fresh-install chain and removes the unused `public.journeys` and `public.journey_events` tables. It raises an error if either table contains rows; do not bypass that guard.

## Before applying V002 during initial setup

1. Start PostgreSQL 17 and allow Flyway to apply V001.
2. Optionally run [`precheck_v2_11_journey.sql`](../../scripts/db/precheck_v2_11_journey.sql) to confirm the new tables are empty.
3. If either table has rows, stop and determine why data was inserted before initial deployment. Do not remove rows or disable the V002 guard to force startup.

## Apply and verify

1. Allow Flyway to apply the remaining migrations.
2. Verify Flyway completed successfully and that `public.journeys` and `public.journey_events` are absent.
3. Verify active work derives from Encounter, EncounterAssignment, ServiceRequest, PaymentAuthorization, and Result data rather than a parallel stage model.

After first deployment, applied Flyway migrations are immutable. Future schema changes must append a new migration.
