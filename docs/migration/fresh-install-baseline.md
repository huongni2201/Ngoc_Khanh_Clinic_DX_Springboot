# Fresh-Install Migration Baseline

The backend has not been deployed. Before the first PostgreSQL 18 deployment, the initial migration chain was consolidated into `V001__create_final_schema.sql`.

Fresh databases apply this one migration to create the final schema, including canonical table and column names, result-release fields, and lifecycle checks. The earlier cut-over steps and prechecks are no longer part of the install path.

After the first deployment, treat every applied migration as immutable and append a new version for each future schema change. This baseline is for clean initialization, not an in-place SQL Server data transfer.
