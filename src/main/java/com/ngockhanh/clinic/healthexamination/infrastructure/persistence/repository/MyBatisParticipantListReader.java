package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantListReader;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantListCriteria;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantPage;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantSummary;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ParticipantSummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Reads Participant list pages with projections: no aggregate restore and no N+1 queries. */
@Repository
@RequiredArgsConstructor
public class MyBatisParticipantListReader implements ParticipantListReader {
  private final HealthExaminationParticipantMyBatisMapper mapper;

  @Override
  public Optional<ParticipantPage> readPage(
      UUID organizationId, UUID agreementId, ParticipantListCriteria criteria) {
    if (!mapper.agreementInScope(organizationId, agreementId)) return Optional.empty();
    long total =
        mapper.countSummaries(
            agreementId,
            criteria.searchPattern(),
            criteria.identificationNumber(),
            criteria.sessionId(),
            criteria.unscheduledFor(),
            criteria.rosterStatus(),
            criteria.attendanceStatus(),
            criteria.reconciliationStatus());
    if (total == 0 || criteria.offset() >= total)
      return Optional.of(new ParticipantPage(List.of(), total));
    List<ParticipantSummary> items =
        mapper
            .findSummaries(
                agreementId,
                criteria.searchPattern(),
                criteria.identificationNumber(),
                criteria.sessionId(),
                criteria.unscheduledFor(),
                criteria.rosterStatus(),
                criteria.attendanceStatus(),
                criteria.reconciliationStatus(),
                criteria.sortKey(),
                criteria.sortBy(),
                criteria.offset(),
                criteria.limit())
            .stream()
            .map(MyBatisParticipantListReader::summary)
            .toList();
    return Optional.of(new ParticipantPage(items, total));
  }

  private static ParticipantSummary summary(ParticipantSummaryView view) {
    return ParticipantSummary.builder()
        .id(view.id())
        .agreementId(view.agreementId())
        .participantCode(view.participantCode())
        .fullName(view.fullName())
        .dateOfBirth(view.dateOfBirth())
        .sex(view.sex())
        .identificationNumber(view.identificationNumber())
        .departmentName(view.departmentName())
        .positionName(view.positionName())
        .rosterStatus(view.rosterStatus())
        .attendanceStatus(view.attendanceStatus())
        .reconciliationStatus(view.reconciliationStatus())
        .liveAssignmentCount(view.liveAssignmentCount())
        .nextSessionDate(view.nextSessionDate())
        .lastAttendedDate(view.lastAttendedDate())
        .preparedAt(view.preparedAt())
        .rowVersion(view.rowVersion())
        .build();
  }
}
