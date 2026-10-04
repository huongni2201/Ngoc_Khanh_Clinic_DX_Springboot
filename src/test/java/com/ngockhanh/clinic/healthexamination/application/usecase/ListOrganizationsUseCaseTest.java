package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.application.query.OrganizationListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListOrganizationsUseCaseTest {
  @Test
  void searchesFiltersAndPaginatesOrganizations() {
    OrganizationRepository organizations = mock(OrganizationRepository.class);
    Organization organization =
        Organization.create(
            new AggregateId(UUID.randomUUID()),
            "CLINIC-NORTH",
            "Clinic North",
            "COMPANY",
            "TAX-01",
            "0901",
            "organization@example.test",
            "Address",
            "Contact",
            null,
            "0902",
            "contact@example.test");
    when(organizations.countAll("%clinic%", "ACTIVE")).thenReturn(11L);
    when(organizations.findPage(5, 5, "%clinic%", "ACTIVE", "name", "DESC"))
        .thenReturn(List.of(organization));

    var response =
        new ListOrganizationsUseCase(organizations)
            .execute(
                OrganizationListQuery.builder()
                    .page(2)
                    .size(5)
                    .searchKey(" Clinic ")
                    .status("ACTIVE")
                    .sortKey("name")
                    .sortBy("desc")
                    .build());

    assertThat(response.page()).isEqualTo(2);
    assertThat(response.size()).isEqualTo(5);
    assertThat(response.totalElements()).isEqualTo(11);
    assertThat(response.totalPages()).isEqualTo(3);
    assertThat(response.items()).containsExactly(OrganizationResponse.from(organization));
    verify(organizations).countAll("%clinic%", "ACTIVE");
    verify(organizations).findPage(5, 5, "%clinic%", "ACTIVE", "name", "DESC");
  }
}
