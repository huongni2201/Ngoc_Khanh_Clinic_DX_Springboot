package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

class OrganizationCrudUseCaseTest {
    @Test
    void updatesDetailsAndDeactivatesOrganization() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000010");
        InMemoryOrganizations organizations = new InMemoryOrganizations();
        organizations.save(Organization.restore(new AggregateId(id), "Old name", null, null,
                "Contact", "0900000000", null, null, "ACTIVE", 3L));

        var updated = new UpdateOrganizationUseCase(organizations).execute(id,
                new UpdateOrganizationCommand("New name", "TAX-2", "Address", "New contact",
                        "0911111111", "Director", "Note"));

        assertThat(updated.name()).isEqualTo("New name");
        assertThat(updated.taxCode()).isEqualTo("TAX-2");
        assertThat(updated.id()).isEqualTo(id);
        assertThat(organizations.findById(new AggregateId(id)).orElseThrow().rowVersion()).isEqualTo(3L);

        new DeactivateOrganizationUseCase(organizations).execute(id);

        assertThat(organizations.findById(new AggregateId(id)).orElseThrow().status()).isEqualTo("INACTIVE");
    }

    @Test
    void normalizesOptionalFieldsWhenUpdatingOrganization() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000011");
        InMemoryOrganizations organizations = new InMemoryOrganizations();
        organizations.save(Organization.create(new AggregateId(id), "Old name", null, null,
                "Contact", "0900000000", null, null));

        new UpdateOrganizationUseCase(organizations).execute(id,
                new UpdateOrganizationCommand(" New name ", "  ", " ", " New contact ",
                        " 0911111111 ", " ", " "));

        Organization updated = organizations.findById(new AggregateId(id)).orElseThrow();
        assertThat(updated.name()).isEqualTo("New name");
        assertThat(updated.taxCode()).isNull();
        assertThat(updated.address()).isNull();
        assertThat(updated.contactName()).isEqualTo("New contact");
        assertThat(updated.contactPhone()).isEqualTo("0911111111");
        assertThat(updated.contactJobTitle()).isNull();
        assertThat(updated.note()).isNull();
    }

    private static final class InMemoryOrganizations implements OrganizationRepository {
        private final Map<AggregateId, Organization> organizations = new HashMap<>();

        @Override
        public Optional<Organization> findById(AggregateId id) {
            return Optional.ofNullable(organizations.get(id));
        }

        @Override
        public Optional<Organization> findByTaxCode(String taxCode) {
            return organizations.values().stream().filter(organization -> taxCode.equals(organization.taxCode())).findFirst();
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
