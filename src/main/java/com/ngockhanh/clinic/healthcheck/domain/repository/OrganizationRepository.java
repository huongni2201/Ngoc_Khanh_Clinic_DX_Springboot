package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.Organization;

public interface OrganizationRepository {
    Optional<Organization> findById(UUID id);
    Optional<Organization> findByCode(String code);
    Optional<Organization> findByTaxCode(String taxCode);
    void save(Organization organization);
}
