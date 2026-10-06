package com.ngockhanh.clinic.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlMigrationIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void freshSchemaPreservesTypesAndRequiresApplicationVersionIncrement() {
    var flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
    flyway.validate();
    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    assertColumn(jdbc, "patients", "identification_number", "text");
    assertColumn(jdbc, "patients", "created_at", "timestamp with time zone");
    assertColumn(jdbc, "patients", "row_version", "bigint");
    assertColumn(jdbc, "accounts", "refresh_token_hash", "bytea");
    assertColumn(jdbc, "appointments", "doctor_id", "uuid");
    assertColumn(jdbc, "import_jobs", "confirmed_result", "jsonb");
    assertColumn(jdbc, "health_examination_batch_participants", "identification_number", "text");
    assertColumn(jdbc, "health_examination_batches", "deleted_at", "timestamp with time zone");
    assertThat(
            jdbc.queryForObject(
                "SELECT numeric_precision FROM information_schema.columns WHERE table_schema='public' AND table_name='services' AND column_name='unit_price'",
                Integer.class))
        .isEqualTo(14);
    assertThat(
            jdbc.queryForObject(
                "SELECT numeric_scale FROM information_schema.columns WHERE table_schema='public' AND table_name='services' AND column_name='unit_price'",
                Integer.class))
        .isEqualTo(2);
    UUID id =
        jdbc.queryForObject(
            "INSERT INTO public.patients(patient_code,full_name,date_of_birth,sex,identification_number,status) VALUES ('P001','Test Patient',DATE '2000-01-01','MALE','000000000001','ACTIVE') RETURNING id",
            UUID.class);
    assertThat(id.version()).isEqualTo(7);
    jdbc.update("UPDATE public.patients SET full_name='Updated' WHERE id=?", id);
    assertThat(
            jdbc.queryForObject(
                "SELECT row_version FROM public.patients WHERE id=?", Long.class, id))
        .isZero();
    assertThat(
            jdbc.update(
                "UPDATE public.patients SET full_name='Versioned',row_version=row_version+1 WHERE id=? AND row_version=0",
                id))
        .isEqualTo(1);
    assertThat(
            jdbc.update(
                "UPDATE public.patients SET full_name='Stale',row_version=row_version+1 WHERE id=? AND row_version=0",
                id))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT full_name FROM public.patients WHERE id=?", String.class, id))
        .isEqualTo("Versioned");
  }

  private static void assertColumn(JdbcTemplate jdbc, String table, String column, String type) {
    assertThat(
            jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name=? AND column_name=?",
                String.class,
                table,
                column))
        .isEqualTo(type);
  }
}
