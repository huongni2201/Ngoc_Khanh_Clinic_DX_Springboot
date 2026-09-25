package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationBatchEmployee;
import java.util.Optional;

public interface HealthExaminationBatchEmployeeRepository {
    Optional<HealthExaminationBatchEmployee> findById(UUID id);
    Optional<HealthExaminationBatchEmployee> findByBatchAndEmployee(UUID batchId, UUID companyEmployeeId);
    void save(HealthExaminationBatchEmployee employee);
}
