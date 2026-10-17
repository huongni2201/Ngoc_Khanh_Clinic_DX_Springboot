package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantServiceRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ParticipantScopeView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ParticipantSummaryView;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HealthExaminationParticipantMyBatisMapper {
  HealthExaminationParticipantRecord findById(UUID id);

  HealthExaminationParticipantRecord findInAgreement(
      @Param("agreementId") UUID agreementId, @Param("id") UUID id);

  List<HealthExaminationParticipantRecord> findManyInAgreementForUpdate(
      @Param("agreementId") UUID agreementId, @Param("ids") Collection<UUID> ids);

  List<HealthExaminationParticipantRecord> findManyInAgreement(
      @Param("agreementId") UUID agreementId, @Param("ids") Collection<UUID> ids);

  boolean identityTakenByOther(
      @Param("agreementId") UUID agreementId,
      @Param("identity") String identity,
      @Param("excludeId") UUID excludeId);

  List<HealthExaminationParticipantServiceRecord> findServices(@Param("ids") Collection<UUID> ids);

  int update(
      @Param("p") HealthExaminationParticipantRecord participant,
      @Param("expectedVersion") long expectedVersion);

  int insertServices(@Param("services") List<HealthExaminationParticipantServiceRecord> services);

  int updateServices(@Param("services") List<HealthExaminationParticipantServiceRecord> services);

  int insertMany(@Param("items") List<HealthExaminationParticipantRecord> items);

  List<String> findExistingIdentities(
      @Param("agreementId") UUID agreementId, @Param("identities") Collection<String> identities);

  long countInAgreement(@Param("agreementId") UUID agreementId);

  boolean agreementInScope(
      @Param("organizationId") UUID organizationId, @Param("agreementId") UUID agreementId);

  long countSummaries(
      @Param("agreementId") UUID agreementId,
      @Param("searchPattern") String searchPattern,
      @Param("identificationNumber") String identificationNumber,
      @Param("sessionId") UUID sessionId,
      @Param("unscheduledFor") String unscheduledFor,
      @Param("rosterStatus") String rosterStatus,
      @Param("attendanceStatus") String attendanceStatus,
      @Param("reconciliationStatus") String reconciliationStatus);

  List<ParticipantSummaryView> findSummaries(
      @Param("agreementId") UUID agreementId,
      @Param("searchPattern") String searchPattern,
      @Param("identificationNumber") String identificationNumber,
      @Param("sessionId") UUID sessionId,
      @Param("unscheduledFor") String unscheduledFor,
      @Param("rosterStatus") String rosterStatus,
      @Param("attendanceStatus") String attendanceStatus,
      @Param("reconciliationStatus") String reconciliationStatus,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy,
      @Param("offset") long offset,
      @Param("limit") int limit);

  /**
   * Active Participants of the agreement in a session, in print order. Participants who did not
   * show are never returned and attended ones only when {@code includeAttended} is true. {@code
   * order} is the name of an {@code ExaminationFormPrintOrder}.
   */
  List<UUID> findPrintableIdsOfSession(
      @Param("agreementId") UUID agreementId,
      @Param("sessionId") UUID sessionId,
      @Param("includeAttended") boolean includeAttended,
      @Param("order") String order);

  /** Scheduling facts of Participants of one organization. */
  List<ParticipantScopeView> findScopes(
      @Param("organizationId") UUID organizationId, @Param("ids") Collection<UUID> ids);
}
