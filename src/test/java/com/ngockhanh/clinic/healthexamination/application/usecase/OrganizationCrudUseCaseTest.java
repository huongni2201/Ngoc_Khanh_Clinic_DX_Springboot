package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationCrudUseCaseTest {
  @Test
  void updatesDetailsAndDeactivatesOrganization() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000010");
    InMemoryOrganizations organizations = new InMemoryOrganizations();
    organizations.save(
        Organization.restore(
            new AggregateId(id),
            "Old name",
            null,
            null,
            "Contact",
            "0900000000",
            null,
            null,
            "ACTIVE",
            3L));

    var updated =
        new UpdateOrganizationUseCase(organizations)
            .execute(
                id,
                new UpdateOrganizationCommand(
                    "New name",
                    "TAX-2",
                    "Address",
                    "New contact",
                    "0911111111",
                    "Director",
                    "Note"));

    assertThat(updated.name()).isEqualTo("New name");
    assertThat(updated.taxCode()).isEqualTo("TAX-2");
    assertThat(updated.id()).isEqualTo(id);
    assertThat(organizations.findById(new AggregateId(id)).orElseThrow().rowVersion())
        .isEqualTo(3L);

    new DeactivateOrganizationUseCase(organizations).execute(id);

    assertThat(organizations.findById(new AggregateId(id)).orElseThrow().status())
        .isEqualTo("INACTIVE");
  }

  @Test
  void normalizesOptionalFieldsWhenUpdatingOrganization() {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000011");
    InMemoryOrganizations organizations = new InMemoryOrganizations();
    organizations.save(
        Organization.create(
            new AggregateId(id), "Old name", null, null, "Contact", "0900000000", null, null));

    new UpdateOrganizationUseCase(organizations)
        .execute(
            id,
            new UpdateOrganizationCommand(
                " New name ", "  ", " ", " New contact ", " 0911111111 ", " ", " "));

    Organization updated = organizations.findById(new AggregateId(id)).orElseThrow();
    assertThat(updated.name()).isEqualTo("New name");
    assertThat(updated.taxCode()).isNull();
    assertThat(updated.address()).isNull();
    assertThat(updated.contactName()).isEqualTo("New contact");
    assertThat(updated.contactPhone()).isEqualTo("0911111111");
    assertThat(updated.contactJobTitle()).isNull();
    assertThat(updated.note()).isNull();
  }

  @Test
  void permitsTheCurrentTaxCodeButRejectsAnotherOrganizationsTaxCode() {
    var organizations = new InMemoryOrganizations();
    var current =
        Organization.create(
            new AggregateId(UUID.randomUUID()),
            "Current",
            "OWN-TAX",
            null,
            "Contact",
            "0900000000",
            null,
            null);
    var other =
        Organization.create(
            new AggregateId(UUID.randomUUID()),
            "Other",
            "OTHER-TAX",
            null,
            "Contact",
            "0900000001",
            null,
            null);
    organizations.save(current);
    organizations.save(other);
    var useCase = new UpdateOrganizationUseCase(organizations);
    assertThat(
            useCase
                .execute(
                    current.id().value(),
                    new UpdateOrganizationCommand(
                        "Updated", "OWN-TAX", null, "Contact", "0900000000", null, null))
                .taxCode())
        .isEqualTo("OWN-TAX");
    assertThatThrownBy(
            () ->
                useCase.execute(
                    current.id().value(),
                    new UpdateOrganizationCommand(
                        "Updated", "OTHER-TAX", null, "Contact", "0900000000", null, null)))
        .isInstanceOf(DuplicateOrganizationIdentity.class);
    assertThat(organizations.findById(current.id()).orElseThrow().taxCode()).isEqualTo("OWN-TAX");
  }

  private static final class InMemoryOrganizations implements OrganizationRepository {
    private final Map<AggregateId, Organization> organizations = new HashMap<>();

    @Override
    public Optional<Organization> findById(AggregateId id) {
      return Optional.ofNullable(organizations.get(id));
    }

    @Override
    public boolean existsByTaxCode(String taxCode, AggregateId excludedOrganizationId) {
      return organizations.values().stream()
          .anyMatch(
              organization ->
                  taxCode.equals(organization.taxCode())
                      && !organization.id().equals(excludedOrganizationId));
    }

    @Override
    public List<Organization> findPage(
        long offset,
        long limit,
        String searchPattern,
        String status,
        String sortKey,
        String sortBy) {
      return List.of();
    }

    @Override
    public long countAll(String searchPattern, String status) {
      return 0;
    }

    @Override
    public void save(Organization organization) {
      organizations.put(organization.id(), organization);
    }

    @Override
    public void update(Organization organization, long expectedRowVersion) {
      organizations.put(organization.id(), organization);
    }
  }
}
