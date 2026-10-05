package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchSummary;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

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

  List<HealthExaminationBatchServiceRecord> findServices(@Param("id") UUID id);

  List<HealthExaminationBatchDayRecord> findDays(@Param("id") UUID id);

  int insert(HealthExaminationBatchRecord record);

  int insertDays(@Param("items") List<HealthExaminationBatchDayRecord> items);

  int insertServices(@Param("items") List<HealthExaminationBatchServiceRecord> items);

  List<BatchSummary> findPage(
      @Param("organizationId") UUID organizationId,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("pattern") String pattern,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long count(@Param("organizationId") UUID organizationId, @Param("pattern") String pattern);
}
