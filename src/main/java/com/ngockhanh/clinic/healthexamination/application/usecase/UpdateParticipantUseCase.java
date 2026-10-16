package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantChangeSupport;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edits one Participant's roster fields.
 *
 * <p>The caller must hold the Participant update permission. In one transaction the agreement is
 * locked and checked, the Participant is loaded inside that agreement and its expected version is
 * compared before anything is changed (the SQL version predicate remains the final guard). The
 * identification number is only checked for a duplicate when it changes, ignoring the Participant
 * itself, and is locked once the Participant is linked to a Patient. Reconciliation, the schedule
 * and the Patient link are untouched. The audit event lists the names of the changed fields, never
 * their values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateParticipantUseCase {
  private static final String ACTION = "UPDATE_PARTICIPANT";

  private final ParticipantAccessPolicy access;
  private final ParticipantChangeSupport support;
  private final HealthExaminationParticipantRepository participants;

  /**
   * Updates the Participant.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param participantId Participant identifier inside the agreement
   * @param command replacement roster fields and expected version
   * @param principal authenticated staff caller
   * @return the stored Participant in full, with its new row version
   * @throws ApplicationException {@code ACCESS_DENIED} without the update permission
   * @throws IllegalArgumentException when an argument is null, the version is negative or a field
   *     breaks a roster rule
   * @throws ResourceNotFoundException when the organization, agreement or Participant is not found
   * @throws ConflictException when the agreement or organization does not accept changes, the
   *     identification number is taken or locked, or the Participant is cancelled
   * @throws ConcurrentUpdateException when the expected row version is stale
   */
  @Transactional
  public ParticipantDetailResponse execute(
      UUID organizationId,
      UUID agreementId,
      UUID participantId,
      UpdateParticipantCommand command,
      UserPrincipal principal) {
    access.requireUpdate(principal);
    if (organizationId == null || agreementId == null || participantId == null || command == null)
      throw new IllegalArgumentException(
          "Organization, agreement and Participant IDs and command are required");
    Long expectedVersion = command.expectedRowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");

    support.lockAgreementForChange(organizationId, agreementId);
    AggregateId agreementAggregateId = AggregateId.of(agreementId);
    var participant =
        participants
            .findInAgreement(agreementAggregateId, AggregateId.of(participantId))
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    if (participant.getRowVersion() != expectedVersion) throw new ConcurrentUpdateException();

    var before = participant.getRoster();
    var next =
        ParticipantChangeSupport.roster(
            before.participantCode(),
            command.fullName(),
            command.dateOfBirth(),
            command.sex(),
            command.identificationNumber(),
            command.identificationIssueDate(),
            command.identificationIssuePlace(),
            command.ethnicity(),
            command.phone(),
            command.email(),
            command.address(),
            command.workplace(),
            command.departmentName(),
            command.positionName(),
            command.note());
    support.requireDateOfBirthNotInFuture(command.dateOfBirth());
    if (!before.identificationNumber().equals(next.identificationNumber()))
      support.requireIdentityFree(
          agreementAggregateId, next.identificationNumber(), participant.getId());

    ParticipantChangeSupport.applyingDomainRule(() -> participant.updateRoster(next));
    participants.save(participant, expectedVersion);
    support.audit(
        principal.userId(),
        ACTION,
        organizationId,
        participant,
        expectedVersion,
        expectedVersion + 1,
        ParticipantChangeSupport.changedFields(before, next));
    log.info(
        "Participant update pending commit: organizationId={}, agreementId={}, participantId={}",
        organizationId,
        agreementId,
        participantId);

    var stored =
        participants
            .findInAgreement(agreementAggregateId, participant.getId())
            .orElseThrow(() -> new IllegalStateException("Participant was not stored"));
    return support.detail(organizationId, stored);
  }
}
