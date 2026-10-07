package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deactivates an organization ({@code ACTIVE} to {@code INACTIVE}) instead of removing its row.
 *
 * <p>The organization row, its health-examination batches and their history are preserved. The
 * status change and its audit event are written in one transaction, so an audit failure rolls back
 * the deactivation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteOrganizationUseCase {
  private final OrganizationRepository organizations;
  private final AuditWriter audit;

  /**
   * Deactivates an organization when the caller's expected row version is still current.
   *
   * <p>Repeating the request against an already inactive organization with the current version is a
   * no-op: nothing is written, the version is not bumped and no audit event is recorded.
   *
   * @param id organization identifier
   * @param command carries the row version the caller last read
   * @param actor authenticated account performing the change
   * @throws IllegalArgumentException when an argument is null or the row version is negative
   * @throws ResourceNotFoundException when the organization does not exist
   * @throws ConcurrentUpdateException when the expected row version is stale
   */
  @Transactional
  public void execute(UUID id, DeleteOrganizationCommand command, UUID actor) {
    if (id == null || command == null || actor == null)
      throw new IllegalArgumentException("Organization ID, command, and actor are required");
    Long expectedVersion = command.rowVersion();
    if (expectedVersion == null || expectedVersion < 0)
      throw new IllegalArgumentException("Expected row version is required");

    var current =
        organizations
            .findById(AggregateId.of(id))
            .orElseThrow(() -> new ResourceNotFoundException("Organization"));
    if (expectedVersion != current.rowVersion()) throw new ConcurrentUpdateException();
    if (current.status() == OrganizationStatus.INACTIVE) {
      log.debug("Organization already inactive: organizationId={}", id);
      return;
    }

    String previousStatus = current.status().name();
    current.deactivate();
    organizations.update(current, expectedVersion);
    var deactivated = organizations.findById(current.id()).orElseThrow();
    audit.record(
        actor,
        "DEACTIVATE_ORGANIZATION",
        "ORGANIZATION",
        deactivated.id().value(),
        Map.of("status", previousStatus, "rowVersion", current.rowVersion()),
        Map.of("status", deactivated.status().name(), "rowVersion", deactivated.rowVersion()));
    log.info(
        "Organization deactivation pending commit: organizationId={}", deactivated.id().value());
  }
}
