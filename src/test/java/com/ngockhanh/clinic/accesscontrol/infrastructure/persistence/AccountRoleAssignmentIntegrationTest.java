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
  private UUID staffMember;
  private UUID patient;

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
  void createSyntheticOwners() {
    staffMember =
        jdbc.queryForObject(
            "INSERT INTO public.staff_members(staff_code,full_name,status) VALUES (?,'Synthetic staff','ACTIVE') RETURNING id",
            UUID.class,
            UUID.randomUUID().toString().substring(0, 20));
    patient =
        jdbc.queryForObject(
            "INSERT INTO public.patients(patient_code,full_name,date_of_birth,sex,identification_number,status) VALUES (?,'Synthetic patient',DATE '2000-01-01','OTHER',?,'ACTIVE') RETURNING id",
            UUID.class,
            "P-" + UUID.randomUUID(),
            UUID.randomUUID().toString());
  }

  @Test
  void aStaffAccountHoldsExactlyOneStaffRole() {
    UUID account = insertStaffAccount("CLINIC_MANAGER");
    assertThat(roleCodeOf(account)).isEqualTo("CLINIC_MANAGER");
    assertThat(jdbc.update("UPDATE public.accounts SET role_id=(SELECT id FROM public.roles WHERE code='RECEPTIONIST') WHERE id=?", account))
        .isEqualTo(1);
    assertThat(roleCodeOf(account)).isEqualTo("RECEPTIONIST");
  }

  @Test
  void aPatientAccountHoldsThePatientRole() {
    assertThat(roleCodeOf(insertPatientAccount("PATIENT"))).isEqualTo("PATIENT");
  }

  @Test
  void aPatientCannotHoldAStaffRole() {
    assertThatThrownBy(() -> insertPatientAccount("CLINIC_MANAGER"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void aStaffAccountCannotHoldThePatientRole() {
    assertThatThrownBy(() -> insertStaffAccount("PATIENT"))
        .isInstanceOf(DataIntegrityViolationException.class);
    UUID account = insertStaffAccount("CLINIC_MANAGER");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.accounts SET role_id=(SELECT id FROM public.roles WHERE code='PATIENT') WHERE id=?",
                    account))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anAccountCannotBeCreatedWithoutARole() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.accounts(account_type,username,password_hash,staff_member_id,status) VALUES ('STAFF',?,'TEST_INVALID_HASH',?,'ACTIVE')",
                    "staff-" + UUID.randomUUID(),
                    staffMember))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private UUID insertStaffAccount(String role) {
    return jdbc.queryForObject(
        "INSERT INTO public.accounts(account_type,role_id,username,password_hash,staff_member_id,status) SELECT 'STAFF',id,?,'TEST_INVALID_HASH',?,'ACTIVE' FROM public.roles WHERE code=? RETURNING id",
        UUID.class,
        "staff-" + UUID.randomUUID(),
        staffMember,
        role);
  }

  private UUID insertPatientAccount(String role) {
    return jdbc.queryForObject(
        "INSERT INTO public.accounts(account_type,role_id,username,password_hash,patient_id,status) SELECT 'PATIENT',id,?,'TEST_INVALID_HASH',?,'ACTIVE' FROM public.roles WHERE code=? RETURNING id",
        UUID.class,
        "patient-" + UUID.randomUUID(),
        patient,
        role);
  }

  private static String roleCodeOf(UUID account) {
    return jdbc.queryForObject(
        "SELECT r.code FROM public.accounts a JOIN public.roles r ON r.id=a.role_id WHERE a.id=?",
        String.class,
        account);
  }
}
