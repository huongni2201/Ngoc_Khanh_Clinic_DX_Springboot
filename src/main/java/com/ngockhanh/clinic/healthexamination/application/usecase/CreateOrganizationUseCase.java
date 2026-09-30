package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;

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

    @Transactional
    public OrganizationResponse execute(CreateOrganizationCommand command) {
        if (command == null) throw new IllegalArgumentException("Missing organization command");
        String taxCode = trimOptional(command.taxCode());
        if (taxCode != null && organizations.findByTaxCode(taxCode).isPresent()) {
            throw new DuplicateOrganizationIdentity();
        }
        String name = trimRequired(command.name());
        String address = trimOptional(command.address());
        String contactName = trimRequired(command.contactName());
        String contactPhone = trimRequired(command.contactPhone());
        String contactJobTitle = trimOptional(command.contactJobTitle());
        String note = trimOptional(command.note());
        AggregateId id = new AggregateId(UuidV7Generator.generate());
        Organization organization = Organization.create(id, name, taxCode, address,
                contactName, contactPhone, contactJobTitle, note);
        organizations.save(organization);
        log.info("Organization creation persisted: organizationId={}", organization.id().value());
        return OrganizationResponse.from(organization);
    }

    private static String trimRequired(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimOptional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
