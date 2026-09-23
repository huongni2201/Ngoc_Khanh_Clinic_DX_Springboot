package com.ngockhanh.clinic.infrastructure.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SqlServerMigrationIntegrationTest {

    @Container
    static final MSSQLServerContainer<?> SQL_SERVER = new MSSQLServerContainer<>(
            "mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    @Test
    void migrationCreatesAllLatestSchemaTablesAndRepresentativeSqlServerTypes() {
        Flyway flyway = Flyway.configure()
                .dataSource(SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(5);

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys.tables where schema_id = schema_id('dbo')",
                Integer.class)).isEqualTo(66);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys.tables "
                        + "where schema_id = schema_id('dbo') "
                        + "and name <> 'flyway_schema_history'",
                Integer.class)).isEqualTo(65);

        assertColumnType(jdbcTemplate, "patients", "identification_number", "varchar");
        assertColumnType(jdbcTemplate, "patients", "row_version", "timestamp");
        assertColumnType(jdbcTemplate, "company_employees", "identification_number", "varchar");
        assertColumnType(jdbcTemplate, "health_check_records", "identification_number_snapshot", "varchar");
        assertColumnType(jdbcTemplate, "health_check_records", "identification_number_issue_date_snapshot", "date");
        assertColumnType(jdbcTemplate, "health_check_records", "identification_number_issue_place_snapshot", "nvarchar");
        assertColumnType(jdbcTemplate, "health_check_import_rows", "identification_number_snapshot", "varchar");
        assertColumnType(jdbcTemplate, "lab_results", "released_to_patient_at", "datetime2");
        assertColumnType(jdbcTemplate, "lab_results", "released_to_patient_by_user_id", "uniqueidentifier");
        assertColumnType(jdbcTemplate, "diagnostic_reports", "released_to_patient_at", "datetime2");
        assertColumnType(jdbcTemplate, "diagnostic_reports", "released_to_patient_by_user_id", "uniqueidentifier");
        assertForeignKey(jdbcTemplate, "lab_results", "FK_lab_results_released_to_patient_by_user_id");
        assertForeignKey(jdbcTemplate, "diagnostic_reports", "FK_diagnostic_reports_released_to_patient_by_user_id");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys.tables where schema_id = schema_id('dbo') and name like 'journey%'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'dbo' "
                        + "and column_name like 'identification_number%'",
                Integer.class)).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'dbo' "
                        + "and column_name like 'cccd%'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'dbo' "
                        + "and (column_name = 'id' or right(column_name, 3) = '_id') "
                        + "and data_type <> 'uniqueidentifier'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'dbo' "
                        + "and column_name = 'row_version' and data_type = 'timestamp'",
                Integer.class)).isGreaterThan(0);

        assertUniqueIndex(jdbcTemplate, "patients", "UX_patients_identification_number");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys.key_constraints "
                        + "where name = ? and parent_object_id = object_id(?) and type = 'UQ'",
                Integer.class,
                "UQ_company_employees_company_id_identification_number",
                "dbo.company_employees")).isEqualTo(1);
        assertUniqueIndex(jdbcTemplate, "encounters", "UQ_encounters_encounter_code");
        assertUniqueIndex(jdbcTemplate, "encounter_assignments", "UX_encounter_assignments_active");
        assertUniqueIndex(jdbcTemplate, "payment_authorizations", "UQ_payment_authorizations_service_request_id");
        assertUniqueIndex(jdbcTemplate, "health_check_records", "UX_health_check_records_shs_code");
        assertUniqueIndex(jdbcTemplate, "health_check_records", "UX_health_check_records_encounter");
        assertUniqueIndex(jdbcTemplate, "health_check_records", "UX_health_check_records_batch_employee");
        assertUniqueIndex(jdbcTemplate, "health_check_batch_employee_services", "UX_hcbes_service_request");
        assertCheckConstraint(jdbcTemplate, "users", "CK_users_principal_type");
        assertCheckConstraint(jdbcTemplate, "encounters", "CK_encounters_status");
        assertCheckConstraint(jdbcTemplate, "service_requests", "CK_service_requests_status");
        assertCheckConstraint(jdbcTemplate, "payment_authorizations", "CK_payment_authorizations_status");
        assertCheckConstraint(jdbcTemplate, "payments", "CK_payments_status");
        assertCheckConstraint(jdbcTemplate, "health_check_batches", "CK_health_check_batches_status");
        assertCheckConstraint(jdbcTemplate, "health_check_records", "CK_health_check_records_status");
        assertColumnType(jdbcTemplate, "health_check_records", "shs_code", "varchar");
        assertColumnType(jdbcTemplate, "service_requests", "unit_price_snapshot", "decimal");
        assertColumnType(jdbcTemplate, "outbox_events", "payload_json", "nvarchar");
    }

    private static void assertColumnType(
            JdbcTemplate jdbcTemplate,
            String tableName,
            String columnName,
            String expectedType) {
        String actualType = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns "
                        + "where table_schema = 'dbo' and table_name = ? and column_name = ?",
                String.class,
                tableName,
                columnName);

        assertThat(actualType).isEqualTo(expectedType);
    }

    private static void assertForeignKey(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys.foreign_keys "
                        + "where name = ? and parent_object_id = object_id(?)",
                Integer.class,
                constraintName,
                "dbo." + tableName)).isEqualTo(1);
    }

    private static void assertUniqueIndex(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys.indexes "
                        + "where name = ? and object_id = object_id(?) and is_unique = 1",
                Integer.class,
                indexName,
                "dbo." + tableName)).isEqualTo(1);
    }

    private static void assertCheckConstraint(JdbcTemplate jdbcTemplate, String tableName, String constraintName) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys.check_constraints "
                        + "where name = ? and parent_object_id = object_id(?) and is_disabled = 0 and is_not_trusted = 0",
                Integer.class,
                constraintName,
                "dbo." + tableName)).isEqualTo(1);
    }
}
