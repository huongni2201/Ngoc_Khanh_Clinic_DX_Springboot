package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationSession;
import com.ngockhanh.clinic.healthexamination.domain.enums.ExaminationSiteType;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionActivity;
import com.ngockhanh.clinic.healthexamination.domain.enums.SessionStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationSessionRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationSessionMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationSessionRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.SessionSummaryView;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisHealthExaminationSessionRepository
    implements HealthExaminationSessionRepository {
  private final HealthExaminationSessionMyBatisMapper mapper;

  @Override
  public Optional<HealthExaminationSession> findById(
      UUID organizationId, UUID sessionId, boolean lock) {
    return Optional.ofNullable(mapper.findById(organizationId, sessionId, lock))
        .map(
            r ->
                HealthExaminationSession.restore(
                    new AggregateId(r.id()),
                    new AggregateId(r.organizationId()),
                    r.agreementId() == null ? null : new AggregateId(r.agreementId()),
                    r.sessionDate(),
                    SessionActivity.valueOf(r.activity()),
                    new ExaminationSite(ExaminationSiteType.valueOf(r.siteType()), r.siteAddress()),
                    SessionStatus.valueOf(r.status()),
                    r.rowVersion()));
  }

  @Override
  public void insert(HealthExaminationSession session, UUID createdBy) {
    if (mapper.insert(record(session, createdBy)) != 1)
      throw new IllegalStateException("Session was not inserted");
  }

  @Override
  public void update(HealthExaminationSession session, long expectedRowVersion) {
    if (mapper.updateDetails(record(session, null), expectedRowVersion) != 1)
      throw new ConcurrentUpdateException();
  }

  @Override
  public void updateStatus(
      HealthExaminationSession session, SessionStatus expectedStatus, long expectedRowVersion) {
    if (session == null || expectedStatus == null)
      throw new IllegalArgumentException("Session and expected status are required");
    if (mapper.updateStatus(
            session.id().value(),
            session.organizationId().value(),
            session.status().name(),
            expectedStatus.name(),
            expectedRowVersion)
        != 1) throw new ConcurrentUpdateException();
  }

  @Override
  public boolean openSlotTaken(
      UUID organizationId,
      LocalDate sessionDate,
      ExaminationSite site,
      SessionActivity activity,
      UUID excludeSessionId) {
    return mapper.openSlotTaken(
        organizationId,
        sessionDate,
        site.type().name(),
        site.address(),
        activity.name(),
        excludeSessionId);
  }

  @Override
  public Optional<SessionSummary> findSummary(UUID organizationId, UUID sessionId) {
    return Optional.ofNullable(mapper.findSummary(organizationId, sessionId))
        .map(MyBatisHealthExaminationSessionRepository::summary);
  }

  @Override
  public List<SessionSummary> findPage(
      SessionCriteria c, long offset, int limit, String sortKey, String sortBy) {
    return mapper
        .findPage(
            c.organizationId(),
            c.agreementId(),
            c.from(),
            c.to(),
            name(c.activity()),
            name(c.status()),
            c.pattern(),
            offset,
            limit,
            sortKey,
            sortBy)
        .stream()
        .map(MyBatisHealthExaminationSessionRepository::summary)
        .toList();
  }

  @Override
  public long count(SessionCriteria c) {
    return mapper.count(
        c.organizationId(),
        c.agreementId(),
        c.from(),
        c.to(),
        name(c.activity()),
        name(c.status()),
        c.pattern());
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }

  private static SessionSummary summary(SessionSummaryView v) {
    return new SessionSummary(
        v.id(),
        v.organizationId(),
        v.agreementId(),
        v.sessionDate(),
        SessionActivity.valueOf(v.activity()),
        ExaminationSiteType.valueOf(v.siteType()),
        v.siteAddress(),
        SessionStatus.valueOf(v.status()),
        v.assignedCount(),
        v.attendedCount(),
        v.noShowCount(),
        v.createdAt(),
        v.updatedAt(),
        v.rowVersion());
  }

  private static HealthExaminationSessionRecord record(HealthExaminationSession s, UUID actor) {
    return HealthExaminationSessionRecord.builder()
        .id(s.id().value())
        .organizationId(s.organizationId().value())
        .agreementId(s.agreementId() == null ? null : s.agreementId().value())
        .sessionDate(s.sessionDate())
        .activity(s.activity().name())
        .siteType(s.site().type().name())
        .siteAddress(s.site().address())
        .status(s.status().name())
        .createdBy(actor)
        .createdAt(null)
        .updatedAt(null)
        .rowVersion(s.rowVersion())
        .build();
  }
}
