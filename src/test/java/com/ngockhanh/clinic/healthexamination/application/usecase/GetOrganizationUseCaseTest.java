package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetOrganizationUseCaseTest {
  @Test
  void returnsOneOrganizationById() {
    UUID organizationId = UUID.randomUUID();
    AggregateId id = AggregateId.of(organizationId);
    Organization organization =
        Organization.create(
            id,
            "ORG-01",
            "Clinic Partner",
            "COMPANY",
            null,
            "0901",
            "office@example.test",
            "Address",
            "Contact Person",
            null,
            "0902",
            "contact@example.test");
    var organizations = mock(OrganizationRepository.class);
    when(organizations.findById(id)).thenReturn(Optional.of(organization));

    var response = new GetOrganizationUseCase(organizations).execute(organizationId);

    assertThat(response.id()).isEqualTo(organizationId);
    assertThat(response.code()).isEqualTo("ORG-01");
    assertThat(response.name()).isEqualTo("Clinic Partner");
    verify(organizations).findById(id);
  }

  @Test
  void missingOrganizationRaisesNotFound() {
    UUID organizationId = UUID.randomUUID();
    AggregateId id = AggregateId.of(organizationId);
    var organizations = mock(OrganizationRepository.class);
    when(organizations.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new GetOrganizationUseCase(organizations).execute(organizationId))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(organizations).findById(id);
  }
}
