package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.CancelParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantChangeSupport;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels one Participant: the roster status becomes {@code CANCELLED} and nothing is deleted. The
 * row, its import provenance, its history and its audit trail stay, and the identification number
 * keeps its place in the agreement, so the same number cannot be added again.
 *
 * <p>The caller must hold the Participant remove permission. A Participant that was prepared for a
 * visit, has attended a session or has its services reconciled cannot be cancelled, and neither can
 * one that is already cancelled. The sessions the Participant is still expected at are cancelled in
 * the same transaction, so they stop counting the person; their history stays.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelParticipantUseCase {
  private static final String ACTION = "CANCEL_PARTICIPANT";

  private final ParticipantAccessPolicy access;
  private final ParticipantChangeSupport support;
  private final HealthExaminationParticipantRepository participants;
  private final SessionAssignmentRepository assignments;
  private final Clock clock;

  /**
   * Cancels the Participant.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param participantId Participant identifier inside the agreement
   * @param command expected Participant version
   * @param principal authenticated staff caller
   * @throws ApplicationException {@code ACCESS_DENIED} without the remove permission
   * @throws IllegalArgumentException when an argument is null or the version is negative
   * @throws ResourceNotFoundException when the organization, agreement or Participant is not found
   * @throws ConflictException when the agreement or organization does not accept changes, or the
   *     Participant is cancelled, prepared, attended or reconciled
   * @throws ConcurrentUpdateException when the expected row version is stale
   */
  @Transactional
  public void execute(
      UUID organizationId,
      UUID agreementId,
      UUID participantId,
      CancelParticipantCommand command,
      UserPrincipal principal) {
    access.requireRemove(principal);
    if (organizationId == null || agreementId == null || participantId == null || command == null)
      throw new IllegalArgumentException(
          "Organization, agreement and Participant IDs and command are required");
    Long expectedVersion = command.expectedRowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");

    support.lockAgreementForChange(organizationId, agreementId);
    var participant =
        participants
            .findInAgreement(AggregateId.of(agreementId), AggregateId.of(participantId))
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    if (participant.getRowVersion() != expectedVersion) throw new ConcurrentUpdateException();

    if (assignments.hasAttended(participant.getId()))
      throw new ConflictException("Participant cannot be cancelled after attendance");
    ParticipantChangeSupport.applyingDomainRule(participant::cancel);
    participants.save(participant, expectedVersion);
    int released =
        assignments.cancelExpectedOfParticipant(
            participant.getId(),
            AggregateId.of(principal.userId()),
            Instant.now(clock).truncatedTo(ChronoUnit.MILLIS));
    support.audit(
        principal.userId(),
        ACTION,
        organizationId,
        participant,
        expectedVersion,
        expectedVersion + 1,
        List.of("rosterStatus"));
    log.info(
        "Participant cancellation pending commit: organizationId={}, agreementId={}, participantId={},"
            + " releasedAssignments={}",
        organizationId,
        agreementId,
        participantId,
        released);
  }
}
