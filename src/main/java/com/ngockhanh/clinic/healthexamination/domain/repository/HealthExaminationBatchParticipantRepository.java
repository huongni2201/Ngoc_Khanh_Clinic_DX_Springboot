package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.util.Optional;

public interface HealthExaminationBatchParticipantRepository {
  Optional<HealthExaminationBatchParticipant> findById(AggregateId id);

  void save(HealthExaminationBatchParticipant participant, long expectedRowVersion);
}
