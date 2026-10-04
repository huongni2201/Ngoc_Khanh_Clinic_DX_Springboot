package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = HealthExaminationBatchCrudIntegrationTest.BatchTestConfiguration.class)
class MyBatisOrganizationRepositoryIntegrationTest {
  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @Autowired OrganizationRepository organizations;

  private Organization organization(String code) {
    return Organization.create(
        new AggregateId(UUID.randomUUID()),
        code,
        "Synthetic School",
        "SCHOOL",
        "SHARED-TAX",
        "0901",
        "o@example.test",
        "Address",
        "Contact",
        "Principal",
        "0902",
        "c@example.test");
  }

  @Test
  void roundTripsCleanSlateChannelsAndUsesCodeUniquenessInsteadOfTaxCode() {
    var first = organization("S1");
    var second = organization("S2");
    organizations.save(first);
    organizations.save(second);
    assertThat(organizations.findById(first.id())).contains(first);
    assertThat(organizations.existsByCode("S1", null)).isTrue();
    assertThat(organizations.existsByCode("S1", first.id())).isFalse();
    assertThatThrownBy(() -> organizations.save(organization("S1")))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  }

  @Test
  void staleWritesAreDetectedAndDeactivationPreservesChannels() {
    var first = organization("S3");
    organizations.save(first);
    organizations.update(first.deactivate(), 0);
    var restored = organizations.findById(first.id()).orElseThrow();
    assertThat(restored.status()).isEqualTo("INACTIVE");
    assertThat(restored.rowVersion()).isEqualTo(1);
    assertThat(restored.contactEmail()).isEqualTo("c@example.test");
    assertThatThrownBy(() -> organizations.update(first, 0))
        .isInstanceOf(ConcurrentUpdateException.class);
  }
}
