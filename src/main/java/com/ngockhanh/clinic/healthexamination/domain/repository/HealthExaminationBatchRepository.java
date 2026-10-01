package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HealthExaminationBatchRepository {
  Optional<HealthExaminationBatchReference> findByIdAndOrganizationId(
      AggregateId batchId, AggregateId organizationId);

  Optional<HealthExaminationBatchReference> findByIdAndOrganizationIdForUpdate(
      AggregateId batchId, AggregateId organizationId);

  Optional<BatchDetails> findDetails(UUID organizationId, UUID batchId, boolean lock);

  Optional<BatchDetails> findDetailsIncludingDeleted(
      UUID organizationId, UUID batchId, boolean lock);

  void insert(HealthExaminationBatch batch, UUID createdBy);

  void update(HealthExaminationBatch batch);

  boolean hasDependents(UUID batchId);

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
      String status,
      Instant createdAt,
      Instant updatedAt) {}

  record HealthExaminationBatchReference(
      AggregateId id, AggregateId organizationId, LocalDate startDate, BatchStatus status) {}
}
