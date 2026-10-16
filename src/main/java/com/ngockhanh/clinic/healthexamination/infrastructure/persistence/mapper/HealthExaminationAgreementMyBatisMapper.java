package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationAgreementRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationAgreementServiceRecord;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view.HealthExaminationAgreementSummaryView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HealthExaminationAgreementMyBatisMapper {
  HealthExaminationAgreementRecord findByIdAndOrganizationId(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  HealthExaminationAgreementRecord findByIdAndOrganizationIdForUpdate(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  HealthExaminationAgreementRecord findScoped(
      @Param("organizationId") UUID organizationId,
      @Param("id") UUID id,
      @Param("lock") boolean lock);

  List<HealthExaminationAgreementServiceRecord> findServices(@Param("id") UUID id);

  int insert(HealthExaminationAgreementRecord record);

  int insertServices(@Param("items") List<HealthExaminationAgreementServiceRecord> items);

  int updateHeader(
      @Param("agreement") HealthExaminationAgreementRecord agreement,
      @Param("expectedRowVersion") long expectedRowVersion);

  int softDelete(
      @Param("id") UUID id,
      @Param("organizationId") UUID organizationId,
      @Param("expectedRowVersion") long expectedRowVersion,
      @Param("deletedAt") Instant deletedAt);

  int updateStatus(
      @Param("id") UUID id,
      @Param("organizationId") UUID organizationId,
      @Param("status") String status,
      @Param("expectedStatus") String expectedStatus,
      @Param("expectedRowVersion") long expectedRowVersion);

  int deleteServices(@Param("agreementId") UUID agreementId, @Param("ids") Collection<UUID> ids);

  int moveServiceOrder(
      @Param("agreementId") UUID agreementId,
      @Param("id") UUID id,
      @Param("displayOrder") int displayOrder);

  int updateService(
      @Param("agreementId") UUID agreementId,
      @Param("id") UUID id,
      @Param("negotiatedPrice") BigDecimal negotiatedPrice,
      @Param("displayOrder") int displayOrder,
      @Param("expectedRowVersion") long expectedRowVersion);

  List<UUID> findReferencedAgreementServiceIds(
      @Param("agreementId") UUID agreementId, @Param("serviceIds") Collection<UUID> serviceIds);

  boolean hasParticipants(@Param("agreementId") UUID agreementId);

  boolean hasSessions(@Param("agreementId") UUID agreementId);

  int lockCodeSequence(@Param("key") long key);

  long highestCodeSequence(@Param("prefix") String prefix);

  List<HealthExaminationAgreementSummaryView> findPage(
      @Param("organizationId") UUID organizationId,
      @Param("offset") long offset,
      @Param("limit") int limit,
      @Param("pattern") String pattern,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long count(@Param("organizationId") UUID organizationId, @Param("pattern") String pattern);
}
