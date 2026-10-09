package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant.*;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchParticipantService;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantServiceRecord;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

  @Override
  public Optional<HealthExaminationBatchParticipant> findInBatch(
      AggregateId batchId, AggregateId id) {
    return Optional.ofNullable(mapper.findInBatch(batchId.value(), id.value()))
        .map(r -> domain(r, mapper.findServices(List.of(r.id()))));
  }

  @Override
  public List<HealthExaminationBatchParticipant> findManyInBatchForUpdate(
      AggregateId batchId, Collection<AggregateId> ids) {
    if (ids.isEmpty()) return List.of();
    var headers =
        mapper.findManyInBatchForUpdate(
            batchId.value(), ids.stream().map(AggregateId::value).toList());
    if (headers.isEmpty()) return List.of();
    Map<UUID, List<HealthExaminationParticipantServiceRecord>> servicesByParticipant =
        mapper.findServices(headers.stream().map(HealthExaminationBatchParticipantRecord::id).toList())
            .stream()
            .collect(Collectors.groupingBy(HealthExaminationParticipantServiceRecord::batchParticipantId));
    return headers.stream()
        .map(header -> domain(header, servicesByParticipant.getOrDefault(header.id(), List.of())))
        .toList();
  }

  @Override
  public void insert(HealthExaminationBatchParticipant participant) {
    if (mapper.insertMany(List.of(record(participant))) != 1)
      throw new IllegalStateException("Participant was not inserted");
  }

  @Override
  public boolean identityTakenByOther(
      AggregateId batchId, IdentificationNumber identity, AggregateId excludeId) {
    return mapper.identityTakenByOther(batchId.value(), identity.value(), value(excludeId));
  }

  public void save(HealthExaminationBatchParticipant p, long expectedVersion) {
    if (mapper.update(record(p), expectedVersion) != 1) throw new ConcurrentUpdateException();
    if (p.services().isEmpty()) return;
    var existing =
        mapper.findServices(List.of(p.getId().value())).stream()
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

  @Override
  public void insertMany(List<HealthExaminationBatchParticipant> participants) {
    if (participants.isEmpty()) return;
    var rows =
        participants.stream()
            .map(MyBatisHealthExaminationBatchParticipantRepository::record)
            .toList();
    if (mapper.insertMany(rows) != rows.size())
      throw new IllegalStateException("Participants were not inserted");
  }

  @Override
  public long countInBatch(AggregateId batchId) {
    return mapper.countInBatch(batchId.value());
  }

  @Override
  public List<IdentificationNumber> findExistingIdentities(
      AggregateId batchId, List<IdentificationNumber> identities) {
    if (identities.isEmpty()) return List.of();
    return mapper
        .findExistingIdentities(
            batchId.value(), identities.stream().map(IdentificationNumber::value).toList())
        .stream()
        .map(IdentificationNumber::of)
        .toList();
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
            r.identificationIssueDate(),
            r.identificationIssuePlace(),
            r.ethnicity(),
            r.phone(),
            r.email(),
            r.address(),
            r.workplace(),
            r.departmentName(),
            r.positionName(),
            r.note()),
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
    var r = p.getRoster();
    return HealthExaminationBatchParticipantRecord.builder()
        .id(p.getId().value())
        .batchId(p.getBatchId().value())
        .batchDayId(p.getBatchDayId().value())
        .participantCode(r.participantCode())
        .fullName(r.fullName())
        .dateOfBirth(r.dateOfBirth())
        .sex(r.sex())
        .identificationNumber(r.identificationNumber().value())
        .identificationIssueDate(r.identificationIssueDate())
        .identificationIssuePlace(r.identificationIssuePlace())
        .ethnicity(r.ethnicity())
        .phone(r.phone())
        .email(r.email())
        .address(r.address())
        .workplace(r.workplace())
        .departmentName(r.departmentName())
        .positionName(r.positionName())
        .note(r.note())
        .patientId(value(p.getPatientId()))
        .rosterStatus(p.getRosterStatus().name())
        .attendanceStatus(p.getAttendanceStatus().name())
        .actualExaminationDate(p.getActualExaminationDate())
        .attendanceRecordedBy(value(p.getAttendanceRecordedBy()))
        .attendanceRecordedAt(p.getAttendanceRecordedAt())
        .attendanceNote(p.getAttendanceNote())
        .serviceReconciliationStatus(p.getReconciliationStatus().name())
        .servicesReconciledBy(value(p.getReconciledBy()))
        .servicesReconciledAt(p.getReconciledAt())
        .importJobId(value(p.getImportJobId()))
        .sourceRowNumber(p.getSourceRowNumber())
        .preparedAt(p.getPreparedAt())
        .createdAt(p.getCreatedAt())
        .updatedAt(p.getUpdatedAt())
        .rowVersion(p.getRowVersion())
        .build();
  }

  private static AggregateId id(UUID id) {
    return id == null ? null : AggregateId.of(id);
  }

  private static UUID value(AggregateId id) {
    return id == null ? null : id.value();
  }
}
