package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationImportJobRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationImportRowRecord;

@Mapper
public interface HealthExaminationImportJobMyBatisMapper {
    HealthExaminationImportJobRecord findJobById(@Param("id") UUID id);
    HealthExaminationImportJobRecord findJobByIdForUpdate(@Param("id") UUID id);
    List<HealthExaminationImportRowRecord> findRowsByJobId(@Param("jobId") UUID jobId);
    int insertJob(HealthExaminationImportJobRecord job);
    int updateJob(HealthExaminationImportJobRecord job);
    int insertRow(HealthExaminationImportRowRecord row);
    int updateRow(HealthExaminationImportRowRecord row);
}
