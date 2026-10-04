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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  public List<HealthExaminationBatchParticipant> findByBatch(
      AggregateId id, long offset, long limit, String search, String sort, String direction) {
    var rows = mapper.findByBatch(id.value(), offset, limit, search, sort, direction);
    if (rows.isEmpty()) return List.of();
    var services =
        mapper
            .findServices(rows.stream().map(HealthExaminationBatchParticipantRecord::id).toList())
            .stream()
            .collect(
                Collectors.groupingBy(
                    HealthExaminationParticipantServiceRecord::batchParticipantId));
    return rows.stream().map(r -> domain(r, services.getOrDefault(r.id(), List.of()))).toList();
  }

  public long countByBatch(AggregateId id, String search) {
    return mapper.countByBatch(id.value(), search);
  }

  public Set<IdentificationNumber> existingIdentificationNumbers(
      AggregateId id, Collection<IdentificationNumber> numbers) {
    if (numbers.isEmpty()) return Set.of();
    return mapper
        .existingIdentificationNumbers(
            id.value(), numbers.stream().map(IdentificationNumber::value).toList())
        .stream()
        .map(IdentificationNumber::of)
        .collect(Collectors.toSet());
  }

  public Map<UUID, Long> activeCountsByDay(AggregateId id) {
    return mapper.activeCountsByDay(id.value()).stream()
        .collect(Collectors.toMap(r -> r.batchDayId(), r -> r.activeCount()));
  }

  public void insertAll(Collection<HealthExaminationBatchParticipant> participants) {
    var rows =
        participants.stream()
            .map(MyBatisHealthExaminationBatchParticipantRepository::record)
            .toList();
    for (int start = 0; start < rows.size(); start += 400) {
      var chunk = rows.subList(start, Math.min(start + 400, rows.size()));
      if (mapper.insertAll(chunk) != chunk.size())
        throw new IllegalStateException("Roster rows were not inserted");
    }
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
                    new HealthExaminationParticipantServiceRecord(
                        s.id().value(),
                        s.batchId().value(),
                        s.batchParticipantId().value(),
                        s.batchServiceId().value(),
                        s.performed(),
                        value(s.serviceRequestId()),
                        s.unitPriceSnapshot().amount(),
                        s.recordedBy().value(),
                        s.recordedAt(),
                        s.createdAt(),
                        s.updatedAt(),
                        s.rowVersion()))
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
    return new HealthExaminationBatchParticipantRecord(
        p.id().value(),
        p.batchId().value(),
        p.batchDayId().value(),
        r.participantCode(),
        r.fullName(),
        r.dateOfBirth(),
        r.sex(),
        r.identificationNumber().value(),
        r.phone(),
        r.email(),
        r.departmentName(),
        r.positionName(),
        value(p.patientId()),
        p.rosterStatus().name(),
        p.attendanceStatus().name(),
        p.actualExaminationDate(),
        value(p.attendanceRecordedBy()),
        p.attendanceRecordedAt(),
        p.attendanceNote(),
        p.reconciliationStatus().name(),
        value(p.reconciledBy()),
        p.reconciledAt(),
        value(p.importJobId()),
        p.sourceRowNumber(),
        p.preparedAt(),
        p.createdAt(),
        p.updatedAt(),
        p.rowVersion());
  }

  private static AggregateId id(UUID id) {
    return id == null ? null : AggregateId.of(id);
  }

  private static UUID value(AggregateId id) {
    return id == null ? null : id.value();
  }
}
