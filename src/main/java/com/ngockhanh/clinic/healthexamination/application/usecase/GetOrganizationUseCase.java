package com.ngockhanh.clinic.healthexamination.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetOrganizationUseCase {
    private final OrganizationRepository organizations;

    @Transactional(readOnly = true)
    public OrganizationResponse execute(UUID id) {
        if (id == null) throw new IllegalArgumentException("Organization ID is required");

        Organization organization = organizations.findById(AggregateId.of(id))
                .orElseThrow(() -> new ResourceNotFoundException("Organization"));

        log.debug("Organization retrieved: organizationId={}", id);
        return OrganizationResponse.from(organization);
    }
}
