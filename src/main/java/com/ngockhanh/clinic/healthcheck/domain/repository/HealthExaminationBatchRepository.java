package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.HealthExaminationBatchId;
import java.util.Optional;

public interface HealthExaminationBatchRepository {
    Optional<HealthExaminationBatch> findById(HealthExaminationBatchId id);
    Optional<HealthExaminationBatch> findByOrganizationAndCode(UUID organizationId, String batchCode);
    void save(HealthExaminationBatch batch);
}
