package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.Set;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.HealthExaminationBatchParticipantId;

public interface HealthExaminationBatchParticipantRepository {
    Optional<HealthExaminationBatchParticipant> findById(HealthExaminationBatchParticipantId id);
    Optional<HealthExaminationBatchParticipant> findByBatchAndParticipant(
            UUID batchId, UUID healthExaminationParticipantId);
    Set<UUID> findParticipantIdsByBatch(UUID batchId, Collection<UUID> participantIds);
    void save(HealthExaminationBatchParticipant participant);
}
