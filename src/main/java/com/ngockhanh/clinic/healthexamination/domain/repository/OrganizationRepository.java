package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.util.Optional;

public interface OrganizationRepository {
  Optional<Organization> findById(AggregateId id);

  /**
   * Checks whether an organization code is taken, optionally excluding an organization being
   * updated.
   */
  boolean existsByCode(String code, AggregateId excludedOrganizationId);

  void save(Organization organization);

  void update(Organization organization, long expectedRowVersion);
}
