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

  int update(
      @Param("p") HealthExaminationBatchParticipantRecord participant,
      @Param("expectedVersion") long expectedVersion);

  int insertServices(@Param("services") List<HealthExaminationParticipantServiceRecord> services);

  int updateServices(@Param("services") List<HealthExaminationParticipantServiceRecord> services);
}
