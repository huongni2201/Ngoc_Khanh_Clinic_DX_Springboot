package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper;

import java.util.UUID;
import java.util.Collection;
import java.util.List;

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
    List<HealthExaminationParticipantRecord> findByOrganizationAndCodes(
            @Param("organizationId") UUID organizationId, @Param("codes") Collection<String> codes);
    List<HealthExaminationParticipantRecord> findByOrganizationAndIdentificationNumbers(
            @Param("organizationId") UUID organizationId, @Param("identificationNumbers") Collection<String> identificationNumbers);
    int insert(HealthExaminationParticipantRecord participant);
    int update(HealthExaminationParticipantRecord participant);
}
