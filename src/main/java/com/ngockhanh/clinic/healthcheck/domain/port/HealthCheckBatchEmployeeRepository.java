package com.ngockhanh.clinic.healthcheck.domain.port;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthCheckBatchEmployee;
import java.util.Optional;

public interface HealthCheckBatchEmployeeRepository {
    Optional<HealthCheckBatchEmployee> findById(UUID id);
    Optional<HealthCheckBatchEmployee> findByBatchAndEmployee(UUID batchId, UUID companyEmployeeId);
    void save(HealthCheckBatchEmployee employee);
}
