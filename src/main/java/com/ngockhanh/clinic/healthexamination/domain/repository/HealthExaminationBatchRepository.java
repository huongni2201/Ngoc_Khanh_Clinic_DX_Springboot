package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.time.*;
import java.util.*;

public interface HealthExaminationBatchRepository {
  Optional<HealthExaminationBatchReference> findByIdAndOrganizationId(
      AggregateId batchId, AggregateId organizationId);

  Optional<HealthExaminationBatchReference> findByIdAndOrganizationIdForUpdate(
      AggregateId batchId, AggregateId organizationId);

  Optional<BatchDetails> findDetails(UUID organizationId, UUID batchId, boolean lock);

  void insert(HealthExaminationBatch batch, UUID createdBy);

  void update(HealthExaminationBatch batch);

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

  record BatchDay(UUID id, LocalDate examinationDate) {
    public BatchDay {
      if (id == null || examinationDate == null)
        throw new IllegalArgumentException("Invalid batch day");
    }
  }

  record HealthExaminationBatchReference(
      AggregateId id,
      AggregateId organizationId,
      List<BatchDay> days,
      BatchStatus status,
      long rowVersion) {
    public HealthExaminationBatchReference {
      days = List.copyOf(days);
    }
  }
}
