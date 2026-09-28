package com.ngockhanh.clinic.healthexamination.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateOrganizationUseCase {
    private final OrganizationRepository organizations;

    @Transactional
    public void execute(UUID id) {
        if (id == null) throw new IllegalArgumentException("Organization ID is required");

        Organization current = organizations.findById(AggregateId.of(id))
                .orElseThrow(() -> new ResourceNotFoundException("Organization"));
        log.debug("Deactivating organization: organizationId={}", id);
        if ("INACTIVE".equals(current.status())) return;

        organizations.update(current.deactivate(), current.rowVersion());
        log.info("Organization deactivation persisted: organizationId={}", id);
    }
}
