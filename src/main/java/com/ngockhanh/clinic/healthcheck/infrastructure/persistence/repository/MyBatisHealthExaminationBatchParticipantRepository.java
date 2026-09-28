package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthcheck.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.HealthExaminationBatchParticipantId;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.Money;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;

@Repository
public final class MyBatisHealthExaminationBatchParticipantRepository implements HealthExaminationBatchParticipantRepository {
    private final HealthExaminationBatchParticipantMyBatisMapper mapper;

    public MyBatisHealthExaminationBatchParticipantRepository(HealthExaminationBatchParticipantMyBatisMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<HealthExaminationBatchParticipant> findById(HealthExaminationBatchParticipantId id) {
        return Optional.ofNullable(toDomain(mapper.findById(id.value())));
    }

    @Override
    public Optional<HealthExaminationBatchParticipant> findByBatchAndParticipant(UUID batchId, UUID participantId) {
        return Optional.ofNullable(toDomain(mapper.findByBatchAndParticipant(batchId, participantId)));
    }

    @Override
    public Set<UUID> findParticipantIdsByBatch(UUID batchId, Collection<UUID> participantIds) {
        if (participantIds.isEmpty()) return Set.of();
        return Set.copyOf(mapper.findParticipantIdsByBatch(batchId, participantIds));
    }

    @Override
    public void save(HealthExaminationBatchParticipant participant) {
        AdministrativeSnapshot snapshot = participant.rosterSnapshot();
        HealthExaminationBatchParticipantRecord record = new HealthExaminationBatchParticipantRecord(
                participant.id().value(), participant.batchId(), participant.healthExaminationParticipantId(),
                participant.participantCodeSnapshot(), participant.departmentSnapshot(), participant.jobTitleSnapshot(),
                participant.occupationSnapshot(), snapshot.fullName(), snapshot.dateOfBirth(), snapshot.sex(),
                snapshot.identificationNumber().value(), snapshot.identificationNumberIssueDate(),
                snapshot.identificationNumberIssuePlace(), snapshot.ethnicity(), snapshot.subjectType(),
                snapshot.payerSource(), snapshot.bloodGroup(), snapshot.phone(), snapshot.province(), snapshot.ward(),
                snapshot.addressDetail(), snapshot.occupation(), snapshot.workplaceOrSchool(),
                snapshot.healthExaminationReason(), "REGISTERED", null);
        if (mapper.insert(record) != 1) throw new IllegalStateException("Batch participant was not saved");
    }

    private HealthExaminationBatchParticipant toDomain(HealthExaminationBatchParticipantRecord record) {
        if (record == null) return null;
        try {
            AdministrativeSnapshot snapshot = toSnapshot(record);
            List<HealthExaminationBatchParticipantService> assignments = mapper.findAssignments(record.id()).stream()
                    .map(service -> HealthExaminationBatchParticipantService.restore(service.id(),
                            service.healthExaminationBatchServiceId(), service.serviceRequestId(),
                            new Money(service.unitPriceSnapshot(), "VND"), service.billable()))
                    .toList();
            return HealthExaminationBatchParticipant.restore(new HealthExaminationBatchParticipantId(record.id()),
                    record.healthExaminationBatchId(), record.healthExaminationParticipantId(), snapshot,
                    record.participantCodeSnapshot(), record.departmentSnapshot(), record.jobTitleSnapshot(),
                    record.occupationSnapshot(), assignments);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Unable to read participant snapshot", failure);
        }
    }

    private static AdministrativeSnapshot toSnapshot(HealthExaminationBatchParticipantRecord record) {
        return new AdministrativeSnapshot(record.fullNameSnapshot(), record.dateOfBirthSnapshot(), record.sexSnapshot(),
                IdentificationNumber.of(record.identificationNumberSnapshot()),
                record.identificationNumberIssueDateSnapshot(), record.identificationNumberIssuePlaceSnapshot(),
                record.ethnicitySnapshot(), record.subjectTypeSnapshot(), record.payerSourceSnapshot(),
                record.bloodGroupSnapshot(), record.phoneSnapshot(), record.provinceSnapshot(), record.wardSnapshot(),
                record.addressDetailSnapshot(), record.administrativeOccupationSnapshot(),
                record.workplaceOrSchoolSnapshot(), record.healthExaminationReasonSnapshot());
    }
}
