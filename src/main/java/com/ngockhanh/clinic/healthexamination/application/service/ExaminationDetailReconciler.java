package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailImportRow;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies one row of an examination detail file to a Participant: the file is the new
 * reconciliation state of that Participant.
 *
 * <p>An {@code X} marks a service as performed and a blank marks it as not performed; a row that
 * once existed is kept with {@code performed = false}, never deleted. A row with at least one
 * {@code X} also makes the Participant {@code ATTENDED} on the actual date of the file, else on the
 * date already recorded, else on the planned date. A row without any {@code X} never changes
 * attendance: a Participant that has no service rows yet is left alone, and one that has them gets
 * every service set to not performed. A row that already matches the stored state writes nothing.
 * The domain keeps the invariants; this class only decides what to ask it to do.
 */
public final class ExaminationDetailReconciler {
  /** What the row did to the Participant. */
  public enum Result {
    /** Nothing was written: the row matches the stored state, or there was nothing to record. */
    UNCHANGED,
    /** Attendance and/or the service rows were changed on the aggregate; it must be saved. */
    UPDATED
  }

  /**
   * Applies the row.
   *
   * @param participant active Participant locked by the caller, already matched to the row
   * @param batch the batch that owns the Participant, with its service scope
   * @param row the file row for the Participant
   * @param actor account performing the import
   * @param now one instant for the whole import
   * @param plannedDate planned examination date of the Participant
   * @throws ConflictException when the row newly marks a service that is no longer offered
   */
  public Result apply(
      HealthExaminationBatchParticipant participant,
      HealthExaminationBatch batch,
      ExaminationDetailImportRow row,
      AggregateId actor,
      Instant now,
      LocalDate plannedDate) {
    Set<AggregateId> ticked = new LinkedHashSet<>();
    for (var serviceId : row.performedServiceIds()) ticked.add(AggregateId.of(serviceId));
    Set<AggregateId> performedNow = participant.performedBatchServiceIds();

    if (ticked.isEmpty() && participant.reconciledBatchServiceIds().isEmpty())
      return Result.UNCHANGED;

    LocalDate attendedOn = null;
    boolean attendanceChanges = false;
    if (!ticked.isEmpty()) {
      boolean attended = participant.getAttendanceStatus() == AttendanceStatus.ATTENDED;
      attendedOn =
          row.actualExaminationDate() != null
              ? row.actualExaminationDate()
              : attended ? participant.getActualExaminationDate() : plannedDate;
      attendanceChanges = !attended || !attendedOn.equals(participant.getActualExaminationDate());
    }
    boolean servicesChange =
        !ticked.equals(performedNow)
            || participant.getReconciliationStatus() != ReconciliationStatus.RECONCILED;
    if (!attendanceChanges && !servicesChange) return Result.UNCHANGED;

    for (AggregateId serviceId : ticked) {
      if (performedNow.contains(serviceId)) continue;
      var batchService = batch.service(serviceId);
      if (batchService == null || !batchService.active())
        throw new ConflictException(
            "Row " + row.rowNumber() + ": a marked service is no longer offered in this batch");
    }

    if (attendanceChanges)
      participant.recordAttendance(
          AttendanceStatus.ATTENDED, attendedOn, actor, now, participant.getAttendanceNote());

    List<HealthExaminationBatchParticipantService> reconciled = new ArrayList<>();
    for (var existing : participant.services())
      reconciled.add(
          existing.recordPerformed(ticked.contains(existing.batchServiceId()), actor, now));
    for (AggregateId serviceId : ticked)
      if (!participant.reconciledBatchServiceIds().contains(serviceId))
        reconciled.add(
            HealthExaminationBatchParticipantService.newPerformed(
                AggregateId.of(UuidV7Generator.generate()),
                participant.getBatchId(),
                participant.getId(),
                batch.service(serviceId),
                actor,
                now));
    participant.reconcileServices(reconciled, batch.services(), actor, now);
    return Result.UPDATED;
  }
}
