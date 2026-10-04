package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.*;
import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrganizationUseCase {
  private final OrganizationRepository organizations;

  @Transactional
  public OrganizationResponse execute(CreateOrganizationCommand command) {
    if (command == null) throw new IllegalArgumentException("Missing organization command");

    var organization =
        Organization.create(
            new AggregateId(UuidV7Generator.generate()),
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
    if (organizations.existsByCode(organization.code(), null))
      throw new DuplicateOrganizationIdentity();
    organizations.save(organization);
    log.info("Organization creation persisted: organizationId={}", organization.id().value());
    return OrganizationResponse.from(organization);
  }
}
