package com.ngockhanh.clinic.healthexamination.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;

public interface HealthExaminationBatchParticipantRepository {
    Optional<HealthExaminationBatchParticipant> findById(AggregateId id);

    Optional<HealthExaminationBatchParticipant> findByBatchAndParticipant(
            AggregateId batchId, AggregateId healthExaminationParticipantId);

    List<BatchParticipantSummary> findByBatch(AggregateId batchId, long offset, long limit,
                                              String searchPattern, String sortKey, String sortBy);

    long countByBatch(AggregateId batchId, String searchPattern);

    Set<AggregateId> findParticipantIdsByBatch(AggregateId batchId, Collection<AggregateId> participantIds);

    List<BatchParticipantRosterSnapshot> findRosterSnapshots(AggregateId batchId,
                                                              Collection<AggregateId> participantIds);

    List<BatchParticipantRosterSnapshot> findRosterSnapshotsForUpdate(AggregateId batchId,
                                                                        Collection<AggregateId> participantIds);

    Set<AggregateId> findBatchParticipantIdsWithHealthRecords(Collection<AggregateId> batchParticipantIds);

    void insertRosterSnapshots(AggregateId batchId, Collection<BatchParticipantRosterSnapshot> snapshots);
    void updateRosterSnapshots(AggregateId batchId, Collection<BatchParticipantRosterSnapshot> snapshots);

    void save(HealthExaminationBatchParticipant participant);
    record BatchParticipantRosterSnapshot(
            AggregateId batchParticipantId,
            AggregateId participantId,
            String participantCode,
            String departmentName,
            String jobTitle,
            String occupation,
            String fullName,
            LocalDate dateOfBirth,
            String sex,
            IdentificationNumber identificationNumber,
            LocalDate identificationNumberIssueDate,
            String identificationNumberIssuePlace,
            String ethnicity,
            String subjectType,
            String payerSource,
            String bloodGroup,
            String phone,
            String province,
            String ward,
            String addressDetail,
            String administrativeOccupation,
            String workplaceOrSchool,
            String healthExaminationReason,
            String rosterNote) {}

    void updateRosterSnapshot(HealthExaminationBatchParticipant participant);
    record BatchParticipantSummary(
            AggregateId batchParticipantId, AggregateId participantId, String participantCode,
            String departmentName, String jobTitle, String occupation, String fullName,
            java.time.LocalDate dateOfBirth, String sex, String identificationNumber,
            java.time.LocalDate identificationNumberIssueDate, String identificationNumberIssuePlace,
            String ethnicity, String subjectType, String payerSource, String bloodGroup, String phone,
            String province, String ward, String addressDetail, String administrativeOccupation,
            String workplaceOrSchool, String healthExaminationReason, String status,
            java.time.Instant createdAt) {}
}
