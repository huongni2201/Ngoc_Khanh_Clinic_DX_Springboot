package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchSummary;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationBatchServiceRecord;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HealthExaminationBatchMyBatisMapper {
  HealthExaminationBatchRecord findByIdAndOrganizationId(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  HealthExaminationBatchRecord findByIdAndOrganizationIdForUpdate(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  HealthExaminationBatchRecord findScoped(
      @Param("organizationId") UUID organizationId,
      @Param("id") UUID id,
      @Param("lock") boolean lock);

  List<
          com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record
              .HealthExaminationBatchServiceRecord>
      findServices(@Param("id") UUID id);

  int insert(HealthExaminationBatchRecord record);

  int update(HealthExaminationBatchRecord record);

  int upsertServices(@Param("items") List<HealthExaminationBatchServiceRecord> items);

  int deleteRemoved(@Param("id") UUID id, @Param("retained") List<UUID> retained);

  boolean hasReferencedRemoved(@Param("id") UUID id, @Param("retained") List<UUID> retained);

  boolean hasDependents(@Param("id") UUID id);

  List<BatchSummary> findPage(
      @Param("organizationId") UUID organizationId,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("pattern") String pattern,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long count(@Param("organizationId") UUID organizationId, @Param("pattern") String pattern);
}
