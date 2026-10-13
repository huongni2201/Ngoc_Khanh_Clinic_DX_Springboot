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
                "SELECT role.code FROM public.account_roles grant_row "
                    + "JOIN public.accounts account ON account.id=grant_row.account_id "
                    + "JOIN public.roles role ON role.id=grant_row.role_id "
                    + "WHERE account.username IN ('administrator','receptionist',"
                    + "'general_practitioner','diagnostic_doctor','nurse',"
                    + "'clinic_manager','patient','test') ORDER BY role.code",
                String.class))
        .containsExactly(
            "ADMINISTRATOR",
            "CLINIC_MANAGER",
            "DIAGNOSTIC_DOCTOR",
            "GENERAL_PRACTITIONER",
            "NURSE",
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
                "SELECT patient.account_type || '|' || grantor.account_type "
                    + "FROM public.account_roles grant_row "
                    + "JOIN public.accounts patient ON patient.id=grant_row.account_id "
                    + "JOIN public.accounts grantor ON grantor.id=grant_row.granted_by "
                    + "JOIN public.roles role ON role.id=grant_row.role_id "
                    + "WHERE patient.username='patient' AND role.code='PATIENT'",
                String.class))
        .isEqualTo("PATIENT|STAFF");
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
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.staff_members staff "
                    + "WHERE staff.staff_code='LOCAL_ELIGIBLE_OWNER_STAFF' AND staff.status='ACTIVE' "
                    + "AND NOT EXISTS (SELECT 1 FROM public.accounts a WHERE a.staff_member_id=staff.id)",
                Integer.class))
        .as("UC-005 eligible staff owner without an account")
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.patients patient "
                    + "WHERE patient.patient_code='LOCAL_ELIGIBLE_OWNER_PATIENT' "
                    + "AND patient.status='ACTIVE' "
                    + "AND NOT EXISTS (SELECT 1 FROM public.accounts a WHERE a.patient_id=patient.id)",
                Integer.class))
        .as("UC-005 eligible patient owner without an account")
        .isEqualTo(1);
  }
}
