package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
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

class CreateOrganizationUseCaseTest {
  @Test
  void createsOrganizationWithApplicationGeneratedIdentityAndAllDocumentedFields() {
    InMemoryOrganizations organizations = new InMemoryOrganizations();
    CreateOrganizationUseCase useCase = new CreateOrganizationUseCase(organizations);

    var result =
        useCase.execute(
            new CreateOrganizationCommand(
                "Clinic Corp", "TAX-1", "Address", "Contact", "0900000000", "Director", "Note"));

    assertThat(result.id().version()).isEqualTo(7);
    assertThat(result.name()).isEqualTo("Clinic Corp");
    assertThat(result.status()).isEqualTo("ACTIVE");
    Organization saved = organizations.findById(new AggregateId(result.id())).orElseThrow();
    assertThat(saved.name()).isEqualTo("Clinic Corp");
    assertThat(saved.taxCode()).isEqualTo("TAX-1");
    assertThat(saved.address()).isEqualTo("Address");
    assertThat(saved.contactJobTitle()).isEqualTo("Director");
    assertThat(saved.note()).isEqualTo("Note");
    assertThat(saved.status()).isEqualTo("ACTIVE");
  }

  @Test
  void rejectsDuplicateOrganizationTaxCodeBeforeInsert() {
    UUID existingId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    InMemoryOrganizations organizations = new InMemoryOrganizations();
    organizations.save(
        Organization.create(
            new AggregateId(existingId),
            "Clinic Corp",
            "TAX-1",
            null,
            "Contact",
            "0900000000",
            null,
            null));
    CreateOrganizationUseCase useCase = new CreateOrganizationUseCase(organizations);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new CreateOrganizationCommand(
                        "Other Org", "TAX-1", null, "Contact", "0900000000", null, null)))
        .isInstanceOf(DuplicateOrganizationIdentity.class);
  }

  @Test
  void normalizesBlankOptionalFieldsAndTrimsRequiredFieldsBeforeSaving() {
    InMemoryOrganizations organizations = new InMemoryOrganizations();
    CreateOrganizationUseCase useCase = new CreateOrganizationUseCase(organizations);

    var result =
        useCase.execute(
            new CreateOrganizationCommand(
                " Clinic Corp ", "  ", "  ", " Contact ", " 0900000000 ", " ", "  "));

    Organization saved = organizations.findById(new AggregateId(result.id())).orElseThrow();
    assertThat(saved.name()).isEqualTo("Clinic Corp");
    assertThat(saved.taxCode()).isNull();
    assertThat(saved.address()).isNull();
    assertThat(saved.contactName()).isEqualTo("Contact");
    assertThat(saved.contactPhone()).isEqualTo("0900000000");
    assertThat(saved.contactJobTitle()).isNull();
    assertThat(saved.note()).isNull();
  }

  @Test
  void allowsMultipleOrganizationsWithBlankTaxCodes() {
    InMemoryOrganizations organizations = new InMemoryOrganizations();
    CreateOrganizationUseCase useCase = new CreateOrganizationUseCase(organizations);

    useCase.execute(
        new CreateOrganizationCommand("First", "", null, "Contact", "0900000000", null, null));
    useCase.execute(
        new CreateOrganizationCommand("Second", " ", null, "Contact", "0900000001", null, null));

    assertThat(organizations.organizations).hasSize(2);
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
