package com.ngockhanh.clinic.identity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class CredentialsMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:18-alpine");

    @Test
    void upgradesLegacyAccountsWithoutInventingCredentialsAndPreservesConstraints() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()).target("1").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        UUID user = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        jdbc.update("INSERT INTO staff(id,staff_code,full_name,staff_type) VALUES(?,'legacy','Legacy staff','DOCTOR')", staff);
        jdbc.update("""
                INSERT INTO users(id,auth_provider,auth_subject,principal_type,staff_id,status,created_at)
                VALUES(?,'legacy-provider','external-subject','STAFF',?,'ACTIVE',CURRENT_TIMESTAMP)
                """, user, staff);
        assertThat(Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT username IS NULL AND password IS NULL AND staff_id=? FROM users WHERE id=?",
                Boolean.class, staff, user)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='users' AND column_name IN ('auth_provider','auth_subject')
                """, Integer.class)).isZero();
        UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,principal_type,staff_id,status,created_at) VALUES(?,'STAFF',?,'ACTIVE',CURRENT_TIMESTAMP)", other, staff);
        jdbc.update("UPDATE users SET username='CaseSensitive' WHERE id=?", user);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET username='CaseSensitive' WHERE id=?", other))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("UPDATE users SET username='casesensitive' WHERE id=?", other)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET principal_type='PATIENT' WHERE id=?", user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
