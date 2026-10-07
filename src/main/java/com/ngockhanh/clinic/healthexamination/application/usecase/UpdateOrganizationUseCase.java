package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces all editable details of an organization using optimistic locking.
 *
 * <p>The update and its audit event run in one transaction. A stale expected row version, whether
 * detected on load or lost between read and write, is reported as a conflict before any audit row
 * is written. Status and identifier are never changed by this use case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateOrganizationUseCase {
  private final OrganizationRepository organizations;
  private final AuditWriter audit;

  /**
   * Updates an organization when the caller's expected row version is still current.
   *
   * @param id organization identifier
   * @param command full replacement details and the expected row version
   * @param actor authenticated account performing the update
   * @return the organization as stored, with the incremented row version
   * @throws IllegalArgumentException when an argument is null or the details are invalid
   * @throws ResourceNotFoundException when the organization does not exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   * @throws DuplicateOrganizationIdentity when the tax code belongs to another organization
   */
  @Transactional
  public OrganizationResponse execute(UUID id, UpdateOrganizationCommand command, UUID actor) {
    if (id == null || command == null || actor == null)
      throw new IllegalArgumentException("Organization ID, command, and updater are required");
    var current =
        organizations
            .findById(AggregateId.of(id))
            .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    if (command.rowVersion() == null || command.rowVersion() != current.rowVersion())
      throw new ConcurrentUpdateException();
    String previousTaxCode = current.taxCode();
    current.updateDetails(
        command.name(),
        command.taxCode(),
        command.phone(),
        command.email(),
        command.address(),
        command.contactFullName(),
        command.contactPhone(),
        command.contactEmail());
    if (organizations.existsByTaxCode(current.taxCode(), current.id()))
      throw new DuplicateOrganizationIdentity();
    organizations.update(current, command.rowVersion());
    var organization = organizations.findById(current.id()).orElseThrow();

    audit.record(
        actor,
        "UPDATE_ORGANIZATION",
        "ORGANIZATION",
        organization.id().value(),
        auditSnapshot(previousTaxCode, current.rowVersion()),
        auditSnapshot(organization.taxCode(), organization.rowVersion()));

    log.info("Organization update pending commit: organizationId={}", organization.id().value());
    return OrganizationResponse.from(organization);
  }

  private static Map<String, Object> auditSnapshot(String taxCode, long rowVersion) {
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("taxCode", taxCode);
    snapshot.put("rowVersion", rowVersion);
    return snapshot;
  }
}
