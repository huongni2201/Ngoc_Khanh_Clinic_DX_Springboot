package com.ngockhanh.clinic.healthexamination.domain.repository;

import java.util.Optional;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

public interface HealthExaminationImportJobRepository {
    Optional<HealthExaminationImportJob> findById(AggregateId id);
    Optional<HealthExaminationImportJob> findByIdForUpdate(AggregateId id);
    void save(HealthExaminationImportJob job);
}
