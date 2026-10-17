package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationAgreement;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies one entry (a row of an examination detail file, or a manual entry for one Participant) to
 * a Participant: the entry is the new service reconciliation of that Participant.
 *
 * <p>An {@code X} marks a service as performed and a blank marks it as not performed; a row that
 * once existed is kept with {@code performed = false}, never deleted. Attendance is not touched: it
 * belongs to the session assignments. A row without any {@code X} leaves a Participant that has no
 * service rows yet alone, and sets every service of one that has them to not performed. A row that
 * already matches the stored state writes nothing. The domain keeps the invariants; this class only
 * decides what to ask it to do.
 */
public final class ExaminationDetailReconciler {
  /** What the row did to the Participant. */
  public enum Result {
    /** Nothing was written: the row matches the stored state, or there was nothing to record. */
    UNCHANGED,
    /** The service rows were changed on the aggregate; it must be saved. */
    UPDATED
  }

  /**
   * Applies the row.
   *
   * @param participant active Participant locked by the caller, already matched to the row
   * @param agreement the agreement that owns the Participant, with its service scope
   * @param entry what the Participant must become: a file row or a manual entry
   * @param actor account performing the import
   * @param now one instant for the whole import
   * @throws ConflictException when the row newly marks a service that is no longer offered
   */
  public Result apply(
      HealthExaminationParticipant participant,
      HealthExaminationAgreement agreement,
      ExaminationDetailEntry entry,
      AggregateId actor,
      Instant now) {
    Set<AggregateId> ticked = new LinkedHashSet<>();
    for (var serviceId : entry.performedServiceIds()) ticked.add(AggregateId.of(serviceId));
    Set<AggregateId> performedNow = participant.performedAgreementServiceIds();

    if (ticked.isEmpty() && participant.reconciledAgreementServiceIds().isEmpty())
      return Result.UNCHANGED;
    boolean servicesChange =
        !ticked.equals(performedNow)
            || participant.getReconciliationStatus() != ReconciliationStatus.RECONCILED;
    if (!servicesChange) return Result.UNCHANGED;

    for (AggregateId serviceId : ticked) {
      if (performedNow.contains(serviceId)) continue;
      var agreementService = agreement.service(serviceId);
      if (agreementService == null || !agreementService.active())
        throw new ConflictException(
            entry.locator() + ": a marked service is no longer offered in this agreement");
    }

    List<HealthExaminationParticipantService> reconciled = new ArrayList<>();
    for (var existing : participant.services())
      reconciled.add(
          existing.recordPerformed(ticked.contains(existing.agreementServiceId()), actor, now));
    for (AggregateId serviceId : ticked)
      if (!participant.reconciledAgreementServiceIds().contains(serviceId))
        reconciled.add(
            HealthExaminationParticipantService.newPerformed(
                AggregateId.of(UuidV7Generator.generate()),
                participant.getAgreementId(),
                participant.getId(),
                agreement.service(serviceId),
                actor,
                now));
    participant.reconcileServices(reconciled, agreement.services(), actor, now);
    return Result.UPDATED;
  }
}
