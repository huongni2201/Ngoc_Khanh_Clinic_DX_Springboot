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
    Flyway schemaFlyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target("1")
            .load();
    assertThat(schemaFlyway.migrate().migrationsExecuted).isEqualTo(1);
    JdbcTemplate jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    for (String table :
        List.of("roles", "permissions", "role_permissions", "departments", "rooms", "services")) {
      assertThat(jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class))
          .as("V001 creates %s without seed data", table)
          .isZero();
    }
    assertThat(
            jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'health_examination_batch_participants' AND column_name IN ('identification_issue_date', 'identification_issue_place', 'ethnicity', 'address', 'workplace', 'note') AND is_nullable = 'YES'",
                String.class))
        .containsExactlyInAnyOrder(
            "identification_issue_date",
            "identification_issue_place",
            "ethnicity",
            "address",
            "workplace",
            "note");
    assertThat(
            jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'organizations' AND column_name = 'phone'",
                String.class))
        .isEqualTo("YES");
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_functiondef('public.trg_validate_account_role()'::regprocedure)",
                String.class))
        .contains("IF v_role_code = 'PATIENT' THEN");
    assertThat(
            jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'health_examination_batches' AND column_name = 'deleted_at'",
                String.class))
        .isEqualTo("timestamp with time zone");
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'public.import_jobs'::regclass AND conname = 'ck_import_jobs_type'",
                String.class))
        .contains("HEALTH_EXAMINATION_SERVICE_RECONCILIATION");
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .defaultSchema("public")
            .schemas("public")
            .load();

    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
    assertThat(flyway.info().pending()).isEmpty();
    flyway.validate();
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    assertThat(
            jdbc.queryForList(
                "SELECT version FROM public.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class))
        .containsExactly("001", "002", "003");
    assertPermissionMatrixSeed(jdbc);
    assertThat(
            jdbc.queryForObject(
                "SELECT code FROM public.services WHERE code = 'CLS58718'", String.class))
        .isEqualTo("CLS58718");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.services WHERE code LIKE 'CLS\\_%' OR row_version <> 0",
                Integer.class))
        .isZero();

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
   * V002 seeds the SRS Permission Matrix 4.4 (ADR-0015) and current endpoint permissions. The two
   * legacy roster permissions are superseded by the matrix codes and stay ungranted.
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
        .isEqualTo(114);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.role_permissions", Integer.class))
        .isEqualTo(118);
    assertThat(
            jdbc.queryForList(
                """
                SELECT p.code FROM public.permissions p
                WHERE NOT EXISTS (SELECT 1 FROM public.role_permissions rp WHERE rp.permission_id = p.id)
                """,
                String.class))
        .containsExactlyInAnyOrder(
            "HEALTH_EXAMINATION_PARTICIPANT_READ", "HEALTH_EXAMINATION_PARTICIPANT_IMPORT");
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
    for (String permission :
        List.of(
            "PARTICIPANT_CREATE",
            "PARTICIPANT_REACTIVATE",
            "HEALTH_EXAMINATION_PARTICIPANT_MANAGE",
            "HEALTH_EXAMINATION_SERVICE_READ",
            "HEALTH_EXAMINATION_SERVICE_RECONCILE",
            "HEALTH_EXAMINATION_REPORT_READ")) {
      assertThat(rolesGranted(jdbc, permission)).containsExactly("CLINIC_MANAGER");
    }
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
