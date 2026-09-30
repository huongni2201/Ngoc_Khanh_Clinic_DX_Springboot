package com.ngockhanh.clinic.infrastructure.migration;

import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class HealthExaminationDatabaseMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("nkclinic")
            .withUsername("nkclinic")
            .withPassword("test-password");

    @Test
    void migratesTheCompleteChainAndEnforcesHealthExaminationContracts() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion()))
                .containsExactly("001", "002");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        assertThat(countRows(jdbcTemplate,
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_type = 'BASE TABLE' "
                        + "and table_name <> 'flyway_schema_history'"))
                .isEqualTo(65);

        assertColumnType(jdbcTemplate, "health_examination_batch_participant_services",
                "service_request_id", "uuid");
        assertColumnNullable(jdbcTemplate, "health_examination_batch_participant_services",
                "service_request_id");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes "
                        + "where schemaname = 'public' "
                        + "and tablename = 'health_examination_batch_participant_services' "
                        + "and indexname = 'ux_health_examination_batch_participant_services_request' "
                        + "and indexdef like '%(service_request_id)%'",
                Integer.class)).isEqualTo(1);
        assertColumnAbsent(jdbcTemplate, "health_examination_batch_participants", "administrative_snapshot_json");
        assertColumnNotNull(jdbcTemplate, "health_examination_batch_participants", "full_name_snapshot");
        assertColumnNotNull(jdbcTemplate, "health_examination_batch_participants", "date_of_birth_snapshot");
        assertColumnNotNull(jdbcTemplate, "health_examination_batch_participants", "sex_snapshot");
        assertColumnNotNull(jdbcTemplate, "health_examination_batch_participants", "identification_number_snapshot");
        assertColumnAbsent(jdbcTemplate, "organizations", "organization_code");
        assertConstraintAbsent(jdbcTemplate, "uq_organizations_organization_code");

        assertConstraint(jdbcTemplate, "health_examination_batches", "ck_health_examination_batches_date_range");
        assertConstraint(jdbcTemplate, "health_examination_batch_services",
                "ck_health_examination_batch_services_base_price_nonnegative");
        assertConstraint(jdbcTemplate, "health_examination_batch_services",
                "ck_health_examination_batch_services_neg_price_nonnegative");
        assertConstraint(jdbcTemplate, "health_examination_batch_services",
                "ck_health_examination_batch_services_display_order_positive");
        assertConstraint(jdbcTemplate, "health_examination_batch_services",
                "ck_health_examination_batch_services_currency_vnd");
        assertConstraint(jdbcTemplate, "health_examination_batch_participant_services",
                "ck_health_exam_participant_services_unit_price_nonnegative");
        assertConstraint(jdbcTemplate, "health_examination_import_jobs",
                "ck_health_examination_import_jobs_counts_nonnegative");
        assertConstraint(jdbcTemplate, "health_examination_import_jobs",
                "ck_health_examination_import_jobs_counts_not_over_total");
        assertConstraint(jdbcTemplate, "health_examination_import_jobs",
                "ck_health_examination_import_jobs_status");
        assertConstraint(jdbcTemplate, "health_examination_import_rows",
                "ck_health_examination_import_rows_row_number_positive");
        assertConstraint(jdbcTemplate, "health_examination_import_rows",
                "ck_health_examination_import_rows_validation_status");
        assertConstraint(jdbcTemplate, "organizations", "ck_organizations_status");
        assertConstraint(jdbcTemplate, "health_examination_participants",
                "ck_health_examination_participants_status");

        assertThat(jdbcTemplate.queryForList(
                "select table_name, column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name <> 'flyway_schema_history' "
                        + "and data_type = 'timestamp without time zone'"))
                .isEmpty();
    }

    private static int countRows(JdbcTemplate jdbcTemplate, String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private static void assertColumnType(
            JdbcTemplate jdbcTemplate, String tableName, String columnName, String expectedType) {
        assertThat(jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class, tableName, columnName)).isEqualTo(expectedType);
    }

    private static void assertColumnNullable(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        assertThat(jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class, tableName, columnName)).isEqualTo("YES");
    }

    private static void assertColumnNotNull(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        assertThat(jdbcTemplate.queryForObject(
                "select is_nullable from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class, tableName, columnName)).isEqualTo("NO");
    }

    private static void assertColumnAbsent(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = ?",
                Integer.class, tableName, columnName))
                .isZero();
    }

    private static void assertConstraint(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint c "
                        + "join pg_class t on t.oid = c.conrelid "
                        + "join pg_namespace n on n.oid = t.relnamespace "
                        + "where n.nspname = 'public' and t.relname = ? "
                        + "and c.conname = ? and c.contype = 'c' and c.convalidated",
                Integer.class, tableName, constraintName)).isEqualTo(1);
    }

    private static void assertConstraintAbsent(JdbcTemplate jdbcTemplate, String constraintName) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_constraint c "
                        + "join pg_namespace n on n.oid = c.connamespace "
                        + "where n.nspname = 'public' and c.conname = ?",
                Integer.class, constraintName)).isZero();
    }
}
