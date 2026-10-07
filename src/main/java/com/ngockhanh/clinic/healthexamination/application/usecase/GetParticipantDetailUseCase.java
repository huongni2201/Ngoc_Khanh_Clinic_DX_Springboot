package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ParticipantChangeSupport;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads one Participant in full, including the complete identification number, phone and email, for
 * the edit form. The caller must hold PARTICIPANT_VIEW. A cancelled Participant is returned too,
 * with its status. It takes no lock and does not depend on the batch status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetParticipantDetailUseCase {
  private final ParticipantAccessPolicy access;
  private final ParticipantChangeSupport support;
  private final HealthExaminationBatchParticipantRepository participants;

  /**
   * Returns the Participant.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param participantId Participant identifier inside the batch
   * @param principal authenticated staff caller
   * @return the Participant detail, including its identification number and contact channels
   * @throws ApplicationException {@code ACCESS_DENIED} without the read permission
   * @throws IllegalArgumentException when an identifier is null
   * @throws ResourceNotFoundException when the batch does not exist in the organization, or the
   *     Participant is not in the batch
   */
  @Transactional(readOnly = true)
  public ParticipantDetailResponse execute(
      UUID organizationId, UUID batchId, UUID participantId, UserPrincipal principal) {
    access.requireRead(principal);
    if (organizationId == null || batchId == null || participantId == null)
      throw new IllegalArgumentException("Organization, batch and Participant IDs are required");
    var batch = support.batchForRead(organizationId, batchId);
    var participant =
        participants
            .findInBatch(AggregateId.of(batchId), AggregateId.of(participantId))
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    log.debug(
        "Participant detail read: organizationId={}, batchId={}, participantId={}",
        organizationId,
        batchId,
        participantId);
    return ParticipantDetailResponse.from(
        participant, ParticipantChangeSupport.examinationDate(batch, participant.getBatchDayId()));
  }
}
