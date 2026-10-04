package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class HealthExaminationImportJob {
  private final AggregateId id;
  private final AggregateId batchId;
  private final AggregateId createdByUserId;
  private final Instant createdAt;
  private List<AggregateId> selectedBatchDayIds;
  private final List<HealthExaminationImportRow> rows;
  private ImportStatus status;
  private AggregateId confirmedByUserId;
  private Instant confirmedAt;
  private Instant cancelledAt;
  private final Instant expiresAt;
  private long rowVersion;
  private boolean persisted;
  private ConfirmationResult confirmedResult;

  public record ConfirmationResult(int importedRows) {
    public ConfirmationResult {
      if (importedRows < 1)
        throw new IllegalArgumentException("Confirmation requires imported rows");
    }
  }

  public HealthExaminationImportJob(
      AggregateId id,
      AggregateId batchId,
      AggregateId createdByUserId,
      Instant createdAt,
      List<AggregateId> selectedBatchDayIds,
      List<HealthExaminationImportRow> rows,
      ImportStatus status,
      AggregateId confirmedByUserId,
      Instant confirmedAt,
      Instant cancelledAt,
      Instant expiresAt,
      long rowVersion,
      boolean persisted,
      ConfirmationResult confirmedResult) {
    if (id == null
        || batchId == null
        || createdByUserId == null
        || createdAt == null
        || status == null
        || rowVersion < 0
        || rows == null
        || rows.isEmpty()
        || selectedBatchDayIds == null
        || selectedBatchDayIds.isEmpty()
        || selectedBatchDayIds.stream().distinct().count() != selectedBatchDayIds.size()
        || rows.stream()
            .anyMatch(
                row ->
                    !row.isValid()
                        || row.getBatchDayId() == null
                        || !selectedBatchDayIds.contains(row.getBatchDayId()))
        || rows.stream().map(HealthExaminationImportRow::getRowNumber).distinct().count()
            != rows.size()
        || rows.stream().map(HealthExaminationImportRow::getIdentificationNumber).distinct().count()
            != rows.size())
      throw new IllegalArgumentException("Only fully valid assigned rows can form an import job");
    this.id = id;
    this.batchId = batchId;
    this.createdByUserId = createdByUserId;
    this.createdAt = createdAt;
    this.selectedBatchDayIds = List.copyOf(selectedBatchDayIds);
    this.rows =
        rows.stream()
            .sorted(java.util.Comparator.comparingInt(HealthExaminationImportRow::getRowNumber))
            .toList();
    this.status = status;
    this.confirmedByUserId = confirmedByUserId;
    this.confirmedAt = confirmedAt;
    this.cancelledAt = cancelledAt;
    this.expiresAt = expiresAt;
    this.rowVersion = rowVersion;
    this.persisted = persisted;
    this.confirmedResult = confirmedResult;
    if (status == ImportStatus.CONFIRMED
        ? confirmedByUserId == null
            || confirmedAt == null
            || confirmedResult == null
            || confirmedResult.importedRows() != rows.size()
        : confirmedByUserId != null || confirmedAt != null || confirmedResult != null)
      throw new IllegalArgumentException("Invalid confirmation audit");
    if (status == ImportStatus.CANCELLED && cancelledAt == null)
      throw new IllegalArgumentException("Cancellation time is required");
  }

  public void markStored() {
    if (persisted) rowVersion++;
    else persisted = true;
  }

  public ImportType type() {
    return ImportType.ORGANIZATION_PARTICIPANT;
  }

  public boolean isConfirmed() {
    return status == ImportStatus.CONFIRMED;
  }

  public void requireEditable(Instant now) {
    if (now == null) throw new IllegalArgumentException("Import lifecycle time is required");
    if (status != ImportStatus.VALIDATED || (expiresAt != null && !expiresAt.isAfter(now)))
      throw new DomainRuleViolation("Import is no longer editable");
  }

  public void reviseDays(List<AggregateId> days, Instant now) {
    requireEditable(now);
    if (days == null
        || days.isEmpty()
        || days.stream().distinct().count() != days.size()
        || rows.stream().anyMatch(row -> !days.contains(row.getBatchDayId())))
      throw new DomainRuleViolation("Import assignments must belong to the selected days");
    selectedBatchDayIds = List.copyOf(days);
  }

  public void confirm(AggregateId actor, Instant at) {
    requireEditable(at);
    if (actor == null || rows.stream().anyMatch(row -> row.getResolvedBatchParticipantId() == null))
      throw new DomainRuleViolation("Import rows must be committed before confirmation");
    status = ImportStatus.CONFIRMED;
    confirmedByUserId = actor;
    confirmedAt = at;
    confirmedResult = new ConfirmationResult(rows.size());
  }

  public void cancel(Instant now) {
    requireEditable(now);
    status = ImportStatus.CANCELLED;
    cancelledAt = now;
  }
}
