package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.SessionAssignment;
import com.ngockhanh.clinic.healthexamination.domain.enums.AssignmentStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionActivity;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.SessionAssignmentMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationSessionAssignmentRecord;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.time.Instant;
import java.time.LocalDate;
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
public class MyBatisSessionAssignmentRepository implements SessionAssignmentRepository {
  private final SessionAssignmentMyBatisMapper mapper;

  @Override
  public Optional<SessionAssignment> findById(
      UUID organizationId, UUID sessionId, UUID assignmentId, boolean lock) {
    return Optional.ofNullable(mapper.findById(organizationId, sessionId, assignmentId, lock))
        .map(MyBatisSessionAssignmentRepository::domain);
  }

  @Override
  public Set<AggregateId> findLiveParticipantIds(
      UUID sessionId, Collection<AggregateId> participantIds) {
    if (participantIds.isEmpty()) return Set.of();
    return mapper
        .findLiveParticipantIds(sessionId, participantIds.stream().map(AggregateId::value).toList())
        .stream()
        .map(AggregateId::of)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public void insertMany(List<SessionAssignment> assignments) {
    if (assignments.isEmpty()) return;
    var rows = assignments.stream().map(MyBatisSessionAssignmentRepository::record).toList();
    if (mapper.insertMany(rows) != rows.size())
      throw new IllegalStateException("Session assignments were not inserted");
  }

  @Override
  public void update(SessionAssignment assignment, long expectedRowVersion) {
    if (mapper.update(record(assignment), expectedRowVersion) != 1)
      throw new ConcurrentUpdateException();
  }

  @Override
  public List<SessionAssignment> findByStatusForUpdate(
      UUID organizationId, UUID sessionId, AssignmentStatus status) {
    return mapper.findByStatusForUpdate(organizationId, sessionId, status.name()).stream()
        .map(MyBatisSessionAssignmentRepository::domain)
        .toList();
  }

  @Override
  public long countLive(UUID sessionId) {
    return mapper.countLive(sessionId);
  }

  @Override
  public boolean hasAttended(AggregateId participantId) {
    return mapper.hasAttended(participantId.value());
  }

  @Override
  public int cancelExpectedOfParticipant(AggregateId participantId, AggregateId actor, Instant at) {
    return mapper.cancelExpectedOfParticipant(participantId.value(), actor.value(), at);
  }

  @Override
  public Map<AggregateId, LocalDate> findExaminationDates(
      UUID organizationId, Collection<AggregateId> participantIds) {
    if (participantIds.isEmpty()) return Map.of();
    return mapper
        .findExaminationDates(
            organizationId, participantIds.stream().map(AggregateId::value).toList())
        .stream()
        .collect(
            Collectors.toUnmodifiableMap(
                v -> AggregateId.of(v.participantId()), v -> v.examinationDate()));
  }

  @Override
  public List<AssignmentRow> findPage(
      UUID organizationId,
      UUID sessionId,
      AssignmentStatus status,
      String pattern,
      long offset,
      int limit,
      String sortKey,
      String sortBy) {
    return mapper
        .findPage(
            organizationId,
            sessionId,
            status == null ? null : status.name(),
            pattern,
            offset,
            limit,
            sortKey,
            sortBy)
        .stream()
        .map(
            v ->
                new AssignmentRow(
                    v.assignmentId(),
                    AssignmentStatus.valueOf(v.status()),
                    v.attendedAt(),
                    v.note(),
                    v.assignmentRowVersion(),
                    v.participantId(),
                    v.agreementId(),
                    v.participantCode(),
                    v.fullName(),
                    v.dateOfBirth(),
                    v.sex(),
                    v.identificationNumber(),
                    v.departmentName(),
                    v.positionName(),
                    RosterStatus.valueOf(v.rosterStatus())))
        .toList();
  }

  @Override
  public long count(UUID organizationId, UUID sessionId, AssignmentStatus status, String pattern) {
    return mapper.count(organizationId, sessionId, status == null ? null : status.name(), pattern);
  }

  @Override
  public List<ParticipantAssignment> findByParticipant(UUID organizationId, UUID participantId) {
    return mapper.findByParticipant(organizationId, participantId).stream()
        .map(
            v ->
                new ParticipantAssignment(
                    v.assignmentId(),
                    v.sessionId(),
                    v.sessionDate(),
                    SessionActivity.valueOf(v.activity()),
                    ExaminationSiteType.valueOf(v.siteType()),
                    v.siteAddress(),
                    SessionStatus.valueOf(v.sessionStatus()),
                    AssignmentStatus.valueOf(v.status()),
                    v.replacedByAssignmentId(),
                    v.attendedAt(),
                    v.note(),
                    v.rowVersion()))
        .toList();
  }

  private static SessionAssignment domain(HealthExaminationSessionAssignmentRecord r) {
    return SessionAssignment.restore(
        AggregateId.of(r.id()),
        AggregateId.of(r.organizationId()),
        AggregateId.of(r.participantId()),
        AggregateId.of(r.sessionId()),
        AssignmentStatus.valueOf(r.status()),
        r.replacedByAssignmentId() == null ? null : AggregateId.of(r.replacedByAssignmentId()),
        r.attendedAt(),
        r.note(),
        AggregateId.of(r.recordedBy()),
        r.recordedAt(),
        r.createdAt(),
        r.updatedAt(),
        r.rowVersion());
  }

  private static HealthExaminationSessionAssignmentRecord record(SessionAssignment a) {
    return HealthExaminationSessionAssignmentRecord.builder()
        .id(a.id().value())
        .organizationId(a.organizationId().value())
        .participantId(a.participantId().value())
        .sessionId(a.sessionId().value())
        .status(a.status().name())
        .replacedByAssignmentId(
            a.replacedByAssignmentId() == null ? null : a.replacedByAssignmentId().value())
        .attendedAt(a.attendedAt())
        .note(a.note())
        .recordedBy(a.recordedBy().value())
        .recordedAt(a.recordedAt())
        .createdAt(a.createdAt())
        .updatedAt(a.updatedAt())
        .rowVersion(a.rowVersion())
        .build();
  }
}
