package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.CreateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantChangeSupport;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
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
 * Adds one Participant to a batch by hand.
 *
 * <p>The caller must hold the Participant create permission. In one transaction the batch is
 * locked, the organization and batch state are checked, the examination day is resolved inside the
 * batch, an identification number already in the batch (cancelled Participants included) is
 * rejected, the Participant is inserted as active, unconfirmed and pending, and the audit event is
 * written. There is no import job and no idempotency key: the unique identification number stops a
 * double submit, and a retry reports a clear conflict. No Patient, Encounter or ParticipantService
 * is created.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateParticipantUseCase {
  private static final String ACTION = "CREATE_BATCH_PARTICIPANT";

  private final ParticipantAccessPolicy access;
  private final ParticipantChangeSupport support;
  private final HealthExaminationBatchParticipantRepository participants;
  private final Clock clock;

  /**
   * Creates the Participant.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param command roster fields and examination day
   * @param principal authenticated staff account
   * @return the stored Participant in full
   * @throws ApplicationException {@code ACCESS_DENIED} without the create permission
   * @throws IllegalArgumentException when an argument is null or a field breaks a roster rule
   * @throws ResourceNotFoundException when the organization or the batch is not found
   * @throws ConflictException when the batch or organization does not accept changes, the day is
   *     not a day of the batch, or the identification number already belongs to the batch
   */
  @Transactional
  public ParticipantDetailResponse execute(
      UUID organizationId,
      UUID batchId,
      CreateParticipantCommand command,
      UserPrincipal principal) {
    access.requireCreate(principal);
    if (organizationId == null
        || batchId == null
        || command == null
        || command.batchDayId() == null)
      throw new IllegalArgumentException(
          "Organization ID, batch ID, command and examination day are required");

    var batch = support.lockBatchForChange(organizationId, batchId);
    AggregateId dayId = support.requireDay(batch, command.batchDayId());
    var roster =
        ParticipantChangeSupport.roster(
            command.participantCode(),
            command.fullName(),
            command.dateOfBirth(),
            command.sex(),
            command.identificationNumber(),
            command.phone(),
            command.email(),
            command.departmentName(),
            command.positionName());
    support.requireDateOfBirthNotInFuture(command.dateOfBirth());
    AggregateId batchAggregateId = AggregateId.of(batchId);
    support.requireIdentityFree(batchAggregateId, roster.identificationNumber(), null);

    AggregateId participantId = AggregateId.of(UuidV7Generator.generate());
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    var participant =
        HealthExaminationBatchParticipant.create(
            participantId, batchAggregateId, dayId, roster, null, null, now);
    participants.insert(participant);
    support.audit(principal.userId(), ACTION, organizationId, participant, null, 0L, List.of());
    log.info(
        "Participant creation pending commit: organizationId={}, batchId={}, participantId={}",
        organizationId,
        batchId,
        participantId.value());

    var stored =
        participants
            .findInBatch(batchAggregateId, participantId)
            .orElseThrow(() -> new IllegalStateException("Participant was not stored"));
    return ParticipantDetailResponse.from(
        stored, ParticipantChangeSupport.examinationDate(batch, stored.getBatchDayId()));
  }
}
