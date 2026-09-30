package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class MyBatisOrganizationRepositoryIntegrationTest {
  private static java.util.UUID id(long suffix) {
    return java.util.UUID.fromString("01990000-0000-7000-8000-" + String.format("%012x", suffix));
  }

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("nkclinic")
          .withUsername("nkclinic")
          .withPassword("test-password");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired OrganizationRepository organizations;

  @Autowired HealthExaminationParticipantRepository participants;

  @Test
  void savesAndRestoresOrganizationThroughMyBatisAgainstPostgreSql() {
    Organization expected =
        Organization.create(
            new AggregateId(id(1)),
            "Organization",
            "TAX-01",
            "Address",
            "Contact",
            "0900000000",
            "Director",
            "Note");

    organizations.save(expected);

    Organization restored = organizations.findById(expected.id()).orElseThrow();
    assertThat(restored.name()).isEqualTo(expected.name());
    assertThat(restored.taxCode()).isEqualTo(expected.taxCode());
    assertThat(restored.address()).isEqualTo(expected.address());
    assertThat(organizations.findByTaxCode(expected.taxCode()))
        .get()
        .extracting(Organization::id)
        .isEqualTo(expected.id());
  }

  @Test
  void incrementsOrganizationVersionAndRejectsStaleUpdates() {
    Organization expected =
        Organization.create(
            new AggregateId(id(4)),
            "Organization",
            "TAX-04",
            "Address",
            "Contact",
            "0900000000",
            "Director",
            "Note");
    organizations.save(expected);

    Organization loaded = organizations.findById(expected.id()).orElseThrow();
    organizations.update(
        loaded.updateDetails(
            "Updated",
            loaded.taxCode(),
            loaded.address(),
            loaded.contactName(),
            loaded.contactPhone(),
            loaded.contactJobTitle(),
            loaded.note()),
        loaded.rowVersion());

    Organization current = organizations.findById(expected.id()).orElseThrow();
    assertThat(current.rowVersion()).isEqualTo(1L);

    assertThatThrownBy(
            () ->
                organizations.update(
                    loaded.updateDetails(
                        "Stale",
                        loaded.taxCode(),
                        loaded.address(),
                        loaded.contactName(),
                        loaded.contactPhone(),
                        loaded.contactJobTitle(),
                        loaded.note()),
                    loaded.rowVersion()))
        .isInstanceOf(ConcurrentUpdateException.class);

    organizations.update(
        current.updateDetails(
            "Updated again",
            current.taxCode(),
            current.address(),
            current.contactName(),
            current.contactPhone(),
            current.contactJobTitle(),
            current.note()),
        current.rowVersion());
    assertThat(organizations.findById(expected.id()).orElseThrow().rowVersion()).isEqualTo(2L);
  }

  @Test
  void searchesFiltersSortsAndPaginatesOrganizations() {
    Organization alpha =
        Organization.create(
            new AggregateId(id(51)),
            "Alpha Clinic",
            "LIST-51",
            "Address",
            "ListContact Alpha",
            "0900000051",
            null,
            null);
    Organization beta =
        Organization.create(
            new AggregateId(id(52)),
            "Beta Clinic",
            "LIST-52",
            "Address",
            "ListContact Beta",
            "0900000052",
            null,
            null);
    Organization inactive =
        Organization.create(
                new AggregateId(id(53)),
                "Hidden Clinic",
                "LIST-53",
                "Address",
                "ListContact Hidden",
                "0900000053",
                null,
                null)
            .deactivate();
    organizations.save(alpha);
    organizations.save(beta);
    organizations.save(inactive);

    assertThat(organizations.countAll("%listcontact%", "ACTIVE")).isEqualTo(2);
    assertThat(organizations.findPage(1, 1, "%listcontact%", "ACTIVE", "contactName", "DESC"))
        .extracting(Organization::name)
        .containsExactly("Alpha Clinic");
  }

  @Test
  void savesReimportsAndFindsParticipantByOrganizationRosterIdentity() {
    AggregateId organizationId = new AggregateId(id(2));
    AggregateId participantId = new AggregateId(id(3));
    organizations.save(
        Organization.create(organizationId, "Organization", "Contact", "0900000000"));
    HealthExaminationParticipant participant =
        HealthExaminationParticipant.create(
            participantId,
            organizationId,
            "PART-01",
            IdentificationNumber.of("987654321098"),
            "Nguyen A",
            java.time.LocalDate.of(1990, 1, 1),
            "MALE",
            "Department",
            "Technician",
            "Technician");
    participants.save(participant);

    HealthExaminationParticipant restored = participants.findById(participantId).orElseThrow();
    assertThat(restored.departmentName()).isEqualTo("Department");
    assertThat(participants.findByOrganizationAndCode(organizationId, "PART-01"))
        .get()
        .extracting(HealthExaminationParticipant::id)
        .isEqualTo(participantId);
    assertThat(
            participants.findByOrganizationAndIdentificationNumber(
                organizationId, participant.identificationNumber()))
        .get()
        .extracting(HealthExaminationParticipant::id)
        .isEqualTo(participantId);

    participants.save(
        restored.reimport(
            "PART-01",
            participant.identificationNumber(),
            "Updated Name",
            participant.dateOfBirth(),
            participant.sex(),
            "New Department",
            participant.jobTitle(),
            participant.occupation()));

    assertThat(participants.findById(participantId).orElseThrow().fullName())
        .isEqualTo("Updated Name");
  }
}
