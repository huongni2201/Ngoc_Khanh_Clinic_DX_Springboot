package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantServiceRecord;

@Mapper
public interface HealthExaminationBatchParticipantMyBatisMapper {
    HealthExaminationBatchParticipantRecord findById(@Param("id") UUID id);
    HealthExaminationBatchParticipantRecord findByBatchAndParticipant(
            @Param("batchId") UUID batchId, @Param("participantId") UUID participantId);
    List<HealthExaminationBatchParticipantServiceRecord> findAssignments(@Param("batchParticipantId") UUID batchParticipantId);
    List<UUID> findParticipantIdsByBatch(@Param("batchId") UUID batchId,
                                         @Param("participantIds") Collection<UUID> participantIds);
    int insert(HealthExaminationBatchParticipantRecord participant);
}
