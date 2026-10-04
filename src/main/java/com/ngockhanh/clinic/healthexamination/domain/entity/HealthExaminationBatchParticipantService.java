package com.ngockhanh.clinic.healthexamination.domain.entity;

import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import java.time.Instant;

/** Staff reconciliation of a performed batch service, with its original price snapshot. */
public record HealthExaminationBatchParticipantService(
    AggregateId id,
    AggregateId batchId,
    AggregateId batchParticipantId,
    AggregateId batchServiceId,
    boolean performed,
    AggregateId serviceRequestId,
    Money unitPriceSnapshot,
    AggregateId recordedBy,
    Instant recordedAt,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {
  public HealthExaminationBatchParticipantService {
    if (id == null
        || batchId == null
        || batchParticipantId == null
        || batchServiceId == null
        || unitPriceSnapshot == null
        || recordedBy == null
        || recordedAt == null
        || createdAt == null
        || updatedAt == null
        || rowVersion < 0) throw new IllegalArgumentException("Invalid reconciled service");
  }

  public HealthExaminationBatchParticipantService recordPerformed(
      boolean performed, AggregateId actor, Instant at) {
    return new HealthExaminationBatchParticipantService(
        id,
        batchId,
        batchParticipantId,
        batchServiceId,
        performed,
        serviceRequestId,
        unitPriceSnapshot,
        actor,
        at,
        createdAt,
        at,
        rowVersion);
  }

  public HealthExaminationBatchParticipantService linkServiceRequest(AggregateId request) {
    if (request == null) throw new IllegalArgumentException("Service request is required");
    if (serviceRequestId != null && !serviceRequestId.equals(request))
      throw new DomainRuleViolation("Service Request relink forbidden");
    return new HealthExaminationBatchParticipantService(
        id,
        batchId,
        batchParticipantId,
        batchServiceId,
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
