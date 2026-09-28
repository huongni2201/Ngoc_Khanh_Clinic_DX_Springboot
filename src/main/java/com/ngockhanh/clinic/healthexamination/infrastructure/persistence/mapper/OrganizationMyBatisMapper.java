package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.OrganizationRecord;

@Mapper
public interface OrganizationMyBatisMapper {
    OrganizationRecord findById(@Param("id") UUID id);
    OrganizationRecord findByCode(@Param("code") String code);
    OrganizationRecord findByTaxCode(@Param("taxCode") String taxCode);
    int insert(OrganizationRecord organization);
    int update(@Param("organization") OrganizationRecord organization,
               @Param("expectedRowVersion") long expectedRowVersion);
}
