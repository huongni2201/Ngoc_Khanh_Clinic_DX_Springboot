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
class LocalAccessControlSeedIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void seedsRoleSpecificAccountsAndGrantsIdempotently() {
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
            jdbc.queryForList(
                "SELECT role.code FROM public.accounts account "
                    + "JOIN public.roles role ON role.id=account.role_id "
                    + "WHERE account.username IN ('administrator','receptionist',"
                    + "'general_practitioner','diagnostic_doctor','data_entry_staff',"
                    + "'clinic_manager','patient','test') ORDER BY role.code",
                String.class))
        .containsExactly(
            "ADMINISTRATOR",
            "CLINIC_MANAGER",
            "DATA_ENTRY_STAFF",
            "DIAGNOSTIC_DOCTOR",
            "GENERAL_PRACTITIONER",
            "PATIENT",
            "RECEPTIONIST",
            "TEST");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.role_permissions grant_row "
                    + "JOIN public.roles role ON role.id=grant_row.role_id "
                    + "WHERE role.code='TEST'",
                Long.class))
        .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM public.permissions", Long.class));
    assertThat(
            jdbc.queryForObject(
                "SELECT account.account_type || '|' || role.code FROM public.accounts account "
                    + "JOIN public.roles role ON role.id=account.role_id "
                    + "WHERE account.username='patient'",
                String.class))
        .isEqualTo("PATIENT|PATIENT");
    assertThat(
            jdbc.queryForList(
                "SELECT permission.code FROM public.role_permissions grant_row "
                    + "JOIN public.roles role ON role.id=grant_row.role_id "
                    + "JOIN public.permissions permission ON permission.id=grant_row.permission_id "
                    + "WHERE role.code='CLINIC_MANAGER' AND permission.code LIKE 'PARTICIPANT_%'",
                String.class))
        .containsExactlyInAnyOrder(
            "PARTICIPANT_VIEW",
            "PARTICIPANT_DETAIL_VIEW",
            "PARTICIPANT_TEMPLATE_DOWNLOAD",
            "PARTICIPANT_IMPORT",
            "PARTICIPANT_EXAMINATION_RECORD_VIEW",
            "PARTICIPANT_EXAMINATION_RECORD_REVIEW",
            "PARTICIPANT_EXAMINATION_FORM_PRINT",
            "PARTICIPANT_EXAMINATION_FORM_BULK_PRINT",
            "PARTICIPANT_CREATE",
            "PARTICIPANT_UPDATE",
            "PARTICIPANT_REMOVE",
            "PARTICIPANT_REACTIVATE");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.role_permissions grant_row "
                    + "JOIN public.roles role ON role.id=grant_row.role_id "
                    + "WHERE role.code='ADMINISTRATOR'",
                Integer.class))
        .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM public.permissions", Integer.class));
  }
}
