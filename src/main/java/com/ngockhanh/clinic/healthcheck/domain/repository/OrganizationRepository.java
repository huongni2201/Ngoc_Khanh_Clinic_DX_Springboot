package com.ngockhanh.clinic.healthcheck.domain.repository;

import java.util.Optional;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.OrganizationId;

public interface OrganizationRepository {
    Optional<Organization> findById(OrganizationId id);
    Optional<Organization> findByCode(String code);
    Optional<Organization> findByTaxCode(String taxCode);
    void save(Organization organization);
}
