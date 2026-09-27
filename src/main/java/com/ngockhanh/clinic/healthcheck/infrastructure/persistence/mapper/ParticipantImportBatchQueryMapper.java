package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportBatchContext;

@Mapper
public interface ParticipantImportBatchQueryMapper {
    ParticipantImportBatchContext findById(@Param("id") UUID id);
}
