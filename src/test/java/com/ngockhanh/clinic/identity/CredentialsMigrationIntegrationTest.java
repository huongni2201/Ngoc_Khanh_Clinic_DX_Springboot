package com.ngockhanh.clinic.identity;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class CredentialsMigrationIntegrationTest {
  @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void freshAccountSchemaRequiresCredentialsOneOwnerAndUniqueCaseSensitiveUsername() {
    Flyway.configure()
        .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
        .load()
        .migrate();
    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
    UUID account = UUID.randomUUID(), staff = UUID.randomUUID(), otherStaff = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO staff_members(id,staff_code,full_name,status) VALUES(?,'first','Test staff','ACTIVE')",
        staff);
    jdbc.update(
        "INSERT INTO staff_members(id,staff_code,full_name,status) VALUES(?,'second','Other staff','ACTIVE')",
        otherStaff);
    jdbc.update(
        "INSERT INTO accounts(id,account_type,staff_member_id,username,password_hash,status) VALUES(?,'STAFF',?,'CaseSensitive','encoded','ACTIVE')",
        account,
        staff);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO accounts(account_type,staff_member_id,username,password_hash,status) VALUES('STAFF',?,'CaseSensitive','encoded','ACTIVE')",
                    otherStaff))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.update(
                "INSERT INTO accounts(account_type,staff_member_id,username,password_hash,status) VALUES('STAFF',?,'casesensitive','encoded','ACTIVE')",
                otherStaff))
        .isEqualTo(1);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE accounts SET account_type='PATIENT' WHERE id=?", account))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE accounts SET password_hash=NULL WHERE id=?", account))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE accounts SET username=' padded ' WHERE id=?", account))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE accounts SET refresh_token_hash=decode('00','hex'), refresh_token_expires_at=CURRENT_TIMESTAMP WHERE id=?",
                    account))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.update(
                "UPDATE accounts SET refresh_token_hash=decode(repeat('00',32),'hex'),refresh_token_expires_at=CURRENT_TIMESTAMP WHERE id=?",
                account))
        .isEqualTo(1);
  }
}
