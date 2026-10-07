package com.ngockhanh.clinic.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class CleanSlatePostgreSqlMigrationIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("nkclinic_clean_slate")
          .withUsername("nkclinic")
          .withPassword("test-password");

  @Test
  void migratesCleanSlateIntoPublicAndEnforcesPatientAndAuditConstraints() {
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .defaultSchema("public")
            .schemas("public")
            .load();

    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();

    JdbcTemplate jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'organizations' AND column_name IN ('organization_type', 'contact_position', 'code')",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints tc JOIN information_schema.key_column_usage kcu USING (constraint_catalog, constraint_schema, constraint_name, table_name) WHERE tc.table_schema = 'public' AND tc.table_name = 'organizations' AND tc.constraint_type = 'UNIQUE' AND kcu.column_name = 'tax_code'",
                Integer.class))
        .isEqualTo(1);

    assertThat(
            jdbc.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """,
                String.class))
        .hasSize(64)
        .contains(
            "accounts",
            "staff_members",
            "health_examination_batch_participants",
            "health_examination_participant_services",
            "result_versions",
            "issued_representations",
            "audit_events");
    assertThat(
            jdbc.queryForList(
                """
                SELECT nspname FROM pg_namespace
                WHERE nspname NOT LIKE 'pg_%' AND nspname <> 'information_schema'
                """,
                String.class))
        .containsExactly("public");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM pg_constraint c
                JOIN pg_class source ON source.oid = c.conrelid
                JOIN pg_namespace source_schema ON source_schema.oid = source.relnamespace
                JOIN pg_class target ON target.oid = c.confrelid
                JOIN pg_namespace target_schema ON target_schema.oid = target.relnamespace
                WHERE c.contype = 'f' AND source_schema.nspname = 'public'
                  AND target_schema.nspname <> 'public'
                """,
                Integer.class))
        .isZero();


    UUID patientId =
        jdbc.queryForObject(
            """
            INSERT INTO public.patients
              (patient_code, full_name, date_of_birth, sex, identification_number, status)
            VALUES ('TEST-001', 'Test Patient', DATE '2000-01-01', 'MALE', '000000000001', 'ACTIVE')
            RETURNING id
            """,
            UUID.class);
    assertThat(patientId).isNotNull();
    assertThat(patientId.version()).isEqualTo(7);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO public.patients
                      (patient_code, full_name, date_of_birth, sex, identification_number, status)
                    VALUES ('TEST-002', 'Test Patient', DATE '2000-01-01', 'MALE', '000000000001', 'ACTIVE')
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);

    UUID auditId =
        jdbc.queryForObject(
            """
            INSERT INTO public.audit_events (action, resource_type, resource_id)
            VALUES ('PATIENT_CREATED', 'PATIENT', ?) RETURNING id
            """,
            UUID.class,
            patientId);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.audit_events SET action = 'CHANGED' WHERE id = ?", auditId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM public.audit_events WHERE id = ?", auditId))
        .isInstanceOf(DataAccessException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT action FROM public.audit_events WHERE id = ?", String.class, auditId))
        .isEqualTo("PATIENT_CREATED");
  }

  /**
   * V004 seeds the SRS Permission Matrix 4.4; V005 replaces the V003 roster codes, adds four
   * permissions for endpoints outside the matrix and stores the endpoint of 14 permissions; V006
   * moves the document and notification template permissions to the Administrator (ADR-0015).
   */
  private static void assertPermissionMatrixSeed(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForList("SELECT code FROM public.roles", String.class))
        .containsExactlyInAnyOrder(
            "PATIENT",
            "RECEPTIONIST",
            "GENERAL_PRACTITIONER",
            "DIAGNOSTIC_DOCTOR",
            "DATA_ENTRY_STAFF",
            "CLINIC_MANAGER",
            "ADMINISTRATOR");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.permissions", Integer.class))
        .isEqualTo(110);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.role_permissions", Integer.class))
        .isEqualTo(116);
    assertThat(
            jdbc.queryForList(
                """
                SELECT p.code FROM public.permissions p
                WHERE NOT EXISTS (SELECT 1 FROM public.role_permissions rp WHERE rp.permission_id = p.id)
                """,
                String.class))
        .isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.permissions WHERE code LIKE 'HEALTH\\_EXAMINATION\\_PARTICIPANT\\_%'",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForList(
                "SELECT code FROM public.permissions WHERE endpoint IS NOT NULL", String.class))
        .hasSize(14)
        .contains("ORGANIZATION_DELETE", "HEALTH_EXAMINATION_BATCH_DETAIL_VIEW", "SERVICE_CATALOG_VIEW");
    assertThat(
            jdbc.queryForObject(
                "SELECT http_method || ' ' || endpoint FROM public.permissions WHERE code = 'PARTICIPANT_IMPORT'",
                String.class))
        .isEqualTo(
            "POST /api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/imports");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.permissions (code, name, description, http_method, endpoint)"
                        + " VALUES ('TEST_SAME_ENDPOINT', 'Test', 'Test', 'GET', '/api/v1/organizations')"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.permissions (code, name, description, http_method, endpoint)"
                        + " VALUES ('TEST_BAD_METHOD', 'Test', 'Test', 'FETCH', '/api/v1/test')"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.permissions (code, name, description, http_method)"
                        + " VALUES ('TEST_NO_ENDPOINT', 'Test', 'Test', 'GET')"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForList(
                """
                SELECT DISTINCT r.code FROM public.role_permissions rp
                JOIN public.roles r ON r.id = rp.role_id
                JOIN public.permissions p ON p.id = rp.permission_id
                WHERE (p.code LIKE 'OWN\\_%') <> (r.code = 'PATIENT')
                """,
                String.class))
        .as("only PATIENT holds OWN_* permissions, and it holds nothing else")
        .isEmpty();
    assertThat(rolesGranted(jdbc, "PARTICIPANT_EXAMINATION_RECORD_VIEW"))
        .containsExactlyInAnyOrder("GENERAL_PRACTITIONER", "DATA_ENTRY_STAFF", "CLINIC_MANAGER");
    assertThat(rolesGranted(jdbc, "REPORT_BATCH_FINANCIAL_SUMMARY_EXPORT"))
        .containsExactly("CLINIC_MANAGER");
    assertThat(rolesGranted(jdbc, "AUDIT_LOG_VIEW")).containsExactly("ADMINISTRATOR");
    assertThat(rolesGranted(jdbc, "MASTER_DATA_DOCUMENT_TEMPLATE_MANAGE"))
        .containsExactly("ADMINISTRATOR");
    assertThat(rolesGranted(jdbc, "MASTER_DATA_NOTIFICATION_TEMPLATE_MANAGE"))
        .containsExactly("ADMINISTRATOR");
  }

  private static List<String> rolesGranted(JdbcTemplate jdbc, String permission) {
    return jdbc.queryForList(
        """
        SELECT r.code FROM public.role_permissions rp
        JOIN public.roles r ON r.id = rp.role_id
        JOIN public.permissions p ON p.id = rp.permission_id
        WHERE p.code = ?
        """,
        String.class,
        permission);
  }
}
