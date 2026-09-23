package com.ngockhanh.clinic.healthcheck.domain.port;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthCheckRecord;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.ShsCode;
import java.util.Optional;

public interface HealthCheckRecordRepository {
    Optional<HealthCheckRecord> findById(UUID id);
    Optional<HealthCheckRecord> findByShsCode(ShsCode code);
    Optional<HealthCheckRecord> findByEncounterId(UUID encounterId);
    void save(HealthCheckRecord record);
}
