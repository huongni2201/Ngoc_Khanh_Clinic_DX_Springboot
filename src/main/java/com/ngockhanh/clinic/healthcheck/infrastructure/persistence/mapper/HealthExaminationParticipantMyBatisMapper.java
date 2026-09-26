package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationParticipantRecord;

@Mapper
public interface HealthExaminationParticipantMyBatisMapper {
    HealthExaminationParticipantRecord findById(@Param("id") UUID id);
    HealthExaminationParticipantRecord findByOrganizationAndCode(
            @Param("organizationId") UUID organizationId, @Param("participantCode") String participantCode);
    HealthExaminationParticipantRecord findByOrganizationAndIdentificationNumber(
            @Param("organizationId") UUID organizationId, @Param("identificationNumber") String identificationNumber);
    int insert(HealthExaminationParticipantRecord participant);
    int update(HealthExaminationParticipantRecord participant);
}
