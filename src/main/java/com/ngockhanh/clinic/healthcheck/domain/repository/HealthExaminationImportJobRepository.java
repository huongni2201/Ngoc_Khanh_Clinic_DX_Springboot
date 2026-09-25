package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationImportJob;
import java.util.Optional;

public interface HealthExaminationImportJobRepository {
    Optional<HealthExaminationImportJob> findById(UUID id);
    void save(HealthExaminationImportJob job);
}
