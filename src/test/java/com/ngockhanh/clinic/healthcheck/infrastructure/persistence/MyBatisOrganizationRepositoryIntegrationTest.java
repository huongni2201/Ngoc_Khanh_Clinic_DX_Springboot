package com.ngockhanh.clinic.healthcheck.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthcheck.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.OrganizationId;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class MyBatisOrganizationRepositoryIntegrationTest {
    private static java.util.UUID id(long suffix) {
        return java.util.UUID.fromString("01990000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("nkclinic")
            .withUsername("nkclinic")
            .withPassword("test-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    HealthExaminationParticipantRepository participants;

    @Test
    void savesAndRestoresOrganizationThroughMyBatisAgainstPostgreSql() {
        Organization expected = Organization.create(new OrganizationId(id(1)), "MYBATIS-ORG-01", "Organization", "TAX-01",
                "Address", "Contact", "0900000000", "Director", "Note");

        organizations.save(expected);

        Organization restored = organizations.findById(expected.id()).orElseThrow();
        assertThat(restored.code()).isEqualTo(expected.code());
        assertThat(restored.name()).isEqualTo(expected.name());
        assertThat(restored.taxCode()).isEqualTo(expected.taxCode());
        assertThat(restored.address()).isEqualTo(expected.address());
        assertThat(organizations.findByCode(expected.code())).get().extracting(Organization::id).isEqualTo(expected.id());
        assertThat(organizations.findByTaxCode(expected.taxCode())).get().extracting(Organization::id).isEqualTo(expected.id());
    }

    @Test
    void savesReimportsAndFindsParticipantByOrganizationRosterIdentity() {
        OrganizationId organizationId = new OrganizationId(id(2));
        java.util.UUID participantId = id(3);
        organizations.save(Organization.create(organizationId, "MYBATIS-PART-ORG", "Organization", "Contact", "0900000000"));
        HealthExaminationParticipant participant = HealthExaminationParticipant.create(participantId, organizationId.value(), "PART-01",
                IdentificationNumber.of("987654321098"), "Nguyen A", java.time.LocalDate.of(1990, 1, 1), "MALE",
                "Department", "Technician", "Technician");
        participants.save(participant);

        HealthExaminationParticipant restored = participants.findById(participantId).orElseThrow();
        assertThat(restored.departmentName()).isEqualTo("Department");
        assertThat(participants.findByOrganizationAndCode(organizationId.value(), "PART-01"))
                .get().extracting(HealthExaminationParticipant::id).isEqualTo(participantId);
        assertThat(participants.findByOrganizationAndIdentificationNumber(organizationId.value(), participant.identificationNumber()))
                .get().extracting(HealthExaminationParticipant::id).isEqualTo(participantId);

        participants.save(restored.reimport("PART-01", participant.identificationNumber(), "Updated Name", participant.dateOfBirth(),
                participant.sex(), "New Department", participant.jobTitle(), participant.occupation()));

        assertThat(participants.findById(participantId).orElseThrow().fullName()).isEqualTo("Updated Name");
    }
}
