package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Storage port of the health examination batch aggregate.
 *
 * <p>Every read and write ignores batches that were soft-deleted.
 */
public interface HealthExaminationBatchRepository {
  /**
   * Loads a batch with its days and services, scoped by organization.
   *
   * @param lock when true the header row is locked until the transaction ends
   */
  Optional<BatchDetails> findDetails(UUID organizationId, UUID batchId, boolean lock);

  void insert(HealthExaminationBatch batch, UUID createdBy);

  /**
   * Stores a replaced draft configuration: the header, then the day and service differences.
   *
   * <p>Days and services that keep their identifier are not recreated. The header row version is
   * incremented exactly once; a service row version is incremented only when that service changed.
   *
   * @param expectedRowVersion header version the caller read
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the header was
   *     changed or deleted since it was read
   */
  void update(HealthExaminationBatch batch, long expectedRowVersion);

  /**
   * Stores the soft deletion already applied to the aggregate; nothing is physically removed.
   *
   * @param expectedRowVersion header version the caller read
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the header was
   *     changed or deleted since it was read
   */
  void softDelete(HealthExaminationBatch batch, long expectedRowVersion);

  /** Returns the subset of the given day identifiers that a Participant is scheduled on. */
  Set<UUID> findReferencedDayIds(UUID batchId, Collection<UUID> dayIds);

  /** Returns the subset of the given batch service identifiers that a Participant is linked to. */
  Set<UUID> findReferencedBatchServiceIds(UUID batchId, Collection<UUID> batchServiceIds);

  /**
   * Returns the highest running number used by a batch code that starts with the prefix, or 0 when
   * none does. Deleted batches count, because their codes stay reserved.
   *
   * <p>First takes a transaction-scoped lock that serialises code generation, so the caller must
   * insert its batch in the same transaction. Codes that do not match {@code <prefix><digits>}
   * (for example codes entered by hand in the past) are ignored.
   */
  long highestCodeSequence(String prefix);

  /** Whether the batch has any Participant, whatever the roster status. */
  boolean hasParticipants(UUID batchId);

  List<BatchSummary> findPage(
      UUID organizationId, long offset, int limit, String pattern, String sortKey, String sortBy);

  long count(UUID organizationId, String pattern);

  record BatchDetails(
      HealthExaminationBatch batch, UUID createdBy, Instant createdAt, Instant updatedAt) {}

  record BatchSummary(
      UUID id,
      String batchCode,
      String batchName,
      LocalDate startDate,
      LocalDate endDate,
      BatchStatus status,
      Instant createdAt,
      Instant updatedAt,
      long rowVersion) {}

  record HealthExaminationBatchReference(
      AggregateId id,
      AggregateId organizationId,
      List<HealthExaminationBatchDay> days,
      BatchStatus status,
      long rowVersion) {
    public HealthExaminationBatchReference {
      days = List.copyOf(days);
    }
  }
}
