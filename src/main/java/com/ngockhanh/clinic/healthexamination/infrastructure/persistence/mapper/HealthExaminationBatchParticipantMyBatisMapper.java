package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantServiceRecord;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HealthExaminationBatchParticipantMyBatisMapper {
  HealthExaminationBatchParticipantRecord findById(UUID id);

  List<HealthExaminationParticipantServiceRecord> findServices(@Param("ids") Collection<UUID> ids);

  List<HealthExaminationBatchParticipantRecord> findByBatch(
      @Param("batchId") UUID batchId,
      @Param("offset") long offset,
      @Param("limit") long limit,
      @Param("searchPattern") String searchPattern,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long countByBatch(@Param("batchId") UUID batchId, @Param("searchPattern") String searchPattern);

  List<String> existingIdentificationNumbers(
      @Param("batchId") UUID batchId, @Param("numbers") Collection<String> numbers);

  List<DayCount> activeCountsByDay(UUID batchId);

  int insertAll(@Param("participants") List<HealthExaminationBatchParticipantRecord> participants);

  int update(
      @Param("p") HealthExaminationBatchParticipantRecord participant,
      @Param("expectedVersion") long expectedVersion);

  int insertServices(@Param("services") List<HealthExaminationParticipantServiceRecord> services);

  int updateServices(@Param("services") List<HealthExaminationParticipantServiceRecord> services);

  record DayCount(UUID batchDayId, long activeCount) {}
}
