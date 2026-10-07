package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.ReactivateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantChangeSupport;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns a cancelled Participant to the active roster: the same row becomes {@code ACTIVE} again.
 * No row is inserted, so the identification number stays unique in the batch and the import
 * provenance and audit history stay linked to the Participant.
 *
 * <p>The caller must hold the Participant manage permission. The batch is locked first, then the
 * Participant, the same order as every other roster change. Only the roster status changes, plus
 * the examination day when the caller picks a different one; roster fields, attendance and
 * reconciliation are kept as they were at cancellation. The audit event lists the names of the
 * changed fields, never their values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactivateParticipantUseCase {
  private static final String ACTION = "REACTIVATE_BATCH_PARTICIPANT";

  private final ParticipantAccessPolicy access;
  private final ParticipantChangeSupport support;
  private final HealthExaminationBatchParticipantRepository participants;

  /**
   * Reactivates the Participant.
   *
   * @return the stored Participant in full, with its new row version
   * @throws ApplicationException {@code ACCESS_DENIED} without the manage permission
   * @throws IllegalArgumentException when an argument is null or the version is negative
   * @throws ResourceNotFoundException when the organization, batch or Participant is not found
   * @throws ConflictException when the batch or organization does not accept changes, the day is
   *     not a day of the batch, or the Participant is not cancelled or was prepared, attended or
   *     reconciled
   * @throws ConcurrentUpdateException when the expected row version is stale
   */
  @Transactional
  public ParticipantDetailResponse execute(
      UUID organizationId,
      UUID batchId,
      UUID participantId,
      ReactivateParticipantCommand command,
      UserPrincipal principal) {
    access.requireManage(principal);
    if (organizationId == null || batchId == null || participantId == null || command == null)
      throw new IllegalArgumentException(
          "Organization, batch and Participant IDs and command are required");
    Long expectedVersion = command.expectedRowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");

    var batch = support.lockBatchForChange(organizationId, batchId);
    AggregateId batchAggregateId = AggregateId.of(batchId);
    var participant =
        participants
            .findInBatch(batchAggregateId, AggregateId.of(participantId))
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    if (participant.getRowVersion() != expectedVersion) throw new ConcurrentUpdateException();

    AggregateId dayBefore = participant.getBatchDayId();
    AggregateId dayAfter =
        command.batchDayId() == null ? dayBefore : support.requireDay(batch, command.batchDayId());

    ParticipantChangeSupport.applyingDomainRule(participant::reactivate);
    boolean dayChanged = !dayBefore.equals(dayAfter);
    if (dayChanged)
      ParticipantChangeSupport.applyingDomainRule(() -> participant.moveToDay(dayAfter));
    participants.save(participant, expectedVersion);

    List<String> changedFields = new ArrayList<>(List.of("rosterStatus"));
    if (dayChanged) changedFields.add("batchDayId");
    support.audit(
        principal.userId(),
        ACTION,
        organizationId,
        participant,
        expectedVersion,
        expectedVersion + 1,
        List.copyOf(changedFields));
    log.info(
        "Participant reactivation pending commit: organizationId={}, batchId={}, participantId={}",
        organizationId,
        batchId,
        participantId);

    var stored =
        participants
            .findInBatch(batchAggregateId, participant.getId())
            .orElseThrow(() -> new IllegalStateException("Participant was not stored"));
    return ParticipantDetailResponse.from(
        stored, ParticipantChangeSupport.examinationDate(batch, stored.getBatchDayId()));
  }
}
