package com.ngockhanh.clinic.healthcheck.domain.port;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.Company;
import java.util.Optional;

public interface CompanyRepository {
    Optional<Company> findById(UUID id);
    Optional<Company> findByCode(String code);
    void save(Company company);
}
