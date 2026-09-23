package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.CompanyRecord;

@Mapper
public interface CompanyMyBatisMapper {
    CompanyRecord findById(@Param("id") UUID id);
    CompanyRecord findByCode(@Param("code") String code);
    CompanyRecord findByTaxCode(@Param("taxCode") String taxCode);
    int insert(CompanyRecord company);
}