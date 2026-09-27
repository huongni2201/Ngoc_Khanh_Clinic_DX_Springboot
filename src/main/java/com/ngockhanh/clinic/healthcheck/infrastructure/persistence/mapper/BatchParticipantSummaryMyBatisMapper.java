package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.BatchParticipantSummaryRecord;

@Mapper
public interface BatchParticipantSummaryMyBatisMapper {
    List<BatchParticipantSummaryRecord> findByBatch(@Param("batchId") UUID batchId,
                                                    @Param("offset") int offset, @Param("limit") int limit);
    long countByBatch(@Param("batchId") UUID batchId);
}
