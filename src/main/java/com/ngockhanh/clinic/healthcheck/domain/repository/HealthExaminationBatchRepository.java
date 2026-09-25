package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationBatch;
import java.util.Optional;

public interface HealthExaminationBatchRepository {
    Optional<HealthExaminationBatch> findById(UUID id);
    Optional<HealthExaminationBatch> findByCompanyAndCode(UUID companyId, String batchCode);
    void save(HealthExaminationBatch batch);
}
