package com.ngockhanh.clinic.healthexamination.domain.repository;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.Optional;

public interface OrganizationRepository {
  Optional<Organization> findById(AggregateId id);

  /**
   * Checks whether an organization tax code is taken, optionally excluding an organization being
   * updated.
   */
  boolean existsByTaxCode(String taxCode, AggregateId excludedOrganizationId);

  void save(Organization organization);

  void update(Organization organization, long expectedRowVersion);

  /**
   * Returns organizations matching the validated filters with pagination metadata.
   *
   * @param page one-based page number
   * @param size maximum number of organizations per page
   * @param searchKey optional keyword to match
   * @param sortKey allowlisted sort field
   * @param sortBy validated sort direction
   * @param status organization status to match
   * @return the requested page, retaining totals even when the page is past the last page
   */
  PageResponse<Organization> search(
      int page,
      int size,
      String searchKey,
      String sortKey,
      String sortBy,
      OrganizationStatus status);
}
