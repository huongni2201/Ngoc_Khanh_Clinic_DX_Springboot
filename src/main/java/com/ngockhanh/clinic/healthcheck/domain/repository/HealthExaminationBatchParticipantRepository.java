package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.HealthExaminationBatchParticipantId;

public interface HealthExaminationBatchParticipantRepository {
    Optional<HealthExaminationBatchParticipant> findById(HealthExaminationBatchParticipantId id);
    Optional<HealthExaminationBatchParticipant> findByBatchAndParticipant(
            UUID batchId, UUID healthExaminationParticipantId);
    void save(HealthExaminationBatchParticipant participant);
}
