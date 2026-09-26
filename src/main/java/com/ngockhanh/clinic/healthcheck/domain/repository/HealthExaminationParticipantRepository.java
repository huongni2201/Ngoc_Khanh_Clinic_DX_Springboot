package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;

public interface HealthExaminationParticipantRepository {
    Optional<HealthExaminationParticipant> findById(UUID id);
    Optional<HealthExaminationParticipant> findByOrganizationAndCode(UUID organizationId, String participantCode);
    Optional<HealthExaminationParticipant> findByOrganizationAndIdentificationNumber(
            UUID organizationId, IdentificationNumber identificationNumber);
    void save(HealthExaminationParticipant participant);
}
