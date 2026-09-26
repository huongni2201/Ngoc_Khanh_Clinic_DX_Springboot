package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthcheck.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.converter.OrganizationPersistenceConverter;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper.OrganizationMyBatisMapper;

@Repository
public final class MyBatisOrganizationRepository implements OrganizationRepository {
    private final OrganizationMyBatisMapper mapper;
    private final OrganizationPersistenceConverter converter = new OrganizationPersistenceConverter();

    public MyBatisOrganizationRepository(OrganizationMyBatisMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return Optional.ofNullable(converter.toDomain(mapper.findById(id)));
    }

    @Override
    public Optional<Organization> findByCode(String code) {
        return Optional.ofNullable(converter.toDomain(mapper.findByCode(code)));
    }

    @Override
    public Optional<Organization> findByTaxCode(String taxCode) {
        if (taxCode == null || taxCode.isBlank()) return Optional.empty();
        return Optional.ofNullable(converter.toDomain(mapper.findByTaxCode(taxCode)));
    }

    @Override
    public void save(Organization organization) {
        if (mapper.insert(converter.toRecord(organization)) != 1) {
            throw new IllegalStateException("Organization was not inserted");
        }
    }
}
