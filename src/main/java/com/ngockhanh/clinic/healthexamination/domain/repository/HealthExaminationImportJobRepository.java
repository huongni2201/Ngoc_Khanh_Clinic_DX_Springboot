package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.util.List;
import java.util.Optional;

public interface HealthExaminationImportJobRepository {
  Optional<HealthExaminationImportJob> findByIdAndBatchId(
      AggregateId importId, AggregateId batchId);

  Optional<HealthExaminationImportJob> findByIdAndBatchIdForUpdate(
      AggregateId importId, AggregateId batchId);

  long countRowsByJobId(AggregateId jobId, String rowFilter);

  List<HealthExaminationImportRow> findRowsByJobId(
      AggregateId jobId, String rowFilter, long offset, int limit);

  void save(HealthExaminationImportJob job);
}
