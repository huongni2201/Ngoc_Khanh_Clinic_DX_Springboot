package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationParticipantMyBatisMapper;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.converter.HealthExaminationParticipantPersistenceConverter;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantRecord;

@Repository
@RequiredArgsConstructor
public final class  MyBatisHealthExaminationParticipantRepository implements HealthExaminationParticipantRepository {
    private final HealthExaminationParticipantMyBatisMapper mapper;
    private final HealthExaminationParticipantPersistenceConverter converter =
            new HealthExaminationParticipantPersistenceConverter();

    @Override
    public Optional<HealthExaminationParticipant> findById(AggregateId id) {
        return Optional.ofNullable(converter.toDomain(mapper.findById(id.value())));
    }

    @Override
    public Optional<HealthExaminationParticipant> findByOrganizationAndCode(AggregateId organizationId,
                                                                             String participantCode) {
        return Optional.ofNullable(converter.toDomain(
                mapper.findByOrganizationAndCode(organizationId.value(), participantCode)));
    }

    @Override
    public Optional<HealthExaminationParticipant> findByOrganizationAndIdentificationNumber(
            AggregateId organizationId, IdentificationNumber identificationNumber) {
        return Optional.ofNullable(converter.toDomain(
                mapper.findByOrganizationAndIdentificationNumber(organizationId.value(), identificationNumber.value())));
    }

    @Override
    public List<HealthExaminationParticipant> findByOrganizationAndCodes(AggregateId organizationId,
                                                                          Collection<String> participantCodes) {
        if (participantCodes.isEmpty()) return List.of();
        return mapper.findByOrganizationAndCodes(organizationId.value(), participantCodes).stream()
                .map(converter::toDomain).toList();
    }

    @Override
    public List<HealthExaminationParticipant> findByOrganizationAndIdentificationNumbers(
            AggregateId organizationId, Collection<IdentificationNumber> identificationNumbers) {
        if (identificationNumbers.isEmpty()) return List.of();
        List<String> values = identificationNumbers.stream().map(IdentificationNumber::value).toList();
        return mapper.findByOrganizationAndIdentificationNumbers(organizationId.value(), values).stream()
                .map(converter::toDomain).toList();
    }

    @Override
    public void save(HealthExaminationParticipant participant) {
        HealthExaminationParticipantRecord record = converter.toRecord(participant);
        if (mapper.update(record) == 0 && mapper.insert(record) != 1) {
            throw new IllegalStateException("Health examination participant was not saved");
        }
    }
}
