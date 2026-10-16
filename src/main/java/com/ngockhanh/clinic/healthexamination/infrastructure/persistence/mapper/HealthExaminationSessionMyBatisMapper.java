package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationSessionRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.SessionSummaryView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HealthExaminationSessionMyBatisMapper {
  HealthExaminationSessionRecord findById(
      @Param("organizationId") UUID organizationId,
      @Param("id") UUID id,
      @Param("lock") boolean lock);

  int insert(HealthExaminationSessionRecord record);

  /** Changes the date, activity and site of an open session. */
  int updateDetails(
      @Param("s") HealthExaminationSessionRecord session,
      @Param("expectedRowVersion") long expectedRowVersion);

  int updateStatus(
      @Param("id") UUID id,
      @Param("organizationId") UUID organizationId,
      @Param("status") String status,
      @Param("expectedStatus") String expectedStatus,
      @Param("expectedRowVersion") long expectedRowVersion);

  boolean openSlotTaken(
      @Param("organizationId") UUID organizationId,
      @Param("sessionDate") LocalDate sessionDate,
      @Param("siteType") String siteType,
      @Param("siteAddress") String siteAddress,
      @Param("activity") String activity,
      @Param("excludeId") UUID excludeId);

  SessionSummaryView findSummary(
      @Param("organizationId") UUID organizationId, @Param("id") UUID id);

  List<SessionSummaryView> findPage(
      @Param("organizationId") UUID organizationId,
      @Param("agreementId") UUID agreementId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("activity") String activity,
      @Param("status") String status,
      @Param("pattern") String pattern,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long count(
      @Param("organizationId") UUID organizationId,
      @Param("agreementId") UUID agreementId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("activity") String activity,
      @Param("status") String status,
      @Param("pattern") String pattern);
}
