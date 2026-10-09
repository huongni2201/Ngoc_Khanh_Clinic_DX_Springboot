package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantServiceRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ParticipantSummaryView;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HealthExaminationBatchParticipantMyBatisMapper {
  HealthExaminationBatchParticipantRecord findById(UUID id);

  HealthExaminationBatchParticipantRecord findInBatch(
      @Param("batchId") UUID batchId, @Param("id") UUID id);

  List<HealthExaminationBatchParticipantRecord> findManyInBatchForUpdate(
      @Param("batchId") UUID batchId, @Param("ids") Collection<UUID> ids);

  boolean identityTakenByOther(
      @Param("batchId") UUID batchId,
      @Param("identity") String identity,
      @Param("excludeId") UUID excludeId);

  List<HealthExaminationParticipantServiceRecord> findServices(@Param("ids") Collection<UUID> ids);

  int update(
      @Param("p") HealthExaminationBatchParticipantRecord participant,
      @Param("expectedVersion") long expectedVersion);

  int insertServices(@Param("services") List<HealthExaminationParticipantServiceRecord> services);

  int updateServices(@Param("services") List<HealthExaminationParticipantServiceRecord> services);

  int insertMany(@Param("items") List<HealthExaminationBatchParticipantRecord> items);

  List<String> findExistingIdentities(
      @Param("batchId") UUID batchId, @Param("identities") Collection<String> identities);

  long countInBatch(@Param("batchId") UUID batchId);

  boolean batchInScope(
      @Param("organizationId") UUID organizationId, @Param("batchId") UUID batchId);

  long countSummaries(
      @Param("batchId") UUID batchId,
      @Param("searchPattern") String searchPattern,
      @Param("identificationNumber") String identificationNumber,
      @Param("batchDayId") UUID batchDayId,
      @Param("rosterStatus") String rosterStatus,
      @Param("attendanceStatus") String attendanceStatus,
      @Param("reconciliationStatus") String reconciliationStatus);

  List<ParticipantSummaryView> findSummaries(
      @Param("batchId") UUID batchId,
      @Param("searchPattern") String searchPattern,
      @Param("identificationNumber") String identificationNumber,
      @Param("batchDayId") UUID batchDayId,
      @Param("rosterStatus") String rosterStatus,
      @Param("attendanceStatus") String attendanceStatus,
      @Param("reconciliationStatus") String reconciliationStatus,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy,
      @Param("offset") long offset,
      @Param("limit") int limit);
}
