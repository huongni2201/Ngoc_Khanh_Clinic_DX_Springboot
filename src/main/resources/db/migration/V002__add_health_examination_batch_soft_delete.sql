-- ============================================================================
-- V002: soft delete for health examination batches
--
-- Owner decision (2026-10-06): deleting a batch is a soft delete. Only a DRAFT
-- batch without Participants or integration history may be deleted; the batch
-- row, its days and services, and its unique batch_code are kept.
--
--   * V001 is not changed; existing batches get deleted_at = NULL (not deleted).
--   * batch_code stays unique across deleted rows (no partial unique index).
--   * The actor of a deletion is recorded in audit_events, not in this table.
--   * No new index: public.ix_health_exam_batches_organization is still used.
--
-- Rollback note: do not DROP deleted_at to roll back the application. Code that
-- does not know deleted_at would show deleted batches again.
-- ============================================================================

ALTER TABLE public.health_examination_batches
    ADD COLUMN deleted_at timestamptz(3) NULL;
