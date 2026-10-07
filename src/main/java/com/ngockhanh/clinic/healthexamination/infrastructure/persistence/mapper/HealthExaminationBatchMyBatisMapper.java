package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.*;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.HealthExaminationBatchSummaryView;
import java.math.BigDecimal;
import java.time.Instant;
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

  int updateHeader(
      @Param("batch") HealthExaminationBatchRecord batch,
      @Param("expectedRowVersion") long expectedRowVersion);

  int softDelete(
      @Param("id") UUID id,
      @Param("organizationId") UUID organizationId,
      @Param("expectedRowVersion") long expectedRowVersion,
      @Param("deletedAt") Instant deletedAt);

  int deleteDays(@Param("batchId") UUID batchId, @Param("ids") Collection<UUID> ids);

  int deleteServices(@Param("batchId") UUID batchId, @Param("ids") Collection<UUID> ids);

  int moveServiceOrder(
      @Param("batchId") UUID batchId,
      @Param("id") UUID id,
      @Param("displayOrder") int displayOrder);

  int updateService(
      @Param("batchId") UUID batchId,
      @Param("id") UUID id,
      @Param("negotiatedPrice") BigDecimal negotiatedPrice,
      @Param("displayOrder") int displayOrder,
      @Param("expectedRowVersion") long expectedRowVersion);

  List<UUID> findReferencedDayIds(
      @Param("batchId") UUID batchId, @Param("dayIds") Collection<UUID> dayIds);

  List<UUID> findReferencedBatchServiceIds(
      @Param("batchId") UUID batchId, @Param("serviceIds") Collection<UUID> serviceIds);

  boolean hasParticipants(@Param("batchId") UUID batchId);

  List<HealthExaminationBatchSummaryView> findPage(
      @Param("organizationId") UUID organizationId,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("pattern") String pattern,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long count(@Param("organizationId") UUID organizationId, @Param("pattern") String pattern);
}
