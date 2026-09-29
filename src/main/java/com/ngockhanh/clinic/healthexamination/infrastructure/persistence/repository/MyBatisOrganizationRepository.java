package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.converter.OrganizationPersistenceConverter;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.OrganizationMyBatisMapper;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;

@Repository
@RequiredArgsConstructor
public class MyBatisOrganizationRepository implements OrganizationRepository {
    private final OrganizationMyBatisMapper mapper;
    private final OrganizationPersistenceConverter converter = new OrganizationPersistenceConverter();

    @Override
    public Optional<Organization> findById(AggregateId id) {
        return Optional.ofNullable(converter.toDomain(mapper.findById(id.value())));
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

    @Override
    public void update(Organization organization, long expectedRowVersion) {
        if (mapper.update(converter.toRecord(organization), expectedRowVersion) != 1) {
            throw new ConcurrentUpdateException();
        }
    }
}
