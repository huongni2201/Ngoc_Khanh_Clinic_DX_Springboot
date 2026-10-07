package com.ngockhanh.clinic.integration.application.imports;

import java.util.List;
import java.util.UUID;

/**
 * Published contract of the integration module for the history and idempotency of Participant
 * imports.
 *
 * <p>Every method joins the caller's transaction and never starts its own, so the reservation, the
 * job and the rows commit or roll back together with the Participants. The caller owns the
 * authorization and the business validation; this contract only stores durable import state.
 */
public interface ParticipantImportStore {
  /**
   * Reserves the request key of one import, or returns the stored receipt when the same request
   * already completed.
   *
   * <p>The key row is locked until the transaction ends, so a concurrent request with the same key
   * waits for it.
   *
   * @return {@link ImportReservation.New} for a first request, {@link ImportReservation.Replay}
   *     when the key and the request hash match a completed request
   * @throws com.ngockhanh.clinic.shared.exception.ConflictException when the key was used for a
   *     request with another hash, or its request is still being processed
   */
  ImportReservation reserve(ImportRequestIdentity request);

  /**
   * Stores a validated job and its staged rows, and returns the job identifier. The job is
   * {@code VALIDATED}, so Participants may reference it.
   */
  UUID createValidatedJob(ValidatedParticipantImport input);

  /** Links each staged row of the job to the resource created from it. */
  void markRowsCommitted(UUID jobId, List<CommittedImportRow> rows);

  /**
   * Marks the job confirmed with the safe result summary.
   *
   * @param expectedVersion row version the job had when it was created
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the job changed
   */
  void confirmJob(UUID jobId, long expectedVersion, ImportReceipt receipt);

  /** Completes the reservation so a retry with the same key replays the receipt. */
  void completeRequest(UUID reservationId, ImportReceipt receipt);
}
