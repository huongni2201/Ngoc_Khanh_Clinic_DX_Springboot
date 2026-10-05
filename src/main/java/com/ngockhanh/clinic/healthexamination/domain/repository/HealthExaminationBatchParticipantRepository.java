package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface HealthExaminationBatchParticipantRepository {
  Optional<HealthExaminationBatchParticipant> findById(AggregateId id);

  Set<IdentificationNumber> existingIdentificationNumbers(
      AggregateId batchId, Collection<IdentificationNumber> numbers);

  Map<UUID, Long> activeCountsByDay(AggregateId batchId);

  void insertAll(Collection<HealthExaminationBatchParticipant> participants);

  void save(HealthExaminationBatchParticipant participant, long expectedRowVersion);
}
