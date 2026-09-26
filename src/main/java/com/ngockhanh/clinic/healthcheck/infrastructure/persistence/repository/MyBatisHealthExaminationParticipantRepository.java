package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.converter.HealthExaminationParticipantPersistenceConverter;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper.HealthExaminationParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationParticipantRecord;

@Repository
public final class MyBatisHealthExaminationParticipantRepository implements HealthExaminationParticipantRepository {
    private final HealthExaminationParticipantMyBatisMapper mapper;
    private final HealthExaminationParticipantPersistenceConverter converter =
            new HealthExaminationParticipantPersistenceConverter();

    public MyBatisHealthExaminationParticipantRepository(HealthExaminationParticipantMyBatisMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<HealthExaminationParticipant> findById(UUID id) {
        return Optional.ofNullable(converter.toDomain(mapper.findById(id)));
    }

    @Override
    public Optional<HealthExaminationParticipant> findByOrganizationAndCode(UUID organizationId, String participantCode) {
        return Optional.ofNullable(converter.toDomain(
                mapper.findByOrganizationAndCode(organizationId, participantCode)));
    }

    @Override
    public Optional<HealthExaminationParticipant> findByOrganizationAndIdentificationNumber(
            UUID organizationId, IdentificationNumber identificationNumber) {
        return Optional.ofNullable(converter.toDomain(
                mapper.findByOrganizationAndIdentificationNumber(organizationId, identificationNumber.value())));
    }

    @Override
    public void save(HealthExaminationParticipant participant) {
        HealthExaminationParticipantRecord record = converter.toRecord(participant);
        if (mapper.update(record) == 0 && mapper.insert(record) != 1) {
            throw new IllegalStateException("Health examination participant was not saved");
        }
    }
}
