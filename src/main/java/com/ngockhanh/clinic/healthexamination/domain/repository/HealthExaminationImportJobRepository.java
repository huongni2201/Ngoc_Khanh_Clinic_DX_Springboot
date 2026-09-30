package com.ngockhanh.clinic.healthexamination.domain.repository;

import java.util.Optional;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

public interface HealthExaminationImportJobRepository {
    Optional<HealthExaminationImportJob> findByIdAndBatchId(AggregateId importId, AggregateId batchId);
    Optional<HealthExaminationImportJob> findByIdAndBatchIdForUpdate(AggregateId importId, AggregateId batchId);
    void save(HealthExaminationImportJob job);
}
