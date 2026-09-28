package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantRecord;

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
            @Param("organizationId") UUID organizationId,
            @Param("identificationNumbers") Collection<String> identificationNumbers);
    int insert(HealthExaminationParticipantRecord participant);
    int update(HealthExaminationParticipantRecord participant);
}
