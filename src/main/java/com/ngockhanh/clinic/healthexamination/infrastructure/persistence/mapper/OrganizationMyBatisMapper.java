package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.OrganizationRecord;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrganizationMyBatisMapper {
  OrganizationRecord findById(@Param("id") UUID id);

  OrganizationRecord findByTaxCode(@Param("taxCode") String taxCode);

  List<OrganizationRecord> findPage(
      @Param("offset") long offset,
      @Param("limit") long limit,
      @Param("searchPattern") String searchPattern,
      @Param("status") String status,
      @Param("sortKey") String sortKey,
      @Param("sortBy") String sortBy);

  long countAll(@Param("searchPattern") String searchPattern, @Param("status") String status);

  int insert(OrganizationRecord organization);

  int update(
      @Param("organization") OrganizationRecord organization,
      @Param("expectedRowVersion") long expectedRowVersion);
}
