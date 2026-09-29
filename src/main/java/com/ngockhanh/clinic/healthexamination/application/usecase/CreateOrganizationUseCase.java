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
        String taxCode = OrganizationFieldNormalizer.optional(command.taxCode());
        if (taxCode != null && organizations.findByTaxCode(taxCode).isPresent()) {
            throw new DuplicateOrganizationIdentity();
        }
        String name = OrganizationFieldNormalizer.required(command.name());
        String address = OrganizationFieldNormalizer.optional(command.address());
        String contactName = OrganizationFieldNormalizer.required(command.contactName());
        String contactPhone = OrganizationFieldNormalizer.required(command.contactPhone());
        String contactJobTitle = OrganizationFieldNormalizer.optional(command.contactJobTitle());
        String note = OrganizationFieldNormalizer.optional(command.note());
        AggregateId id = new AggregateId(ids.next());
        Organization organization = Organization.create(id, name, taxCode, address,
                contactName, contactPhone, contactJobTitle, note);
        organizations.save(organization);
        log.info("Organization creation persisted: organizationId={}", organization.id().value());
        return OrganizationResponse.from(organization);
    }
}
