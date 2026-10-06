package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads one active organization by identifier.
 *
 * <p>Inactive (deactivated) organizations are treated as not found. The read never changes data and
 * records no audit event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetOrganizationByIdUseCase {
  private final OrganizationRepository organizations;

  /**
   * Returns the active organization with the given identifier.
   *
   * @param id organization identifier
   * @return the organization
   * @throws IllegalArgumentException when {@code id} is null
   * @throws ResourceNotFoundException when no active organization has this identifier
   */
  @Transactional(readOnly = true)
  public OrganizationResponse execute(UUID id) {
    if (id == null) throw new IllegalArgumentException("Organization ID is required");

    Organization organization =
        organizations
            .findById(AggregateId.of(id))
            .filter(found -> "ACTIVE".equals(found.status()))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));

    log.debug("Organization retrieved: organizationId={}", id);
    return OrganizationResponse.from(organization);
  }
}
