package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HealthExaminationImportJobRepository {
  Optional<Summary> findSummaryByIdAndBatchId(AggregateId importId, AggregateId batchId);

  Optional<HealthExaminationImportJob> findByIdAndBatchIdForUpdate(
      AggregateId importId, AggregateId batchId);

  long countRowsByJobId(AggregateId jobId);

  List<HealthExaminationImportRow> findRowsByJobId(AggregateId jobId, long offset, int limit);

  void save(HealthExaminationImportJob job);

  record Summary(
      AggregateId id,
      List<AggregateId> selectedBatchDayIds,
      ImportStatus status,
      Instant expiresAt,
      long rowVersion) {
    public Summary {
      if (id == null || status == null || rowVersion < 0)
        throw new IllegalArgumentException("Invalid import summary");
      selectedBatchDayIds = List.copyOf(selectedBatchDayIds);
    }
  }
}
