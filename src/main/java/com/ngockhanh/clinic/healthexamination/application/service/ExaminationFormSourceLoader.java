package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.healthexamination.application.contract.ExaminationFormSource;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads, in one short read-only transaction, the facts needed to print one Participant's form. It is
 * separate from the use case so the database connection is released before the slow PDF conversion.
 */
@Component
@RequiredArgsConstructor
public class ExaminationFormSourceLoader {
  private final ParticipantChangeSupport support;
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchParticipantRepository participants;
  private final Clock clock;

  /** Reason a Participant is left out of a bulk print because it is not in the batch. */
  public static final String NOT_FOUND = "NOT_FOUND";

  /** Reason a Participant is left out of a bulk print because it was cancelled. */
  public static final String CANCELLED = "CANCELLED";

  /** The forms of many Participants. */
  public record Forms(List<Form> forms, List<Left> left) {}

  /** One printable Participant. */
  public record Form(UUID participantId, ExaminationFormSource source) {}

  /** One Participant left out, with the reason. */
  public record Left(UUID participantId, String reason) {}

  /**
   * Loads the form facts of an active Participant of a ready or finalized batch.
   *
   * @throws ResourceNotFoundException when the batch, the organization or the Participant is not
   *     found
   * @throws ConflictException when the batch is a draft or closed, or the Participant is cancelled
   */
  @Transactional(readOnly = true)
  public ExaminationFormSource load(UUID organizationId, UUID batchId, UUID participantId) {
    var batch = printableBatch(organizationId, batchId);
    var participant =
        participants
            .findInBatch(AggregateId.of(batchId), AggregateId.of(participantId))
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    if (participant.getRosterStatus() != RosterStatus.ACTIVE)
      throw new ConflictException("A cancelled Participant has no examination form");
    return source(batch, organization(organizationId), participant);
  }

  /**
   * Checks that forms may be printed for the batch now, without reading any Participant.
   *
   * @return the business code of the batch
   * @throws ResourceNotFoundException when the batch or the organization is not found
   * @throws ConflictException when the batch is a draft or closed
   */
  @Transactional(readOnly = true)
  public String requirePrintableBatch(UUID organizationId, UUID batchId) {
    var batch = printableBatch(organizationId, batchId);
    organization(organizationId);
    return batch.code();
  }

  /**
   * Loads the form facts of many Participants of one batch in a short read-only transaction,
   * without locking any row. A Participant that is not in the batch or was cancelled is reported in
   * {@link Forms#left()} instead of failing the others. The forms come in the order of the given
   * identifiers.
   *
   * @throws ResourceNotFoundException when the batch or the organization is not found
   * @throws ConflictException when the batch is a draft or closed
   */
  @Transactional(readOnly = true)
  public Forms loadMany(UUID organizationId, UUID batchId, List<UUID> participantIds) {
    var batch = printableBatch(organizationId, batchId);
    var organization = organization(organizationId);
    Map<UUID, HealthExaminationBatchParticipant> found = new HashMap<>();
    for (var participant :
        participants.findManyInBatch(
            AggregateId.of(batchId), participantIds.stream().map(AggregateId::of).toList()))
      found.put(participant.getId().value(), participant);
    List<Form> forms = new ArrayList<>();
    List<Left> left = new ArrayList<>();
    for (UUID id : participantIds) {
      var participant = found.get(id);
      if (participant == null) left.add(new Left(id, NOT_FOUND));
      else if (participant.getRosterStatus() != RosterStatus.ACTIVE) left.add(new Left(id, CANCELLED));
      else forms.add(new Form(id, source(batch, organization, participant)));
    }
    return new Forms(forms, left);
  }

  private HealthExaminationBatch printableBatch(UUID organizationId, UUID batchId) {
    var batch = support.batchForRead(organizationId, batchId);
    if (batch.status() != BatchStatus.READY && batch.status() != BatchStatus.FINALIZED)
      throw new ConflictException("The batch status does not allow printing examination forms");
    return batch;
  }

  private Organization organization(UUID organizationId) {
    return organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));
  }

  private ExaminationFormSource source(
      HealthExaminationBatch batch,
      Organization organization,
      HealthExaminationBatchParticipant participant) {
    var roster = participant.getRoster();
    return new ExaminationFormSource(
        roster.participantCode(),
        roster.fullName(),
        roster.dateOfBirth(),
        roster.sex(),
        roster.identificationNumber() == null ? null : roster.identificationNumber().value(),
        roster.identificationIssueDate(),
        roster.identificationIssuePlace(),
        roster.ethnicity(),
        roster.phone(),
        roster.address(),
        roster.workplace(),
        roster.departmentName(),
        roster.positionName(),
        organization.name(),
        organization.address(),
        batch.code(),
        batch.name(),
        ParticipantChangeSupport.examinationDate(batch, participant.getBatchDayId()),
        LocalDate.now(clock.withZone(ParticipantChangeSupport.BUSINESS_ZONE)));
  }
}
