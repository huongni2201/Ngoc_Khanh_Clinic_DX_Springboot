package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.BatchParticipantSummaryMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantServiceRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository.BatchParticipantSummary;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;

@Repository
@RequiredArgsConstructor
public class MyBatisHealthExaminationBatchParticipantRepository
        implements HealthExaminationBatchParticipantRepository {
    private final HealthExaminationBatchParticipantMyBatisMapper mapper;
    private final BatchParticipantSummaryMyBatisMapper summaryMapper;

    @Override
    public Optional<HealthExaminationBatchParticipant> findById(AggregateId id) {
        return Optional.ofNullable(toDomain(mapper.findById(id.value())));
    }

    @Override
    public Optional<HealthExaminationBatchParticipant> findByBatchAndParticipant(
            AggregateId batchId, AggregateId participantId) {
        return Optional.ofNullable(toDomain(mapper.findByBatchAndParticipant(
                batchId.value(), participantId.value())));
    }

    @Override
    public List<BatchParticipantSummary> findByBatch(AggregateId batchId, long offset, long limit,
                                                     String searchPattern, String sortKey, String sortBy) {
        return summaryMapper.findByBatch(batchId.value(), offset, limit, searchPattern, sortKey, sortBy)
                .stream().map(Converter::toSummary).toList();
    }

    @Override
    public long countByBatch(AggregateId batchId, String searchPattern) {
        return summaryMapper.countByBatch(batchId.value(), searchPattern);
    }

    @Override
    public Set<AggregateId> findParticipantIdsByBatch(AggregateId batchId,
                                                       Collection<AggregateId> participantIds) {
        if (participantIds.isEmpty()) return Set.of();
        List<java.util.UUID> ids = participantIds.stream().map(AggregateId::value).toList();
        return mapper.findParticipantIdsByBatch(batchId.value(), ids).stream()
                .map(AggregateId::new).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public void save(HealthExaminationBatchParticipant participant) {
        HealthExaminationBatchParticipantRecord record = Converter.toRecord(participant);
        if (mapper.insert(record) != 1) throw new IllegalStateException("Batch participant was not saved");
        List<HealthExaminationBatchParticipantServiceRecord> assignments = participant.assignments().stream()
                .map(assignment -> Converter.toRecord(participant, assignment))
                .toList();
        if (!assignments.isEmpty() && mapper.insertAssignments(assignments) != assignments.size()) {
            throw new IllegalStateException("Batch participant service assignments were not saved");
        }
    }

    private HealthExaminationBatchParticipant toDomain(HealthExaminationBatchParticipantRecord record) {
        if (record == null) return null;
        return Converter.toDomain(record, mapper.findAssignments(record.id()));
    }

    private static final class Converter {
        static HealthExaminationBatchParticipantRecord toRecord(HealthExaminationBatchParticipant participant) {
            return new HealthExaminationBatchParticipantRecord(
                participant.id().value(), participant.batchId().value(),
                participant.healthExaminationParticipantId().value(), participant.participantCodeSnapshot(),
                participant.departmentSnapshot(), participant.jobTitleSnapshot(), participant.occupationSnapshot(),
                participant.fullNameSnapshot(), participant.dateOfBirthSnapshot(), participant.sexSnapshot(),
                participant.identificationNumberSnapshot().value(), participant.identificationNumberIssueDateSnapshot(),
                participant.identificationNumberIssuePlaceSnapshot(), participant.ethnicitySnapshot(),
                participant.subjectTypeSnapshot(), participant.payerSourceSnapshot(), participant.bloodGroupSnapshot(),
                participant.phoneSnapshot(), participant.provinceSnapshot(), participant.wardSnapshot(),
                participant.addressDetailSnapshot(), participant.administrativeOccupationSnapshot(),
                participant.workplaceOrSchoolSnapshot(), participant.healthExaminationReasonSnapshot(),
                "REGISTERED", null);
        }

        static HealthExaminationBatchParticipantServiceRecord toRecord(
                HealthExaminationBatchParticipant participant,
                HealthExaminationBatchParticipantService assignment) {
            return new HealthExaminationBatchParticipantServiceRecord(
                    assignment.id().value(), participant.id().value(), assignment.batchServiceId().value(),
                    assignment.serviceRequestId() == null ? null : assignment.serviceRequestId().value(),
                    assignment.billable(), assignment.unitPrice().amount(),
                    null, null);
        }

        static HealthExaminationBatchParticipant toDomain(HealthExaminationBatchParticipantRecord record,
                    List<HealthExaminationBatchParticipantServiceRecord> assignmentRecords) {
            if (record == null) return null;
            List<HealthExaminationBatchParticipantService> assignments = assignmentRecords.stream()
                    .map(service -> HealthExaminationBatchParticipantService.restore(
                            new AggregateId(service.id()), new AggregateId(service.healthExaminationBatchServiceId()),
                            service.serviceRequestId() == null ? null : new AggregateId(service.serviceRequestId()),
                            new Money(service.unitPriceSnapshot(), "VND"), service.billable()))
                    .toList();
            return HealthExaminationBatchParticipant.restore(new AggregateId(record.id()),
                    new AggregateId(record.healthExaminationBatchId()),
                    new AggregateId(record.healthExaminationParticipantId()), record.participantCodeSnapshot(),
                    record.departmentSnapshot(), record.jobTitleSnapshot(), record.occupationSnapshot(),
                    record.fullNameSnapshot(), record.dateOfBirthSnapshot(), record.sexSnapshot(),
                    IdentificationNumber.of(record.identificationNumberSnapshot()),
                    record.identificationNumberIssueDateSnapshot(), record.identificationNumberIssuePlaceSnapshot(),
                    record.ethnicitySnapshot(), record.subjectTypeSnapshot(), record.payerSourceSnapshot(),
                    record.bloodGroupSnapshot(), record.phoneSnapshot(), record.provinceSnapshot(), record.wardSnapshot(),
                    record.addressDetailSnapshot(), record.administrativeOccupationSnapshot(),
                    record.workplaceOrSchoolSnapshot(), record.healthExaminationReasonSnapshot(), assignments);
        }

        static BatchParticipantSummary toSummary(HealthExaminationBatchParticipantRecord record) {
            return new BatchParticipantSummary(new AggregateId(record.id()),
                    new AggregateId(record.healthExaminationParticipantId()), record.participantCodeSnapshot(),
                    record.departmentSnapshot(), record.jobTitleSnapshot(), record.occupationSnapshot(),
                    record.fullNameSnapshot(), record.dateOfBirthSnapshot(), record.sexSnapshot(),
                    record.identificationNumberSnapshot(), record.identificationNumberIssueDateSnapshot(),
                    record.identificationNumberIssuePlaceSnapshot(), record.ethnicitySnapshot(),
                    record.subjectTypeSnapshot(), record.payerSourceSnapshot(), record.bloodGroupSnapshot(),
                    record.phoneSnapshot(), record.provinceSnapshot(), record.wardSnapshot(),
                    record.addressDetailSnapshot(), record.administrativeOccupationSnapshot(),
                    record.workplaceOrSchoolSnapshot(), record.healthExaminationReasonSnapshot(),
                    record.status(), record.createdAt());
        }
    }
}
