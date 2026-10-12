package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.RecordExaminationDetailCommand;
import com.ngockhanh.clinic.healthexamination.application.port.out.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailRowResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDateRules;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailEntry;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailReconciler;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the examination details of one Participant by hand: attended with the services
 * performed, or absent.
 *
 * <p>The caller must hold the service record permission. An attended entry is exactly one row of
 * the Excel import: it goes through {@link ExaminationDetailReconciler}, so the Participant becomes
 * {@code ATTENDED} and {@code RECONCILED}, unticked services keep their row with {@code performed =
 * false}, a newly ticked service takes the negotiated price, and a service that is no longer
 * offered cannot be newly ticked. An absent entry is accepted only while no service is recorded as
 * performed and never touches the reconciliation rows. Going back to "not yet arrived" is not
 * supported. The rules that tie the date and the services to the attendance are checked here, so
 * they hold for every caller, not only for the HTTP request.
 *
 * <p>Everything runs in one transaction that locks the batch (so a manual entry and an import
 * never interleave) and then the Participant. The row version must match; the single exception is a
 * retry: when the stored state already is the requested state the stored row is returned and
 * nothing is written, so a client that lost the response of a successful request can send it again
 * without an idempotency key. A request that equals the stored state writes nothing and does not
 * raise the row version. The audit event carries identifiers and counts only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordExaminationDetailUseCase {
  private static final String ACTION = "RECORD_EXAMINATION_DETAIL";

  private final ExaminationDetailAccessPolicy access;
  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;
  private final HealthExaminationBatchParticipantRepository participants;
  private final ExaminationDetailReader reader;
  private final AuditWriter audit;
  private final Clock clock;

  private final ExaminationDetailReconciler reconciler = new ExaminationDetailReconciler();

  /**
   * Records the entry and returns the Participant row as it is stored afterwards.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param participantId Participant identifier
   * @param command attendance, date, services and the row version the client saw
   * @param principal authenticated staff account
   * @return the up-to-date row, with the identification number still complete in the read model and
   *     masked by the response
   * @throws ApplicationException {@code ACCESS_DENIED} without the record permission, or {@code
   *     INVALID_INPUT} when the date or the services do not fit the attendance (attended needs a
   *     date and at least one service, absent needs neither), a service is outside the batch, or
   *     the actual date is outside the batch dates and today
   * @throws IllegalArgumentException when an identifier, the command or its attendance is null
   * @throws ResourceNotFoundException when the organization, the batch or the Participant is not
   *     found
   * @throws ConflictException when the organization or batch does not accept changes, the
   *     Participant is cancelled, a newly ticked service is no longer offered, or an absent entry
   *     is sent for a Participant that already has performed services
   * @throws ConcurrentUpdateException when the Participant changed since the client read it and
   *     the stored state is not already the requested one
   */
  @Transactional
  public ExaminationDetailRowResponse execute(
      UUID organizationId,
      UUID batchId,
      UUID participantId,
      RecordExaminationDetailCommand command,
      UserPrincipal principal) {
    access.requireServiceRecord(principal);
    if (organizationId == null || batchId == null || participantId == null || command == null)
      throw new IllegalArgumentException(
          "Organization, batch and Participant IDs and command are required");
    if (command.attendance() == null) throw new IllegalArgumentException("Attendance is required");
    requireEntryFitsAttendance(command);
    AttendanceStatus status = command.attendance().status();

    var details =
        batches
            .findDetails(organizationId, batchId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Health examination batch"));
    var batch = details.batch();
    var organization =
        organizations
            .findById(AggregateId.of(organizationId))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    if (organization.status() != OrganizationStatus.ACTIVE || !batch.acceptsParticipantChanges())
      throw new ConflictException("Batch does not accept examination detail changes");

    Set<UUID> batchServiceIds = new HashSet<>();
    for (var service : batch.services()) batchServiceIds.add(service.id().value());
    if (!batchServiceIds.containsAll(command.performedBatchServiceIds()))
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT,
          "performedBatchServiceIds contains a service outside this batch");

    HealthExaminationBatchParticipant participant =
        participants
            .findManyInBatchForUpdate(
                AggregateId.of(batchId), List.of(AggregateId.of(participantId)))
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Participant"));
    if (participant.getRosterStatus() != RosterStatus.ACTIVE)
      throw new ConflictException("Participant is cancelled");

    if (participant.getRowVersion() != command.rowVersion()) {
      if (alreadyRecorded(participant, command)) return currentRow(organizationId, batchId, participantId);
      throw new ConcurrentUpdateException(
          "PARTICIPANT_CHANGED", "Participant was changed by another request; reload and try again");
    }

    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    AggregateId actor = AggregateId.of(principal.userId());
    boolean written =
        status == AttendanceStatus.ATTENDED
            ? recordAttended(participant, batch, command, actor, now)
            : recordAbsent(participant, actor, now);
    if (written) {
      // An absent entry changes the Participant row only; the service rows must stay untouched.
      if (status == AttendanceStatus.ATTENDED) participants.save(participant, command.rowVersion());
      else participants.saveWithoutServices(participant, command.rowVersion());
      audit.record(
          principal.userId(),
          ACTION,
          "HEALTH_EXAMINATION_BATCH_PARTICIPANT",
          participantId,
          null,
          Map.of(
              "organizationId", organizationId,
              "batchId", batchId,
              "participantId", participantId,
              "attendanceStatus", status.name(),
              "performedItems", command.performedBatchServiceIds().size()));
    }
    log.info(
        "Examination detail recorded: organizationId={}, batchId={}, participantId={}, result={}",
        organizationId,
        batchId,
        participantId,
        written ? "UPDATED" : "UNCHANGED");
    return currentRow(organizationId, batchId, participantId);
  }

  /**
   * The date and the services must fit the attendance: an attended Participant has an actual date
   * and at least one service performed, an absent one has neither.
   */
  private static void requireEntryFitsAttendance(RecordExaminationDetailCommand command) {
    boolean noServices = command.performedBatchServiceIds().isEmpty();
    if (command.attendance() == RecordExaminationDetailCommand.Attendance.ATTENDED) {
      if (command.actualExaminationDate() == null)
        throw invalid("actualExaminationDate is required when the Participant attended");
      if (noServices)
        throw invalid("performedBatchServiceIds needs at least one service when attended");
    } else {
      if (command.actualExaminationDate() != null)
        throw invalid("actualExaminationDate must be empty when the Participant is absent");
      if (!noServices)
        throw invalid("performedBatchServiceIds must be empty when the Participant is absent");
    }
  }

  private static ApplicationException invalid(String message) {
    return new ApplicationException(ApplicationException.Type.INVALID_INPUT, message);
  }

  private boolean recordAttended(
      HealthExaminationBatchParticipant participant,
      HealthExaminationBatch batch,
      RecordExaminationDetailCommand command,
      AggregateId actor,
      Instant now) {
    ExaminationDateRules.requireActualDateInRange(
        batch, command.actualExaminationDate(), clock, "Participant");
    var entry =
        new ExaminationDetailEntry(
            participant.getId().value(),
            command.rowVersion(),
            command.performedBatchServiceIds(),
            command.actualExaminationDate(),
            "Participant");
    // The planned date is only used when no actual date is given, which a manual entry always has.
    var result = reconciler.apply(participant, batch, entry, actor, now, command.actualExaminationDate());
    return result == ExaminationDetailReconciler.Result.UPDATED;
  }

  private boolean recordAbsent(
      HealthExaminationBatchParticipant participant, AggregateId actor, Instant now) {
    if (!participant.performedBatchServiceIds().isEmpty())
      throw new ConflictException(
          "Participant has performed services; clear them before marking absent");
    if (participant.getAttendanceStatus() == AttendanceStatus.ABSENT) return false;
    participant.recordAttendance(
        AttendanceStatus.ABSENT, null, actor, now, participant.getAttendanceNote());
    return true;
  }

  /** Whether the stored state already is what the command asks for (a retry of a won request). */
  private static boolean alreadyRecorded(
      HealthExaminationBatchParticipant participant, RecordExaminationDetailCommand command) {
    if (command.attendance() == RecordExaminationDetailCommand.Attendance.ABSENT)
      return participant.getAttendanceStatus() == AttendanceStatus.ABSENT
          && participant.performedBatchServiceIds().isEmpty();
    Set<UUID> performed = new HashSet<>();
    for (AggregateId id : participant.performedBatchServiceIds()) performed.add(id.value());
    return participant.getAttendanceStatus() == AttendanceStatus.ATTENDED
        && command.actualExaminationDate().equals(participant.getActualExaminationDate())
        && participant.getReconciliationStatus() == ReconciliationStatus.RECONCILED
        && performed.equals(command.performedBatchServiceIds());
  }

  private ExaminationDetailRowResponse currentRow(
      UUID organizationId, UUID batchId, UUID participantId) {
    return ExaminationDetailRowResponse.from(
        reader
            .readOne(organizationId, batchId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant")));
  }
}
