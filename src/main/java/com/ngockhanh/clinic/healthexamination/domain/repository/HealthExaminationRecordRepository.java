package com.ngockhanh.clinic.healthexamination.domain.repository;

import java.util.Optional;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationRecord;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ShsCode;

public interface HealthExaminationRecordRepository {
    Optional<HealthExaminationRecord> findById(AggregateId id);
    Optional<HealthExaminationRecord> findByShsCode(ShsCode code);
    Optional<HealthExaminationRecord> findByEncounterId(AggregateId encounterId);
    void save(HealthExaminationRecord record);
}
