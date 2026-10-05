package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.OrganizationRecord;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrganizationMyBatisMapper {
  OrganizationRecord findById(@Param("id") UUID id);

  boolean existsByCode(@Param("code") String code, @Param("excludedId") UUID excludedId);

  int insert(OrganizationRecord organization);

  int update(
      @Param("organization") OrganizationRecord organization,
      @Param("expectedRowVersion") long expectedRowVersion);
}
