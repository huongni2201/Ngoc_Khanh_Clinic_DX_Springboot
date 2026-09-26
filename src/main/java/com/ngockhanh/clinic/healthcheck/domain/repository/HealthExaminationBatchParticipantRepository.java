package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationBatchParticipant;

public interface HealthExaminationBatchParticipantRepository {
    Optional<HealthExaminationBatchParticipant> findById(UUID id);
    Optional<HealthExaminationBatchParticipant> findByBatchAndParticipant(
            UUID batchId, UUID healthExaminationParticipantId);
    void save(HealthExaminationBatchParticipant participant);
}
