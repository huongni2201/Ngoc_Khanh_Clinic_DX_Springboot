package com.ngockhanh.clinic.healthexamination.domain.entity;

import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import java.time.Instant;

/** Staff reconciliation of a performed agreement service, with its original price snapshot. */
public record HealthExaminationParticipantService(
    AggregateId id,
    AggregateId agreementId,
    AggregateId participantId,
    AggregateId agreementServiceId,
    boolean performed,
    AggregateId serviceRequestId,
    Money unitPriceSnapshot,
    AggregateId recordedBy,
    Instant recordedAt,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {
  public HealthExaminationParticipantService {
    if (id == null
        || agreementId == null
        || participantId == null
        || agreementServiceId == null
        || unitPriceSnapshot == null
        || recordedBy == null
        || recordedAt == null
        || createdAt == null
        || updatedAt == null
        || rowVersion < 0) throw new IllegalArgumentException("Invalid reconciled service");
  }

  /**
   * Creates the reconciliation row of a service that staff just recorded as performed. The price
   * snapshot is the agreement service's current negotiated price; it is never changed afterwards.
   *
   * @param id identifier of the new row
   * @param agreementId agreement the Participant and the service belong to
   * @param participantId Participant the service was performed for
   * @param agreementService agreement service that was performed
   * @param actor account recording the service
   * @param at time of the recording; also the creation time of the row
   * @throws IllegalArgumentException when an argument is missing or the service is of another
   *     agreement
   */
  public static HealthExaminationParticipantService newPerformed(
      AggregateId id,
      AggregateId agreementId,
      AggregateId participantId,
      HealthExaminationAgreementService agreementService,
      AggregateId actor,
      Instant at) {
    if (agreementService == null || !agreementService.agreementId().equals(agreementId))
      throw new IllegalArgumentException("Agreement service must belong to the agreement");
    return new HealthExaminationParticipantService(
        id,
        agreementId,
        participantId,
        agreementService.id(),
        true,
        null,
        agreementService.negotiatedPrice(),
        actor,
        at,
        at,
        at,
        0);
  }

  public HealthExaminationParticipantService recordPerformed(
      boolean performed, AggregateId actor, Instant at) {
    return new HealthExaminationParticipantService(
        id,
        agreementId,
        participantId,
        agreementServiceId,
        performed,
        serviceRequestId,
        unitPriceSnapshot,
        actor,
        at,
        createdAt,
        at,
        rowVersion);
  }

  public HealthExaminationParticipantService linkServiceRequest(AggregateId request) {
    if (request == null) throw new IllegalArgumentException("Service request is required");
    if (serviceRequestId != null && !serviceRequestId.equals(request))
      throw new DomainRuleViolation("Service Request relink forbidden");
    return new HealthExaminationParticipantService(
        id,
        agreementId,
        participantId,
        agreementServiceId,
        performed,
        request,
        unitPriceSnapshot,
        recordedBy,
        recordedAt,
        createdAt,
        updatedAt,
        rowVersion);
  }
}
