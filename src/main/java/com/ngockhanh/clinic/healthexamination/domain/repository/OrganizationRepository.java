package com.ngockhanh.clinic.healthexamination.domain.repository;

import java.util.Optional;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

public interface OrganizationRepository {
    Optional<Organization> findById(AggregateId id);

    Optional<Organization> findByTaxCode(String taxCode);

    void save(Organization organization);

    void update(Organization organization, long expectedRowVersion);
}
