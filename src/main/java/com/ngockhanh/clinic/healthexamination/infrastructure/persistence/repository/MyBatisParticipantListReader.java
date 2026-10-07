package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.application.port.ParticipantListReader;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantListCriteria;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantPage;
import com.ngockhanh.clinic.healthexamination.application.query.ParticipantSummary;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper;
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
  private final HealthExaminationBatchParticipantMyBatisMapper mapper;

  @Override
  public Optional<ParticipantPage> readPage(
      UUID organizationId, UUID batchId, ParticipantListCriteria criteria) {
    if (!mapper.batchInScope(organizationId, batchId)) return Optional.empty();
    long total =
        mapper.countSummaries(
            batchId,
            criteria.searchPattern(),
            criteria.identificationNumber(),
            criteria.batchDayId(),
            criteria.rosterStatus(),
            criteria.attendanceStatus(),
            criteria.reconciliationStatus());
    if (total == 0 || criteria.offset() >= total) return Optional.of(new ParticipantPage(List.of(), total));
    List<ParticipantSummary> items =
        mapper
            .findSummaries(
                batchId,
                criteria.searchPattern(),
                criteria.identificationNumber(),
                criteria.batchDayId(),
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
        .batchId(view.batchId())
        .batchDayId(view.batchDayId())
        .examinationDate(view.examinationDate())
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
        .actualExaminationDate(view.actualExaminationDate())
        .preparedAt(view.preparedAt())
        .rowVersion(view.rowVersion())
        .build();
  }
}
