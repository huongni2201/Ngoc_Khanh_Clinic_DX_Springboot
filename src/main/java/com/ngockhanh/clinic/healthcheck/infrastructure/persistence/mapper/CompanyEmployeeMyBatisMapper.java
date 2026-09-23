package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.CompanyEmployeeRecord;

@Mapper
public interface CompanyEmployeeMyBatisMapper {
    CompanyEmployeeRecord findById(@Param("id") UUID id);
    CompanyEmployeeRecord findByCompanyAndCode(@Param("companyId") UUID companyId, @Param("employeeCode") String employeeCode);
    CompanyEmployeeRecord findByCompanyAndIdentificationNumber(@Param("companyId") UUID companyId,
                                                                @Param("identificationNumber") String identificationNumber);
    int insert(CompanyEmployeeRecord employee);
    int update(CompanyEmployeeRecord employee);
}