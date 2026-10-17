package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.ReactivateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantChangeSupport;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
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
 * Returns a cancelled Participant to the active roster: the same row becomes {@code ACTIVE} again.
 * No row is inserted, so the identification number stays unique in the agreement and the import
 * provenance and audit history stay linked to the Participant.
 *
 * <p>The caller must hold the Participant reactivate permission. The agreement is locked first,
 * then the Participant, the same order as every other roster change. Only the roster status
 * changes; roster fields and reconciliation are kept as they were at cancellation, and the
 * Participant is scheduled again explicitly. The audit event lists the names of the changed fields,
 * never their values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactivateParticipantUseCase {
  private static final String ACTION = "REACTIVATE_PARTICIPANT";

  private final ParticipantAccessPolicy access;
  private final ParticipantChangeSupport support;
  private final HealthExaminationParticipantRepository participants;

  /**
   * Reactivates the Participant.
   *
   * @param organizationId owning organization
   * @param agreementId agreement identifier
   * @param participantId Participant identifier inside the agreement
   * @param command expected Participant version
   * @param principal authenticated staff caller
   * @return the stored Participant in full, with its new row version
   * @throws ApplicationException {@code ACCESS_DENIED} without the reactivate permission
   * @throws IllegalArgumentException when an argument is null or the version is negative
   * @throws ResourceNotFoundException when the organization, agreement or Participant is not found
   * @throws ConflictException when the agreement or organization does not accept changes, or the
   *     Participant is not cancelled or was prepared, attended or reconciled
   * @throws ConcurrentUpdateException when the expected row version is stale
   */
  @Transactional
  public ParticipantDetailResponse execute(
      UUID organizationId,
      UUID agreementId,
      UUID participantId,
      ReactivateParticipantCommand command,
      UserPrincipal principal) {
    access.requireReactivate(principal);
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

    ParticipantChangeSupport.applyingDomainRule(participant::reactivate);
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
        "Participant reactivation pending commit: organizationId={}, agreementId={}, participantId={}",
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
