package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.enums.HealthExaminationRecordStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.time.Instant;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Stable visit identity. Administrative and clinical versions are separate history records. */
@Getter
public class HealthExaminationRecord {
  private final AggregateId id;
  private final AggregateId batchParticipantId;
  private final AggregateId encounterId;
  private final String mrn;
  private HealthExaminationRecordStatus status;
  private final Instant preparedAt;
  private Instant issuedAt;
  private Instant finalizedAt;
  private Instant cancelledAt;
  private String cancelReason;
  private final long rowVersion;

  private HealthExaminationRecord(
      AggregateId id,
      AggregateId participant,
      AggregateId encounter,
      String mrn,
      HealthExaminationRecordStatus status,
      Instant preparedAt,
      Instant issuedAt,
      Instant finalizedAt,
      Instant cancelledAt,
      String reason,
      long version) {
    if (id == null
        || encounter == null
        || mrn == null
        || mrn.isBlank()
        || mrn.length() > 100
        || status == null
        || preparedAt == null
        || version < 0
        || ((status == HealthExaminationRecordStatus.COMPLETED
                || status == HealthExaminationRecordStatus.ISSUED)
            && finalizedAt == null)
        || (status == HealthExaminationRecordStatus.ISSUED && issuedAt == null)
        || (status == HealthExaminationRecordStatus.CANCELLED && cancelledAt == null))
      throw new IllegalArgumentException("Invalid health examination record");
    this.id = id;
    this.batchParticipantId = participant;
    this.encounterId = encounter;
    this.mrn = mrn;
    this.status = status;
    this.preparedAt = preparedAt;
    this.issuedAt = issuedAt;
    this.finalizedAt = finalizedAt;
    this.cancelledAt = cancelledAt;
    this.cancelReason = reason;
    this.rowVersion = version;
  }

  public static HealthExaminationRecord prepare(
      AggregateId id, AggregateId participant, AggregateId encounter, String mrn, Instant at) {
    return restore(
        id,
        participant,
        encounter,
        mrn,
        HealthExaminationRecordStatus.PREPARED,
        at,
        null,
        null,
        null,
        null,
        0);
  }

  public static HealthExaminationRecord restore(
      AggregateId id,
      AggregateId participant,
      AggregateId encounter,
      String mrn,
      HealthExaminationRecordStatus status,
      Instant preparedAt,
      Instant issuedAt,
      Instant finalizedAt,
      Instant cancelledAt,
      String reason,
      long version) {
    return new HealthExaminationRecord(
        id,
        participant,
        encounter,
        mrn,
        status,
        preparedAt,
        issuedAt,
        finalizedAt,
        cancelledAt,
        reason,
        version);
  }

  public void start() {
    require(HealthExaminationRecordStatus.PREPARED);
    status = HealthExaminationRecordStatus.IN_PROGRESS;
  }

  public void complete(Instant at) {
    require(HealthExaminationRecordStatus.IN_PROGRESS);
    requireTime(at);
    finalizedAt = at;
    status = HealthExaminationRecordStatus.COMPLETED;
  }

  public void issue(Instant at) {
    require(HealthExaminationRecordStatus.COMPLETED);
    requireTime(at);
    issuedAt = at;
    status = HealthExaminationRecordStatus.ISSUED;
  }

  public void cancel(Instant at, String reason) {
    if (status == HealthExaminationRecordStatus.ISSUED
        || status == HealthExaminationRecordStatus.CANCELLED)
      throw new DomainRuleViolation("Issued or cancelled records cannot be cancelled");
    requireTime(at);
    cancelledAt = at;
    cancelReason = reason;
    status = HealthExaminationRecordStatus.CANCELLED;
  }

  private void require(HealthExaminationRecordStatus expected) {
    if (status != expected)
      throw new DomainRuleViolation("Invalid health examination record transition");
  }

  private static void requireTime(Instant at) {
    if (at == null) throw new IllegalArgumentException("Lifecycle time is required");
  }
}
