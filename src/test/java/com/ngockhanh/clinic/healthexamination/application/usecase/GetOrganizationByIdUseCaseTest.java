package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetOrganizationByIdUseCaseTest {
  private Organization organization(AggregateId id, String status, long version) {
    return Organization.restore(
        id,
        "Clinic Partner",
        "TAX-01",
        "0901",
        "office@example.test",
        "Address",
        "Contact Person",
        "0902",
        "contact@example.test",
        status,
        version);
  }

  @Test
  void returnsActiveOrganizationWithAllFields() {
    UUID organizationId = UUID.randomUUID();
    AggregateId id = AggregateId.of(organizationId);
    var organizations = mock(OrganizationRepository.class);
    when(organizations.findById(id)).thenReturn(Optional.of(organization(id, "ACTIVE", 4)));

    var response = new GetOrganizationByIdUseCase(organizations).execute(organizationId);

    assertThat(response.id()).isEqualTo(organizationId);
    assertThat(response.name()).isEqualTo("Clinic Partner");
    assertThat(response.taxCode()).isEqualTo("TAX-01");
    assertThat(response.phone()).isEqualTo("0901");
    assertThat(response.email()).isEqualTo("office@example.test");
    assertThat(response.address()).isEqualTo("Address");
    assertThat(response.contactFullName()).isEqualTo("Contact Person");
    assertThat(response.contactPhone()).isEqualTo("0902");
    assertThat(response.contactEmail()).isEqualTo("contact@example.test");
    assertThat(response.status()).isEqualTo("ACTIVE");
    assertThat(response.rowVersion()).isEqualTo(4);
    verify(organizations).findById(id);
  }

  @Test
  void inactiveOrganizationRaisesNotFound() {
    UUID organizationId = UUID.randomUUID();
    AggregateId id = AggregateId.of(organizationId);
    var organizations = mock(OrganizationRepository.class);
    when(organizations.findById(id)).thenReturn(Optional.of(organization(id, "INACTIVE", 2)));

    assertThatThrownBy(() -> new GetOrganizationByIdUseCase(organizations).execute(organizationId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void missingOrganizationRaisesNotFound() {
    UUID organizationId = UUID.randomUUID();
    AggregateId id = AggregateId.of(organizationId);
    var organizations = mock(OrganizationRepository.class);
    when(organizations.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new GetOrganizationByIdUseCase(organizations).execute(organizationId))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(organizations).findById(id);
  }

  @Test
  void nullIdIsRejectedWithoutReadingTheRepository() {
    var organizations = mock(OrganizationRepository.class);

    assertThatThrownBy(() -> new GetOrganizationByIdUseCase(organizations).execute(null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(organizations);
  }
}
