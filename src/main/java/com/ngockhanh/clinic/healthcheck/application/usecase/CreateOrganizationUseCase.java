package com.ngockhanh.clinic.healthcheck.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthcheck.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthcheck.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthcheck.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.OrganizationId;
import com.ngockhanh.clinic.shared.id.IdGenerator;

@Service
public final class CreateOrganizationUseCase {
    private final OrganizationRepository organizations;
    private final IdGenerator ids;

    public CreateOrganizationUseCase(OrganizationRepository organizations, IdGenerator ids) {
        this.organizations = organizations;
        this.ids = ids;
    }

    @Transactional
    public UUID execute(CreateOrganizationCommand command) {
        if (command == null) throw new IllegalArgumentException("Missing organization command");
        if (organizations.findByCode(command.code()).isPresent()
                || (command.taxCode() != null && !command.taxCode().isBlank()
                    && organizations.findByTaxCode(command.taxCode()).isPresent())) {
            throw new DuplicateOrganizationIdentity();
        }
        OrganizationId id = new OrganizationId(ids.next());
        Organization organization = Organization.create(id, command.code(), command.name(), command.taxCode(),
                command.address(), command.contactName(), command.contactPhone(), command.contactJobTitle(), command.note());
        organizations.save(organization);
        return organization.id().value();
    }
}
