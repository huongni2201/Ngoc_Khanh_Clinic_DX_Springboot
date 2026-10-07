package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.CancelParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantChangeSupport;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels one Participant: the roster status becomes {@code CANCELLED} and nothing is deleted. The
 * row, its import provenance, its history and its audit trail stay, and the identification number
 * keeps its place in the batch, so the same number cannot be added again.
 *
 * <p>The caller must hold the Participant manage permission. A Participant that was prepared for a
 * visit, has attended or has its services reconciled cannot be cancelled, and neither can one that
 * is already cancelled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelParticipantUseCase {
  private static final String ACTION = "CANCEL_BATCH_PARTICIPANT";

  private final ParticipantAccessPolicy access;
  private final ParticipantChangeSupport support;
  private final HealthExaminationBatchParticipantRepository participants;

  /**
   * Cancels the Participant.
   *
   * @throws ApplicationException {@code ACCESS_DENIED} without the manage permission
   * @throws IllegalArgumentException when an argument is null or the version is negative
   * @throws ResourceNotFoundException when the organization, batch or Participant is not found
   * @throws ConflictException when the batch or organization does not accept changes, or the
   *     Participant is cancelled, prepared, attended or reconciled
   * @throws ConcurrentUpdateException when the expected row version is stale
   */
  @Transactional
  public void execute(
      UUID organizationId,
      UUID batchId,
      UUID participantId,
      CancelParticipantCommand command,
      UserPrincipal principal) {
    access.requireManage(principal);
    if (organizationId == null || batchId == null || participantId == null || command == null)
      throw new IllegalArgumentException(
          "Organization, batch and Participant IDs and command are required");
    Long expectedVersion = command.expectedRowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");

    support.lockBatchForChange(organizationId, batchId);
    var participant =
        participants
            .findInBatch(AggregateId.of(batchId), AggregateId.of(participantId))
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    if (participant.getRowVersion() != expectedVersion) throw new ConcurrentUpdateException();

    ParticipantChangeSupport.applyingDomainRule(participant::cancel);
    participants.save(participant, expectedVersion);
    support.audit(
        principal.userId(),
        ACTION,
        organizationId,
        participant,
        expectedVersion,
        expectedVersion + 1,
        List.of("rosterStatus"));
    log.info(
        "Participant cancellation pending commit: organizationId={}, batchId={}, participantId={}",
        organizationId,
        batchId,
        participantId);
  }
}
