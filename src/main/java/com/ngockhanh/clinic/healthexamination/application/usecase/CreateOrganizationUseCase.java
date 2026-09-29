package com.ngockhanh.clinic.healthexamination.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;

import com.ngockhanh.clinic.shared.infrastructure.id.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrganizationUseCase {
    private final OrganizationRepository organizations;
    private final IdGenerator ids;

    @Transactional
    public OrganizationResponse execute(CreateOrganizationCommand command) {
        if (command == null) throw new IllegalArgumentException("Missing organization command");
        if (command.taxCode() != null && !command.taxCode().isBlank()
                && organizations.findByTaxCode(command.taxCode()).isPresent()) {
            throw new DuplicateOrganizationIdentity();
        }
        AggregateId id = new AggregateId(ids.next());
        Organization organization = Organization.create(id, command.name(), command.taxCode(),
                command.address(), command.contactName(), command.contactPhone(), command.contactJobTitle(), command.note());
        organizations.save(organization);
        log.info("Organization creation persisted: organizationId={}", organization.id().value());
        return OrganizationResponse.from(organization);
    }
}
