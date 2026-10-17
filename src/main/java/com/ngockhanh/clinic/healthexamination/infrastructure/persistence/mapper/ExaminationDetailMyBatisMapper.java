package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ExaminationCountView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ExaminationDetailRowView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.PaymentParticipantCountView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.PaymentSessionCountView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.PerformedServiceView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ServiceAmountView;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Read-only SQL of the examination detail matrix, its export and the payment summary. */
@Mapper
public interface ExaminationDetailMyBatisMapper {
  boolean agreementInScope(
      @Param("organizationId") UUID organizationId, @Param("agreementId") UUID agreementId);

  long countRows(
      @Param("agreementId") UUID agreementId,
      @Param("searchPattern") String searchPattern,
      @Param("rosterStatus") String rosterStatus,
      @Param("attendanceStatus") String attendanceStatus,
      @Param("reconciliationStatus") String reconciliationStatus);

  List<ExaminationDetailRowView> findRows(
      @Param("agreementId") UUID agreementId,
      @Param("searchPattern") String searchPattern,
      @Param("rosterStatus") String rosterStatus,
      @Param("attendanceStatus") String attendanceStatus,
      @Param("reconciliationStatus") String reconciliationStatus,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy,
      @Param("offset") long offset,
      @Param("limit") int limit);

  /** One Participant row of the agreement, or null when the Participant is not in the agreement. */
  ExaminationDetailRowView findRow(
      @Param("agreementId") UUID agreementId, @Param("participantId") UUID participantId);

  /** Performed rows of the given Participants, in one query. */
  List<PerformedServiceView> findPerformedServices(
      @Param("participantIds") Collection<UUID> participantIds);

  List<ExaminationDetailRowView> findActiveRowsForExport(@Param("agreementId") UUID agreementId);

  /** Performed rows of every active Participant of the agreement. */
  List<PerformedServiceView> findPerformedServicesOfActiveParticipants(
      @Param("agreementId") UUID agreementId);

  ExaminationCountView countActiveParticipants(@Param("agreementId") UUID agreementId);

  PaymentParticipantCountView countPaymentParticipants(
      @Param("agreementId") UUID agreementId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  PaymentSessionCountView countPaymentSessions(
      @Param("agreementId") UUID agreementId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);

  List<ServiceAmountView> aggregatePerformedServices(
      @Param("agreementId") UUID agreementId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}
