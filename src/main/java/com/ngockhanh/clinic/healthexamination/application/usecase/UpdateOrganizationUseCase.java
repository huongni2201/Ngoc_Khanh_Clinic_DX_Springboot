package com.ngockhanh.clinic.healthexamination.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateOrganizationUseCase {
    private final OrganizationRepository organizations;

    @Transactional
    public OrganizationResponse execute(UUID id, UpdateOrganizationCommand command) {
        if (id == null) throw new IllegalArgumentException("Organization ID is required");
        if (command == null) throw new IllegalArgumentException("Missing organization command");

        AggregateId organizationId = AggregateId.of(id);
        Organization current = organizations.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization"));

        if (organizations.findByCode(command.code())
                .filter(found -> !found.id().equals(current.id())).isPresent()
                || (command.taxCode() != null && !command.taxCode().isBlank()
                && organizations.findByTaxCode(command.taxCode())
                .filter(found -> !found.id().equals(current.id())).isPresent())) {
            throw new DuplicateOrganizationIdentity();
        }

        Organization updated = current.updateDetails(command.code(), command.name(), command.taxCode(), command.address(),
                command.contactName(), command.contactPhone(), command.contactJobTitle(), command.note());
        organizations.update(updated, current.rowVersion());
        log.info("Organization update persisted: organizationId={}", id);
        return OrganizationResponse.from(updated);
    }
}
