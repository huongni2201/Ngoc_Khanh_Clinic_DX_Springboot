package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HealthExaminationBatchParticipantRepository {
  Optional<HealthExaminationBatchParticipant> findById(AggregateId id);

  /**
   * Updates an existing Participant. This is an update, not an upsert: it never creates a row.
   *
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the expected row
   *     version is stale
   */
  void save(HealthExaminationBatchParticipant participant, long expectedRowVersion);

  /** Loads a Participant only when it belongs to the given batch. */
  Optional<HealthExaminationBatchParticipant> findInBatch(AggregateId batchId, AggregateId id);

  /**
   * Loads the given Participants of one batch with their reconciliation rows, locking every
   * Participant row until the transaction ends. The rows are locked in identifier order, so two
   * concurrent callers cannot deadlock on the same set. Identifiers that do not belong to the batch
   * are simply absent from the result; the caller decides what that means.
   *
   * <p>One query reads the headers and one reads all the reconciliation rows, whatever the number
   * of Participants.
   */
  List<HealthExaminationBatchParticipant> findManyInBatchForUpdate(
      AggregateId batchId, Collection<AggregateId> ids);

  /**
   * Inserts one Participant. A duplicate identification number in the batch, whatever the roster
   * status of the existing row, is rejected by the database unique constraint.
   *
   * @throws IllegalStateException when the row was not written
   */
  void insert(HealthExaminationBatchParticipant participant);

  /**
   * Whether another Participant of the batch already holds the identification number, whatever its
   * roster status.
   *
   * @param excludeId Participant to ignore (the one being edited), or {@code null} on create
   */
  boolean identityTakenByOther(
      AggregateId batchId, IdentificationNumber identity, AggregateId excludeId);

  /**
   * Inserts new Participants of one batch. A duplicate identification number in the batch, whatever
   * the roster status of the existing row, is not ignored: the database unique constraint rejects
   * the write.
   *
   * @throws IllegalStateException when fewer rows were written than given
   */
  void insertMany(List<HealthExaminationBatchParticipant> participants);

  /**
   * Returns the given identification numbers that already belong to a Participant of the batch,
   * whatever its roster status.
   */
  List<IdentificationNumber> findExistingIdentities(
      AggregateId batchId, List<IdentificationNumber> identities);
}
