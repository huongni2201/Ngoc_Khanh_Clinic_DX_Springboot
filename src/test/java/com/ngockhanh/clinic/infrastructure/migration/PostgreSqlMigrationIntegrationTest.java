package com.ngockhanh.clinic.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("nkclinic")
            .withUsername("nkclinic")
            .withPassword("test-password");

    @Test
    void migrationCreatesLatestPostgreSqlSchemaAndRepresentativeTypes() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_type = 'BASE TABLE' "
                        + "and table_name <> 'flyway_schema_history'",
                Integer.class)).isEqualTo(65);

        assertColumnType(jdbcTemplate, "patients", "identification_number", "character varying");
        assertColumnType(jdbcTemplate, "patients", "row_version", "bigint");
        assertColumnType(jdbcTemplate, "patients", "created_at", "timestamp with time zone");
        assertThat(jdbcTemplate.queryForObject(
                "select column_default from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'organizations' and column_name = 'id'",
                String.class)).isNull();
        assertColumnType(jdbcTemplate, "health_examination_participants", "identification_number", "character varying");
        assertColumnType(jdbcTemplate, "services", "health_examination_eligible", "boolean");
        assertColumnType(jdbcTemplate, "document_templates", "is_master_health_examination_form", "boolean");
        assertColumnType(jdbcTemplate, "appointments", "physician_staff_id", "uuid");
        assertColumnType(jdbcTemplate, "appointments", "scheduled_start", "timestamp with time zone");
        assertColumnType(jdbcTemplate, "encounter_assignments", "physician_staff_id", "uuid");
        assertColumnType(jdbcTemplate, "health_examination_records", "identification_number_snapshot", "character varying");
        assertColumnType(jdbcTemplate, "health_examination_records", "identification_number_issue_date_snapshot", "date");
        assertColumnType(jdbcTemplate, "health_examination_records", "identification_number_issue_place_snapshot", "character varying");
        assertColumnType(jdbcTemplate, "health_examination_import_rows", "identification_number_snapshot", "character varying");
        assertColumnType(jdbcTemplate, "lab_results", "released_to_patient_at", "timestamp with time zone");
        assertColumnType(jdbcTemplate, "lab_results", "released_to_patient_by_user_id", "uuid");
        assertColumnType(jdbcTemplate, "diagnostic_reports", "released_to_patient_at", "timestamp with time zone");
        assertColumnType(jdbcTemplate, "diagnostic_reports", "released_to_patient_by_user_id", "uuid");
        assertThat(countRows(jdbcTemplate,
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and data_type = 'timestamp without time zone'"))
                .isZero();
        assertForeignKey(jdbcTemplate, "lab_results", "fk_lab_results_released_to_patient_by_user_id");
        assertForeignKey(jdbcTemplate, "diagnostic_reports", "fk_diagnostic_reports_released_to_patient_by_user_id");

        assertThat(countRows(jdbcTemplate,
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name like 'journey%'")).isZero();
        assertThat(countRows(jdbcTemplate,
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name like 'health_check%'")).isZero();
        assertThat(countRows(jdbcTemplate,
                "select count(*) from information_schema.columns where table_schema = 'public' "
                        + "and column_name like 'identification_number%'"))
                .isEqualTo(6);
        assertThat(countRows(jdbcTemplate,
                "select count(*) from information_schema.columns where table_schema = 'public' "
                        + "and column_name like 'cccd%'"))
                .isZero();

        assertUniqueIndex(jdbcTemplate, "patients", "ux_patients_identification_number");
        assertUniqueIndex(jdbcTemplate, "encounters", "uq_encounters_encounter_code");
        assertUniqueIndex(jdbcTemplate, "encounter_assignments", "ux_encounter_assignments_active");
        assertUniqueIndex(jdbcTemplate, "payment_authorizations", "uq_payment_authorizations_service_request_id");
        assertUniqueIndex(jdbcTemplate, "health_examination_records", "ux_health_examination_records_shs_code");
        assertUniqueIndex(jdbcTemplate, "health_examination_records", "ux_health_examination_records_encounter");
        assertCheckConstraint(jdbcTemplate, "users", "ck_users_principal_type");
        assertCheckConstraint(jdbcTemplate, "encounters", "ck_encounters_status");
        assertCheckConstraint(jdbcTemplate, "service_requests", "ck_service_requests_status");
        assertCheckConstraint(jdbcTemplate, "payment_authorizations", "ck_payment_authorizations_status");
        assertCheckConstraint(jdbcTemplate, "payments", "ck_payments_status");
        assertCheckConstraint(jdbcTemplate, "health_examination_records", "ck_health_examination_records_status");
        assertColumnType(jdbcTemplate, "health_examination_records", "shs_code", "character varying");
        assertColumnType(jdbcTemplate, "service_requests", "unit_price_snapshot", "numeric");
        assertColumnType(jdbcTemplate, "outbox_events", "payload_json", "text");
    }

    private static int countRows(JdbcTemplate jdbcTemplate, String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private static void assertColumnType(
            JdbcTemplate jdbcTemplate,
            String tableName,
            String columnName,
            String expectedType) {
        String actualType = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class,
                tableName,
                columnName);

        assertThat(actualType).isEqualTo(expectedType);
    }

    private static void assertForeignKey(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint c "
                        + "join pg_class t on t.oid = c.conrelid "
                        + "join pg_namespace n on n.oid = t.relnamespace "
                        + "where n.nspname = 'public' and t.relname = ? "
                        + "and c.conname = ? and c.contype = 'f'",
                Integer.class,
                tableName,
                constraintName)).isEqualTo(1);
    }

    private static void assertUniqueIndex(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes "
                        + "where schemaname = 'public' and tablename = ? and indexname = ? "
                        + "and indexdef like 'CREATE UNIQUE INDEX%'",
                Integer.class,
                tableName,
                indexName)).isEqualTo(1);
    }

    private static void assertCheckConstraint(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint c "
                        + "join pg_class t on t.oid = c.conrelid "
                        + "join pg_namespace n on n.oid = t.relnamespace "
                        + "where n.nspname = 'public' and t.relname = ? "
                        + "and c.conname = ? and c.contype = 'c' and c.convalidated",
                Integer.class,
                tableName,
                constraintName)).isEqualTo(1);
    }
}
