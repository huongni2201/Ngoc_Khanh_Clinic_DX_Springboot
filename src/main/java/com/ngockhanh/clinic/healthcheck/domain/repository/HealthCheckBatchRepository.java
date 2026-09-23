package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthCheckBatch;
import java.util.Optional;

public interface HealthCheckBatchRepository {
    Optional<HealthCheckBatch> findById(UUID id);
    Optional<HealthCheckBatch> findByCompanyAndCode(UUID companyId, String batchCode);
    void save(HealthCheckBatch batch);
}
