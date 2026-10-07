package com.ngockhanh.clinic.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The local profile's repeatable seed must apply on top of the schema and stay idempotent. */
@Testcontainers(disabledWithoutDocker = true)
class LocalCatalogSeedIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void seedsLocalOrganizationsAndActiveCatalogServicesIdempotently() {
    var flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration", "classpath:db/local")
            .load();
    flyway.migrate();
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.departments WHERE code LIKE 'LOCAL_DEPT_%' AND active",
                Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.organizations WHERE tax_code IN ('LOCAL_TEST_ORG_001', 'LOCAL_TEST_ORG_002', 'LOCAL_TEST_ORG_003') AND status = 'ACTIVE'",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.services WHERE code LIKE 'LOCAL_SVC_%' AND active AND unit_price > 0",
                Integer.class))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(DISTINCT service_type) FROM public.services WHERE code LIKE 'LOCAL_SVC_%'",
                Integer.class))
        .isEqualTo(5);
    assertThat(
            jdbc.queryForList(
                """
                SELECT role.code FROM public.account_roles grant_row
                JOIN public.accounts account ON account.id = grant_row.account_id
                JOIN public.roles role ON role.id = grant_row.role_id
                WHERE account.username = 'admin'
                """,
                String.class))
        .containsExactlyInAnyOrder("CLINIC_MANAGER", "ADMINISTRATOR");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.roles WHERE code IN ('ADMIN', 'USER')", Integer.class))
        .isZero();
  }
}
