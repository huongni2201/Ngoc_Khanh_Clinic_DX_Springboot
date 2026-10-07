package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class AccountRoleAssignmentIntegrationTest {
  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");
  private static JdbcTemplate jdbc;
  private UUID staffAccount;
  private UUID patientAccount;

  @BeforeAll
  static void migrateTheCurrentSchema() {
    Flyway.configure()
        .dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
  }

  @BeforeEach
  void createSyntheticAccounts() {
    UUID staff =
        jdbc.queryForObject(
            "INSERT INTO public.staff_members(staff_code,full_name,status) VALUES (?,'Synthetic staff','ACTIVE') RETURNING id",
            UUID.class,
            UUID.randomUUID().toString().substring(0, 20));
    staffAccount =
        jdbc.queryForObject(
            "INSERT INTO public.accounts(account_type,username,password_hash,staff_member_id,status) VALUES ('STAFF',?,'TEST_INVALID_HASH',?,'ACTIVE') RETURNING id",
            UUID.class,
            "staff-" + UUID.randomUUID(),
            staff);
    UUID patient =
        jdbc.queryForObject(
            "INSERT INTO public.patients(patient_code,full_name,date_of_birth,sex,identification_number,status) VALUES (?,'Synthetic patient',DATE '2000-01-01','OTHER',?,'ACTIVE') RETURNING id",
            UUID.class,
            "P-" + UUID.randomUUID(),
            UUID.randomUUID().toString());
    patientAccount =
        jdbc.queryForObject(
            "INSERT INTO public.accounts(account_type,username,password_hash,patient_id,status) VALUES ('PATIENT',?,'TEST_INVALID_HASH',?,'ACTIVE') RETURNING id",
            UUID.class,
            "patient-" + UUID.randomUUID(),
            patient);
  }

  @Test
  void staffCanGrantThePatientRoleToAPatientAccount() {
    assertThat(grant(patientAccount, "PATIENT", staffAccount)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT granted_by FROM public.account_roles WHERE account_id=?",
                UUID.class,
                patientAccount))
        .isEqualTo(staffAccount);
  }

  @Test
  void aPatientCannotReceiveAStaffRole() {
    assertThatThrownBy(() -> grant(patientAccount, "CLINIC_MANAGER", staffAccount))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aStaffAccountCannotReceiveThePatientRole() {
    assertThatThrownBy(() -> grant(staffAccount, "PATIENT", staffAccount))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aPatientCannotGrantRolesEvenToItself() {
    assertThatThrownBy(() -> grant(patientAccount, "PATIENT", patientAccount))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> grant(staffAccount, "CLINIC_MANAGER", patientAccount))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private int grant(UUID account, String role, UUID grantor) {
    return jdbc.update(
        "INSERT INTO public.account_roles(account_id,role_id,granted_by) SELECT ?,id,? FROM public.roles WHERE code=?",
        account,
        grantor,
        role);
  }
}
