package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository {
  Optional<Organization> findById(AggregateId id);

  /** Checks whether a tax code is taken, optionally excluding an organization being updated. */
  boolean existsByTaxCode(String taxCode, AggregateId excludedOrganizationId);

  List<Organization> findPage(
      long offset, long limit, String searchPattern, String status, String sortKey, String sortBy);

  long countAll(String searchPattern, String status);

  void save(Organization organization);

  void update(Organization organization, long expectedRowVersion);
}
