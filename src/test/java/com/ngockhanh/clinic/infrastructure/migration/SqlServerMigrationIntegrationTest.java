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

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys.tables "
                        + "where schema_id = schema_id('dbo') "
                        + "and name <> 'flyway_schema_history'",
                Integer.class)).isEqualTo(67);

        assertColumnType(jdbcTemplate, "patients", "identification_number", "varchar");
        assertColumnType(jdbcTemplate, "patients", "row_version", "timestamp");
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
}
