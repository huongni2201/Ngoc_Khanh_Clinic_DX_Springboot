package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.valueobject.Cccd;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.CompanyEmployee;
import java.util.Optional;

public interface CompanyEmployeeRepository {
    Optional<CompanyEmployee> findById(UUID id);
    Optional<CompanyEmployee> findByCompanyAndCode(UUID companyId, String employeeCode);
    Optional<CompanyEmployee> findByCompanyAndCccd(UUID companyId, Cccd cccd);
    void save(CompanyEmployee employee);
}
