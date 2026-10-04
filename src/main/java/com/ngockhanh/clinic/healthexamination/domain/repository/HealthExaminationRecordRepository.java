package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationRecord;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.util.Optional;

public interface HealthExaminationRecordRepository {
  Optional<HealthExaminationRecord> findById(AggregateId id);

  Optional<HealthExaminationRecord> findByMrn(String mrn);

  Optional<HealthExaminationRecord> findByEncounterId(AggregateId encounterId);

  void save(HealthExaminationRecord record, long expectedRowVersion);
}
