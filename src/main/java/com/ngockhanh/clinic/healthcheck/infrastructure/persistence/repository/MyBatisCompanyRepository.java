package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.Company;
import com.ngockhanh.clinic.healthcheck.domain.repository.CompanyRepository;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.converter.CompanyPersistenceConverter;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper.CompanyMyBatisMapper;

@Repository
public final class MyBatisCompanyRepository implements CompanyRepository {
    private final CompanyMyBatisMapper mapper;
    private final CompanyPersistenceConverter converter = new CompanyPersistenceConverter();

    public MyBatisCompanyRepository(CompanyMyBatisMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return Optional.ofNullable(converter.toDomain(mapper.findById(id)));
    }

    @Override
    public Optional<Company> findByCode(String code) {
        return Optional.ofNullable(converter.toDomain(mapper.findByCode(code)));
    }

    @Override
    public Optional<Company> findByTaxCode(String taxCode) {
        if (taxCode == null || taxCode.isBlank()) return Optional.empty();
        return Optional.ofNullable(converter.toDomain(mapper.findByTaxCode(taxCode)));
    }

    @Override
    public void save(Company company) {
        if (mapper.insert(converter.toRecord(company)) != 1) {
            throw new IllegalStateException("Company was not inserted");
        }
    }
}