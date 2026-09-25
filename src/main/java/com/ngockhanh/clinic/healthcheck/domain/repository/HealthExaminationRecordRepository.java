package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationRecord;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.ShsCode;
import java.util.Optional;

public interface HealthExaminationRecordRepository {
    Optional<HealthExaminationRecord> findById(UUID id);
    Optional<HealthExaminationRecord> findByShsCode(ShsCode code);
    Optional<HealthExaminationRecord> findByEncounterId(UUID encounterId);
    void save(HealthExaminationRecord record);
}
