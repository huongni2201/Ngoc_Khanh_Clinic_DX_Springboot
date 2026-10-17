package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.enums.AgreementStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HealthExaminationParticipantRepository {
  Optional<HealthExaminationParticipant> findById(AggregateId id);

  /**
   * Updates an existing Participant. This is an update, not an upsert: it never creates a row.
   *
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the expected row
   *     version is stale
   */
  void save(HealthExaminationParticipant participant, long expectedRowVersion);

  /**
   * Updates only the Participant's own row: roster and reconciliation state. The service rows are
   * neither read nor written, so their version and {@code updated_at} stay as they were. Use it
   * when a change cannot have touched the services.
   *
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the expected row
   *     version is stale
   */
  void saveWithoutServices(HealthExaminationParticipant participant, long expectedRowVersion);

  /** Loads a Participant only when it belongs to the given agreement. */
  Optional<HealthExaminationParticipant> findInAgreement(AggregateId agreementId, AggregateId id);

  /**
   * Loads the given Participants of one agreement with their reconciliation rows, locking every
   * Participant row until the transaction ends. The rows are locked in identifier order, so two
   * concurrent callers cannot deadlock on the same set. Identifiers that do not belong to the
   * agreement are simply absent from the result; the caller decides what that means.
   *
   * <p>One query reads the headers and one reads all the reconciliation rows, whatever the number
   * of Participants.
   */
  List<HealthExaminationParticipant> findManyInAgreementForUpdate(
      AggregateId agreementId, Collection<AggregateId> ids);

  /**
   * Loads the given Participants of one agreement without locking them, for read-only work such as
   * printing. Identifiers that do not belong to the agreement are simply absent from the result.
   */
  List<HealthExaminationParticipant> findManyInAgreement(
      AggregateId agreementId, Collection<AggregateId> ids);

  /**
   * Inserts one Participant. A duplicate identification number in the agreement, whatever the
   * roster status of the existing row, is rejected by the database unique constraint.
   *
   * @throws IllegalStateException when the row was not written
   */
  void insert(HealthExaminationParticipant participant);

  /**
   * Whether another Participant of the agreement already holds the identification number, whatever
   * its roster status.
   *
   * @param excludeId Participant to ignore (the one being edited), or {@code null} on create
   */
  boolean identityTakenByOther(
      AggregateId agreementId, IdentificationNumber identity, AggregateId excludeId);

  /**
   * Inserts new Participants of one agreement. A duplicate identification number in the agreement,
   * whatever the roster status of the existing row, is not ignored: the database unique constraint
   * rejects the write.
   *
   * @throws IllegalStateException when fewer rows were written than given
   */
  void insertMany(List<HealthExaminationParticipant> participants);

  /**
   * Number of Participants of the agreement, whatever their roster status. Participants are never
   * deleted, so with the agreement locked the count only grows and is the base of the next
   * generated participant code.
   */
  long countInAgreement(AggregateId agreementId);

  /**
   * Returns the given identification numbers that already belong to a Participant of the agreement,
   * whatever its roster status.
   */
  List<IdentificationNumber> findExistingIdentities(
      AggregateId agreementId, List<IdentificationNumber> identities);

  /**
   * Reads the scheduling facts of the given Participants of one organization in one query: the
   * agreement each belongs to, its roster status and the status of that agreement. Identifiers that
   * are not Participants of the organization are absent from the result. Nothing is locked.
   */
  List<ParticipantScope> findScopes(AggregateId organizationId, Collection<AggregateId> ids);

  /**
   * What scheduling needs to know about a Participant.
   *
   * @param agreementStatus status of the Participant's agreement
   */
  record ParticipantScope(
      AggregateId id,
      AggregateId agreementId,
      RosterStatus rosterStatus,
      AgreementStatus agreementStatus) {}
}
