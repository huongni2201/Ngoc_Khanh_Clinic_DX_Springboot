package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchParticipantRecord;

@Mapper
public interface BatchParticipantSummaryMyBatisMapper {
    List<HealthExaminationBatchParticipantRecord> findByBatch(
            @Param("batchId") UUID batchId,
            @Param("offset") long offset,
            @Param("limit") long limit,
            @Param("searchPattern") String searchPattern,
            @Param("sortKey") String sortKey,
            @Param("sortBy") String sortBy);

    long countByBatch(@Param("batchId") UUID batchId, @Param("searchPattern") String searchPattern);
}
