package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.Company;
import java.util.Optional;

public interface CompanyRepository {
    Optional<Company> findById(UUID id);
    Optional<Company> findByCode(String code);
    Optional<Company> findByTaxCode(String taxCode);
    void save(Company company);
}
