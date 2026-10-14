package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.application.port.out.PrintableParticipantReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationFormPrintOrder;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Chooses the Participants of an examination day with projections only. */
@Repository
@RequiredArgsConstructor
public class MyBatisPrintableParticipantReader implements PrintableParticipantReader {
  private final HealthExaminationBatchParticipantMyBatisMapper mapper;

  @Override
  public Optional<PrintableDay> readDay(
      UUID organizationId,
      UUID batchId,
      UUID batchDayId,
      boolean includeAttended,
      ExaminationFormPrintOrder order) {
    if (!mapper.batchInScope(organizationId, batchId)) return Optional.empty();
    LocalDate date = mapper.findBatchDayDate(batchId, batchDayId);
    if (date == null) return Optional.empty();
    List<UUID> ids = mapper.findPrintableIdsOfDay(batchId, batchDayId, includeAttended, order.name());
    return Optional.of(new PrintableDay(date, ids));
  }
}
