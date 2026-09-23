package com.ngockhanh.clinic.healthcheck.domain.port;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthCheckImportJob;
import java.util.Optional;

public interface HealthCheckImportJobRepository {
    Optional<HealthCheckImportJob> findById(UUID id);
    void save(HealthCheckImportJob job);
}
