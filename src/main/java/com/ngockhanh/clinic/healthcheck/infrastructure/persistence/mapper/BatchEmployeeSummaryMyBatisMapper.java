package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.BatchEmployeeSummaryRecord;

@Mapper
public interface BatchEmployeeSummaryMyBatisMapper {
    List<BatchEmployeeSummaryRecord> findByBatch(@Param("batchId") UUID batchId,
                                                    @Param("offset") int offset, @Param("limit") int limit,
                                                    @Param("searchPattern") String searchPattern,
                                                    @Param("sortType") String sortType,
                                                    @Param("sortBy") String sortBy);
    long countByBatch(@Param("batchId") UUID batchId,
                      @Param("searchPattern") String searchPattern);
}
