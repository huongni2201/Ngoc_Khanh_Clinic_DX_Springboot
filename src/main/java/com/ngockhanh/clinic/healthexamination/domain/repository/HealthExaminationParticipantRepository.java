package com.ngockhanh.clinic.healthexamination.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;

public interface HealthExaminationParticipantRepository {
    Optional<HealthExaminationParticipant> findById(AggregateId id);
    Optional<HealthExaminationParticipant> findByOrganizationAndCode(AggregateId organizationId, String participantCode);
    Optional<HealthExaminationParticipant> findByOrganizationAndIdentificationNumber(
            AggregateId organizationId, IdentificationNumber identificationNumber);
    List<HealthExaminationParticipant> findByOrganizationAndCodes(
            AggregateId organizationId, Collection<String> participantCodes);
    List<HealthExaminationParticipant> findByOrganizationAndIdentificationNumbers(
            AggregateId organizationId, Collection<IdentificationNumber> identificationNumbers);
    List<HealthExaminationParticipant> findByOrganizationAndIdentificationNumbersForUpdate(
            AggregateId organizationId, Collection<IdentificationNumber> identificationNumbers);
    void save(HealthExaminationParticipant participant);
    void saveAll(Collection<HealthExaminationParticipant> participants);
}
