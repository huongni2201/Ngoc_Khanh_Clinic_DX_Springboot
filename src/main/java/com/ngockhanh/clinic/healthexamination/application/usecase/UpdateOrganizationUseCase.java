package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.*;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateOrganizationUseCase {
  private final OrganizationRepository organizations;
  private final AuditWriter audit;

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
    var organization =
        current.updateDetails(
            command.code(),
            command.name(),
            command.organizationType(),
            command.taxCode(),
            command.phone(),
            command.email(),
            command.address(),
            command.contactFullName(),
            command.contactPosition(),
            command.contactPhone(),
            command.contactEmail());
    if (organizations.existsByCode(organization.code(), current.id()))
      throw new DuplicateOrganizationIdentity();
    organizations.update(organization, command.rowVersion());
    organization = organizations.findById(organization.id()).orElseThrow();
    audit.record(
        actor,
        "UPDATE_ORGANIZATION",
        "ORGANIZATION",
        organization.id().value(),
        Map.of("code", current.code(), "rowVersion", current.rowVersion()),
        Map.of("code", organization.code(), "rowVersion", organization.rowVersion()));
    log.info("Organization update persisted: organizationId={}", organization.id().value());
    return OrganizationResponse.from(organization);
  }
}
