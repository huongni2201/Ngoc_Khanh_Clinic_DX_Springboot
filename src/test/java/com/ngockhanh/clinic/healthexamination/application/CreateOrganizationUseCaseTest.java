package com.ngockhanh.clinic.healthexamination.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

class CreateOrganizationUseCaseTest {
    @Test
    void createsOrganizationWithApplicationGeneratedIdentityAndAllDocumentedFields() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        InMemoryOrganizations organizations = new InMemoryOrganizations();
        CreateOrganizationUseCase useCase = new CreateOrganizationUseCase(organizations, () -> id);

        var result = useCase.execute(new CreateOrganizationCommand("ORG-01", "Clinic Corp", "TAX-1", "Address",
                "Contact", "0900000000", "Director", "Note"));

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("Clinic Corp");
        assertThat(result.status()).isEqualTo("ACTIVE");
        Organization saved = organizations.findById(new AggregateId(id)).orElseThrow();
        assertThat(saved.code()).isEqualTo("ORG-01");
        assertThat(saved.name()).isEqualTo("Clinic Corp");
        assertThat(saved.taxCode()).isEqualTo("TAX-1");
        assertThat(saved.address()).isEqualTo("Address");
        assertThat(saved.contactJobTitle()).isEqualTo("Director");
        assertThat(saved.note()).isEqualTo("Note");
        assertThat(saved.status()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsDuplicateOrganizationCodeBeforeInsert() {
        UUID existingId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        InMemoryOrganizations organizations = new InMemoryOrganizations();
        organizations.save(Organization.create(new AggregateId(existingId), "ORG-01", "Clinic Corp", "Contact", "0900000000"));
        CreateOrganizationUseCase useCase = new CreateOrganizationUseCase(organizations, UUID::randomUUID);

        assertThatThrownBy(() -> useCase.execute(new CreateOrganizationCommand("ORG-01", "Other Org", null, null,
                "Contact", "0900000000", null, null))).isInstanceOf(DuplicateOrganizationIdentity.class);
    }

    private static final class InMemoryOrganizations implements OrganizationRepository {
        private final Map<AggregateId, Organization> organizations = new HashMap<>();

        @Override
        public Optional<Organization> findById(AggregateId id) { return Optional.ofNullable(organizations.get(id)); }
        @Override
        public Optional<Organization> findByCode(String code) {
            return organizations.values().stream().filter(organization -> organization.code().equals(code)).findFirst();
        }
        @Override
        public Optional<Organization> findByTaxCode(String taxCode) {
            return organizations.values().stream().filter(organization -> taxCode.equals(organization.taxCode())).findFirst();
        }
        @Override
        public void save(Organization organization) { organizations.put(organization.id(), organization); }

        @Override
        public void update(Organization organization, long expectedRowVersion) {
            organizations.put(organization.id(), organization);
        }
    }
}
