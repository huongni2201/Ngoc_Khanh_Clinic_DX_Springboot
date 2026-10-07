package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ExaminationCountView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ExaminationDetailRowView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.PerformedServiceView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ServiceAmountView;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Read-only SQL of the examination detail matrix, its export and the payment summary. */
@Mapper
public interface ExaminationDetailMyBatisMapper {
  boolean batchInScope(
      @Param("organizationId") UUID organizationId, @Param("batchId") UUID batchId);

  long countRows(
      @Param("batchId") UUID batchId,
      @Param("searchPattern") String searchPattern,
      @Param("rosterStatus") String rosterStatus,
      @Param("attendanceStatus") String attendanceStatus,
      @Param("reconciliationStatus") String reconciliationStatus);

  List<ExaminationDetailRowView> findRows(
      @Param("batchId") UUID batchId,
      @Param("searchPattern") String searchPattern,
      @Param("rosterStatus") String rosterStatus,
      @Param("attendanceStatus") String attendanceStatus,
      @Param("reconciliationStatus") String reconciliationStatus,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy,
      @Param("offset") long offset,
      @Param("limit") int limit);

  /** Performed rows of the given Participants, in one query. */
  List<PerformedServiceView> findPerformedServices(
      @Param("participantIds") Collection<UUID> participantIds);

  List<ExaminationDetailRowView> findActiveRowsForExport(@Param("batchId") UUID batchId);

  /** Performed rows of every active Participant of the batch. */
  List<PerformedServiceView> findPerformedServicesOfActiveParticipants(
      @Param("batchId") UUID batchId);

  ExaminationCountView countActiveParticipants(@Param("batchId") UUID batchId);

  List<ServiceAmountView> aggregatePerformedServices(@Param("batchId") UUID batchId);
}
