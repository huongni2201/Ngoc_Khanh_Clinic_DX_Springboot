package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.*;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an active organization with a unique tax code when one is provided.
 *
 * <p>The insert and its audit event run in one transaction: if the audit write fails, the
 * organization is rolled back too. A concurrent create with the same non-null tax code loses at the
 * database unique constraint before any audit row is written.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrganizationUseCase {
  private final OrganizationRepository organizations;
  private final AuditWriter audit;

  /**
   * Creates an organization with status {@code ACTIVE} and row version 0.
   *
   * @param command organization details
   * @param actor authenticated account creating the organization
   * @return the created organization
   * @throws IllegalArgumentException when an argument is null or the details are invalid
   * @throws DuplicateOrganizationIdentity when the tax code is already used
   */
  @Transactional
  public OrganizationResponse execute(CreateOrganizationCommand command, UUID actor) {
    if (command == null || actor == null)
      throw new IllegalArgumentException("Organization command and creator are required");

    var organization =
        Organization.create(
            new AggregateId(UuidV7Generator.generate()),
            command.name(),
            command.taxCode(),
            command.phone(),
            command.email(),
            command.address(),
            command.contactFullName(),
            command.contactPhone(),
            command.contactEmail());
    if (organizations.existsByTaxCode(organization.taxCode(), null))
      throw new DuplicateOrganizationIdentity();
    organizations.save(organization);
    audit.record(
        actor,
        "CREATE_ORGANIZATION",
        "ORGANIZATION",
        organization.id().value(),
        null,
        Collections.singletonMap("taxCode", organization.taxCode()));
    log.info("Organization creation pending commit: organizationId={}", organization.id().value());
    return OrganizationResponse.from(organization);
  }
}
