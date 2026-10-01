package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationImportJobRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationImportRowRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.ImportAuditLogRecord;

@Mapper
public interface HealthExaminationImportJobMyBatisMapper {
    HealthExaminationImportJobRecord findJobByIdAndBatchId(
            @Param("id") UUID id, @Param("batchId") UUID batchId);
    HealthExaminationImportJobRecord findJobSummaryByIdAndBatchId(
            @Param("id") UUID id, @Param("batchId") UUID batchId);
    HealthExaminationImportJobRecord findJobByIdAndBatchIdForUpdate(
            @Param("id") UUID id, @Param("batchId") UUID batchId);
    List<HealthExaminationImportRowRecord> findRowsByJobId(@Param("jobId") UUID jobId);
    long countRowsByJobId(@Param("jobId") UUID jobId, @Param("rowFilter") String rowFilter);
    List<HealthExaminationImportRowRecord> findRowsPage(@Param("jobId") UUID jobId,
                                                        @Param("rowFilter") String rowFilter,
                                                        @Param("offset") long offset,
                                                        @Param("limit") int limit);
    int insertJob(HealthExaminationImportJobRecord job);
    int updateJob(HealthExaminationImportJobRecord job);
    int insertRow(HealthExaminationImportRowRecord row);
    int updateRow(HealthExaminationImportRowRecord row);
    int upsertRows(@Param("items") List<HealthExaminationImportRowRecord> rows);
    int insertImportAudit(ImportAuditLogRecord audit);
}
