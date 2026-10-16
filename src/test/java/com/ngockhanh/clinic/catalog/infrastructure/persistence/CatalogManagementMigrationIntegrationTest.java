package com.ngockhanh.clinic.catalog.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies the fresh catalog schema, constraints and permissions on PostgreSQL 18. */
@Testcontainers(disabledWithoutDocker = true)
class CatalogManagementMigrationIntegrationTest {
  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");
  JdbcTemplate jdbc;

  Flyway flyway(String target, String... locations) {
    var cfg = Flyway.configure()
        .dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
        .locations(locations).cleanDisabled(false);
    if (target != null) cfg.target(target);
    return cfg.load();
  }

  @BeforeEach
  void resetTestDatabase() {
    flyway(null, "classpath:db/migration").clean();
    flyway("6", "classpath:db/migration").migrate();
    jdbc = new JdbcTemplate(
        new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
  }

  @Test
  void freshSchemaHasSequentialVersionsAndKeepsSeededServicePrices() {
    var prices = jdbc.queryForList("SELECT id,unit_price FROM services ORDER BY id");
    assertThat(flyway(null, "classpath:db/migration").migrate().migrationsExecuted).isEqualTo(3);
    assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank", String.class))
        .containsExactly("001", "002", "003", "004", "005", "006", "007", "008", "009");
    assertThat(jdbc.queryForList("SELECT id,unit_price FROM services ORDER BY id")).isEqualTo(prices);
    assertThat(flyway(null, "classpath:db/migration").migrate().migrationsExecuted).isZero();
  }

  @Test
  void allCatalogRecordsHaveTimestampsAndRejectNegativeVersions() {
    flyway(null, "classpath:db/migration").migrate();
    for (var row : createCatalogRecords("VERSION_").entrySet()) {
      var saved = jdbc.queryForMap(
          "SELECT created_at,updated_at,row_version FROM " + row.getKey() + " WHERE id=?", row.getValue());
      assertThat(saved.get("created_at")).isNotNull();
      assertThat(saved.get("updated_at")).isNotNull();
      assertThat(saved.get("row_version")).isEqualTo(0L);
      assertThatThrownBy(() -> jdbc.update(
          "UPDATE " + row.getKey() + " SET row_version=-1 WHERE id=?", row.getValue()))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update(
          "UPDATE " + row.getKey() + " SET created_at=NULL WHERE id=?", row.getValue()))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void canonicalCodeUniquenessAppliesToEveryCatalog() {
    flyway(null, "classpath:db/migration").migrate();
    var first = createCatalogRecords("FIRST_");
    var second = createCatalogRecords("SECOND_");
    for (var row : first.entrySet()) {
      String code = jdbc.queryForObject("SELECT code FROM " + row.getKey() + " WHERE id=?", String.class, row.getValue());
      assertThatThrownBy(() -> jdbc.update(
          "UPDATE " + row.getKey() + " SET code=? WHERE id=?", " " + code.toLowerCase(java.util.Locale.ROOT) + " ", second.get(row.getKey())))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void clinicManagerOwnsThirtyMappedPermissionsAndAdminKeepsAccountAdministration() {
    flyway(null, "classpath:db/migration").migrate();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code LIKE 'CATALOG_%' AND http_method IS NOT NULL AND endpoint IS NOT NULL", Integer.class)).isEqualTo(30);
    assertThat(catalogGrantCount("CLINIC_MANAGER")).isEqualTo(30);
    assertThat(catalogGrantCount("ADMINISTRATOR")).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.code='ADMINISTRATOR' AND p.code IN ('USER_ACCOUNT_CREATE','USER_ROLE_ASSIGN','STAFF_MEMBER_VIEW','ROLE_VIEW')", Integer.class)).isEqualTo(4);
  }

  @Test
  void endpointConflictRollsBackCatalogSchemaAndPermissions() {
    jdbc.update("INSERT INTO permissions(code,name,description,http_method,endpoint,module) VALUES ('CONFLICT_TEST','Conflict','Synthetic','GET','/api/v1/catalog/management/departments','Clinic Master Data')");
    assertThatThrownBy(() -> flyway(null, "classpath:db/migration").migrate())
        .hasStackTraceContaining("uq_permissions_http_method_endpoint");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='departments' AND column_name='row_version'", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND indexname='uq_departments_code_canonical'", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code LIKE 'CATALOG_%'", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class)).isEqualTo(6);
    jdbc.update("DELETE FROM permissions WHERE code='CONFLICT_TEST'");
    assertThat(flyway(null, "classpath:db/migration").migrate().migrationsExecuted).isEqualTo(3);
  }

  @Test
  void localSeedAndCallbackGrantTestAccessWithoutGrantingCatalogToAdmin() {
    flyway(null, "classpath:db/migration", "classpath:db/local").migrate();
    for (String role : List.of("TEST", "CLINIC_MANAGER")) {
      assertThat(catalogGrantCount(role)).isEqualTo(30);
    }
    assertThat(catalogGrantCount("ADMINISTRATOR")).isZero();
    assertThat(flyway(null, "classpath:db/migration", "classpath:db/local").migrate().migrationsExecuted).isZero();
  }

  private int catalogGrantCount(String role) {
    return jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.code=? AND p.code LIKE 'CATALOG_%'", Integer.class, role);
  }

  private Map<String, UUID> createCatalogRecords(String prefix) {
    UUID department = jdbc.queryForObject("INSERT INTO departments(code,name,department_type) VALUES (?,'Synthetic','CLINICAL') RETURNING id", UUID.class, prefix + "D");
    UUID specialty = jdbc.queryForObject("INSERT INTO specialties(code,name) VALUES (?,'Synthetic') RETURNING id", UUID.class, prefix + "SP");
    UUID room = jdbc.queryForObject("INSERT INTO rooms(code,name,department_id,room_type) VALUES (?,'Synthetic',?,'EXAMINATION') RETURNING id", UUID.class, prefix + "R", department);
    UUID service = jdbc.queryForObject("INSERT INTO services(code,name,performing_department_id,specialty_id,service_type,unit_price) VALUES (?,'Synthetic',?,?,'CONSULTATION',150000) RETURNING id", UUID.class, prefix + "S", department, specialty);
    UUID medicine = jdbc.queryForObject("INSERT INTO medicines(code,generic_name,strength,dosage_form,unit) VALUES (?,'Synthetic','500mg','TABLET','VIEN') RETURNING id", UUID.class, prefix + "M");
    return Map.of("departments",department,"specialties",specialty,"rooms",room,"services",service,"medicines",medicine);
  }
}
