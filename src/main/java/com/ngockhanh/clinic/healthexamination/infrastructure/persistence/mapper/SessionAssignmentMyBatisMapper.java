package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationSessionAssignmentRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.AssignmentRowView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ExaminationDateView;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.ParticipantAssignmentView;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SessionAssignmentMyBatisMapper {
  HealthExaminationSessionAssignmentRecord findById(
      @Param("organizationId") UUID organizationId,
      @Param("sessionId") UUID sessionId,
      @Param("id") UUID id,
      @Param("lock") boolean lock);

  List<UUID> findLiveParticipantIds(
      @Param("sessionId") UUID sessionId, @Param("participantIds") Collection<UUID> participantIds);

  int insertMany(@Param("items") List<HealthExaminationSessionAssignmentRecord> items);

  int update(
      @Param("a") HealthExaminationSessionAssignmentRecord assignment,
      @Param("expectedRowVersion") long expectedRowVersion);

  List<HealthExaminationSessionAssignmentRecord> findByStatusForUpdate(
      @Param("organizationId") UUID organizationId,
      @Param("sessionId") UUID sessionId,
      @Param("status") String status);

  long countLive(@Param("sessionId") UUID sessionId);

  boolean hasAttended(@Param("participantId") UUID participantId);

  int cancelExpectedOfParticipant(
      @Param("participantId") UUID participantId,
      @Param("actor") UUID actor,
      @Param("at") Instant at);

  List<AssignmentRowView> findPage(
      @Param("organizationId") UUID organizationId,
      @Param("sessionId") UUID sessionId,
      @Param("status") String status,
      @Param("pattern") String pattern,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long count(
      @Param("organizationId") UUID organizationId,
      @Param("sessionId") UUID sessionId,
      @Param("status") String status,
      @Param("pattern") String pattern);

  List<ExaminationDateView> findExaminationDates(
      @Param("organizationId") UUID organizationId,
      @Param("participantIds") Collection<UUID> participantIds);

  List<ParticipantAssignmentView> findByParticipant(
      @Param("organizationId") UUID organizationId, @Param("participantId") UUID participantId);
}
