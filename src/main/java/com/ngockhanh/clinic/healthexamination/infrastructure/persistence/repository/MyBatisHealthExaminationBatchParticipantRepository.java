package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.*;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantServiceRecord;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisHealthExaminationBatchParticipantRepository
    implements HealthExaminationBatchParticipantRepository {
  private final HealthExaminationBatchParticipantMyBatisMapper mapper;

  public Optional<HealthExaminationBatchParticipant> findById(AggregateId id) {
    return Optional.ofNullable(mapper.findById(id.value()))
        .map(r -> domain(r, mapper.findServices(List.of(r.id()))));
  }

  public void save(HealthExaminationBatchParticipant p, long expectedVersion) {
    if (mapper.update(record(p), expectedVersion) != 1) throw new ConcurrentUpdateException();
    if (p.services().isEmpty()) return;
    var existing =
        mapper.findServices(List.of(p.id().value())).stream()
            .map(HealthExaminationParticipantServiceRecord::id)
            .collect(Collectors.toSet());
    var rows =
        p.services().stream()
            .map(
                s ->
                    HealthExaminationParticipantServiceRecord.builder()
                        .id(s.id().value())
                        .batchId(s.batchId().value())
                        .batchParticipantId(s.batchParticipantId().value())
                        .batchServiceId(s.batchServiceId().value())
                        .isPerformed(s.performed())
                        .serviceRequestId(value(s.serviceRequestId()))
                        .unitPriceSnapshot(s.unitPriceSnapshot().amount())
                        .recordedBy(s.recordedBy().value())
                        .recordedAt(s.recordedAt())
                        .createdAt(s.createdAt())
                        .updatedAt(s.updatedAt())
                        .rowVersion(s.rowVersion())
                        .build())
            .toList();
    var added = rows.stream().filter(r -> !existing.contains(r.id())).toList();
    var changed = rows.stream().filter(r -> existing.contains(r.id())).toList();
    if (!added.isEmpty() && mapper.insertServices(added) != added.size())
      throw new IllegalStateException("Participant services were not inserted");
    if (!changed.isEmpty() && mapper.updateServices(changed) != changed.size())
      throw new ConcurrentUpdateException();
  }

  private static HealthExaminationBatchParticipant domain(
      HealthExaminationBatchParticipantRecord r,
      List<HealthExaminationParticipantServiceRecord> services) {
    return HealthExaminationBatchParticipant.restore(
        id(r.id()),
        id(r.batchId()),
        id(r.batchDayId()),
        new Roster(
            r.participantCode(),
            r.fullName(),
            r.dateOfBirth(),
            r.sex(),
            IdentificationNumber.of(r.identificationNumber()),
            r.phone(),
            r.email(),
            r.departmentName(),
            r.positionName()),
        new Progress(
            id(r.patientId()),
            RosterStatus.valueOf(r.rosterStatus()),
            AttendanceStatus.valueOf(r.attendanceStatus()),
            r.actualExaminationDate(),
            id(r.attendanceRecordedBy()),
            r.attendanceRecordedAt(),
            r.attendanceNote(),
            ReconciliationStatus.valueOf(r.serviceReconciliationStatus()),
            id(r.servicesReconciledBy()),
            r.servicesReconciledAt(),
            r.preparedAt()),
        id(r.importJobId()),
        r.sourceRowNumber(),
        r.createdAt(),
        r.updatedAt(),
        r.rowVersion(),
        services.stream()
            .map(
                s ->
                    new HealthExaminationBatchParticipantService(
                        id(s.id()),
                        id(s.batchId()),
                        id(s.batchParticipantId()),
                        id(s.batchServiceId()),
                        s.isPerformed(),
                        id(s.serviceRequestId()),
                        new Money(s.unitPriceSnapshot(), "VND"),
                        id(s.recordedBy()),
                        s.recordedAt(),
                        s.createdAt(),
                        s.updatedAt(),
                        s.rowVersion()))
            .toList());
  }

  private static HealthExaminationBatchParticipantRecord record(
      HealthExaminationBatchParticipant p) {
    var r = p.roster();
    return HealthExaminationBatchParticipantRecord.builder()
        .id(p.id().value())
        .batchId(p.batchId().value())
        .batchDayId(p.batchDayId().value())
        .participantCode(r.participantCode())
        .fullName(r.fullName())
        .dateOfBirth(r.dateOfBirth())
        .sex(r.sex())
        .identificationNumber(r.identificationNumber().value())
        .phone(r.phone())
        .email(r.email())
        .departmentName(r.departmentName())
        .positionName(r.positionName())
        .patientId(value(p.patientId()))
        .rosterStatus(p.rosterStatus().name())
        .attendanceStatus(p.attendanceStatus().name())
        .actualExaminationDate(p.actualExaminationDate())
        .attendanceRecordedBy(value(p.attendanceRecordedBy()))
        .attendanceRecordedAt(p.attendanceRecordedAt())
        .attendanceNote(p.attendanceNote())
        .serviceReconciliationStatus(p.reconciliationStatus().name())
        .servicesReconciledBy(value(p.reconciledBy()))
        .servicesReconciledAt(p.reconciledAt())
        .importJobId(value(p.importJobId()))
        .sourceRowNumber(p.sourceRowNumber())
        .preparedAt(p.preparedAt())
        .createdAt(p.createdAt())
        .updatedAt(p.updatedAt())
        .rowVersion(p.rowVersion())
        .build();
  }

  private static AggregateId id(UUID id) {
    return id == null ? null : AggregateId.of(id);
  }

  private static UUID value(AggregateId id) {
    return id == null ? null : id.value();
  }
}
