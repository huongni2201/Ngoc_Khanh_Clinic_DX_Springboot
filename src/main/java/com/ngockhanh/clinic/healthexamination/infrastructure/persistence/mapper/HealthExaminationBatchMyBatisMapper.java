package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchRecord;

@Mapper
public interface HealthExaminationBatchMyBatisMapper {
    HealthExaminationBatchRecord findById(@Param("id") UUID id);
}
