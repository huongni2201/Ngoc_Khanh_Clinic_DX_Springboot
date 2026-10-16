package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.application.port.out.PrintableParticipantReader;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationFormPrintOrder;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationSessionMyBatisMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Chooses the Participants of a session with projections only. */
@Repository
@RequiredArgsConstructor
public class MyBatisPrintableParticipantReader implements PrintableParticipantReader {
  private final HealthExaminationParticipantMyBatisMapper participants;
  private final HealthExaminationSessionMyBatisMapper sessions;

  @Override
  public Optional<PrintableSession> readSession(
      UUID organizationId,
      UUID agreementId,
      UUID sessionId,
      boolean includeAttended,
      ExaminationFormPrintOrder order) {
    if (!participants.agreementInScope(organizationId, agreementId)) return Optional.empty();
    var session = sessions.findById(organizationId, sessionId, false);
    if (session == null
        || !"OPEN".equals(session.status())
        || (session.agreementId() != null && !session.agreementId().equals(agreementId)))
      return Optional.empty();
    List<UUID> ids =
        participants.findPrintableIdsOfSession(
            agreementId, sessionId, includeAttended, order.name());
    return Optional.of(new PrintableSession(session.sessionDate(), ids));
  }
}
