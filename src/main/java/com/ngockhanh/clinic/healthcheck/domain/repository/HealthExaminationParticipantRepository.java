package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;

public interface HealthExaminationParticipantRepository {
    Optional<HealthExaminationParticipant> findById(UUID id);
    Optional<HealthExaminationParticipant> findByOrganizationAndCode(UUID organizationId, String participantCode);
    Optional<HealthExaminationParticipant> findByOrganizationAndIdentificationNumber(
            UUID organizationId, IdentificationNumber identificationNumber);
    List<HealthExaminationParticipant> findByOrganizationAndCodes(UUID organizationId, Collection<String> participantCodes);
    List<HealthExaminationParticipant> findByOrganizationAndIdentificationNumbers(
            UUID organizationId, Collection<IdentificationNumber> identificationNumbers);
    void save(HealthExaminationParticipant participant);
}
