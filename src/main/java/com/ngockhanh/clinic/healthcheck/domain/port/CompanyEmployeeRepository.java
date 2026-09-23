package com.ngockhanh.clinic.healthcheck.domain.port;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.CompanyEmployee;
import java.util.Optional;

public interface CompanyEmployeeRepository {
    Optional<CompanyEmployee> findById(UUID id);
    Optional<CompanyEmployee> findByCompanyAndCode(UUID companyId, String employeeCode);
    Optional<CompanyEmployee> findByCompanyAndIdentificationNumber(UUID companyId, IdentificationNumber identificationNumber);
    void save(CompanyEmployee employee);
}
