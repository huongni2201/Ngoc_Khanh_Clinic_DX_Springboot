package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.CreateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantChangeSupport;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
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
 * Adds one Participant to an agreement by hand.
 *
 * <p>The caller must hold the Participant create permission. In one transaction the agreement is
 * locked, the organization and agreement state are checked, an identification number already in the
 * agreement (cancelled Participants included) is rejected, the Participant is inserted as active
 * and pending, without a schedule, and the audit event is written. The participant code is
 * generated from the agreement code and never typed. There is no import job and no idempotency key:
 * the unique identification number stops a double submit, and a retry reports a clear conflict. No
 * Patient, Encounter or ParticipantService is created.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateParticipantUseCase {
  private static final String ACTION = "CREATE_PARTICIPANT";

  private final ParticipantAccessPolicy access;
  private final ParticipantChangeSupport support;
  private final HealthExaminationParticipantRepository participants;
  private final Clock clock;

  /**
   * Creates the Participant.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param command roster fields
   * @param principal authenticated staff account
   * @return the stored Participant in full
   * @throws ApplicationException {@code ACCESS_DENIED} without the create permission
   * @throws IllegalArgumentException when an argument is null or a field breaks a roster rule
   * @throws ResourceNotFoundException when the organization or the agreement is not found
   * @throws ConflictException when the agreement or organization does not accept changes, or the
   *     identification number already belongs to the agreement
   */
  @Transactional
  public ParticipantDetailResponse execute(
      UUID organizationId,
      UUID agreementId,
      CreateParticipantCommand command,
      UserPrincipal principal) {
    access.requireCreate(principal);
    if (organizationId == null || agreementId == null || command == null)
      throw new IllegalArgumentException("Organization ID, agreement ID and command are required");

    var agreement = support.lockAgreementForChange(organizationId, agreementId);
    var roster =
        ParticipantChangeSupport.roster(
            support.nextParticipantCodes(agreement, 1).get(0),
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
    AggregateId agreementAggregateId = AggregateId.of(agreementId);
    support.requireIdentityFree(agreementAggregateId, roster.identificationNumber(), null);

    AggregateId participantId = AggregateId.of(UuidV7Generator.generate());
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    var participant =
        HealthExaminationParticipant.create(
            participantId,
            AggregateId.of(organizationId),
            agreementAggregateId,
            roster,
            null,
            null,
            now);
    participants.insert(participant);
    support.audit(principal.userId(), ACTION, organizationId, participant, null, 0L, List.of());
    log.info(
        "Participant creation pending commit: organizationId={}, agreementId={}, participantId={}",
        organizationId,
        agreementId,
        participantId.value());

    var stored =
        participants
            .findInAgreement(agreementAggregateId, participantId)
            .orElseThrow(() -> new IllegalStateException("Participant was not stored"));
    return support.detail(organizationId, stored);
  }
}
