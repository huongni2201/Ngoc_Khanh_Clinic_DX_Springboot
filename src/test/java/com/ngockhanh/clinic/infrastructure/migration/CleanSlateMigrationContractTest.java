package com.ngockhanh.clinic.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CleanSlateMigrationContractTest {
  @Test
  void runtimeSqlReferencesOnlyTablesInTheFreshBaseline() throws Exception {
    String sql =
        Files.readString(
            Path.of("src/main/resources/db/migration/V001__create_clean_slate_schema.sql"));
    var tables =
        Pattern.compile("(?m)^CREATE TABLE public\\.([a-z_]+)")
            .matcher(sql)
            .results()
            .map(match -> match.group(1))
            .collect(java.util.stream.Collectors.toSet());
    var relation = Pattern.compile("(?i)\\b(?:FROM|JOIN|UPDATE|INTO)\\s+public\\.([a-z_]+)");
    for (Path directory :
        java.util.List.of(Path.of("src/main/resources/mapper"), Path.of("src/main/java"))) {
      try (var files = Files.walk(directory)) {
        for (Path file :
            files
                .filter(
                    path -> path.toString().endsWith(".xml") || path.toString().endsWith(".java"))
                .toList()) {
          for (var match : relation.matcher(Files.readString(file)).results().toList()) {
            assertThat(tables).as("SQL table referenced in %s", file).contains(match.group(1));
          }
        }
      }
    }
  }

  @Test
  void freshBaselineUsesTheApprovedPublicSchemaAndNoLegacyVersionTriggers() throws Exception {
    Path directory = Path.of("src/main/resources/db/migration");
    try (var files = Files.list(directory)) {
      assertThat(
              files
                  .filter(path -> path.toString().endsWith(".sql"))
                  .map(path -> path.getFileName().toString())
                  .toList())
          .containsExactlyInAnyOrder(
              "V001__create_clean_slate_schema.sql",
              "V002__add_health_examination_batch_soft_delete.sql",
              "V003__add_participant_roster_permissions.sql",
              "V004__add_participant_manage_permission.sql",
              "V005__add_examination_detail_and_report_permissions.sql",
              "V006__add_service_reconciliation_import_type.sql");
    }
    String sql = Files.readString(directory.resolve("V001__create_clean_slate_schema.sql"));
    var tables =
        Pattern.compile("(?m)^CREATE TABLE public\\.([a-z_]+)")
            .matcher(sql)
            .results()
            .map(match -> match.group(1))
            .toList();
    assertThat(tables)
        .hasSize(64)
        .doesNotHaveDuplicates()
        .contains(
            "accounts",
            "staff_members",
            "import_jobs",
            "import_rows",
            "health_examination_batch_days",
            "health_examination_record_snapshots",
            "health_examination_record_versions",
            "result_releases",
            "issued_representations")
        .doesNotContain(
            "users",
            "staff",
            "health_examination_participants",
            "service_prices",
            "generated_documents",
            "audit_logs");
    assertThat(sql)
        .contains(
            "numeric(14,2)",
            "timestamptz(3)",
            "DEFAULT uuidv7()",
            "row_version bigint NOT NULL DEFAULT 0")
        .doesNotContain("CREATE SCHEMA", "numeric(18,2)", "NEW.row_version := OLD.row_version + 1");
    assertThat(sql)
        .contains(
            "UNIQUE (batch_id, identification_number)",
            "FOREIGN KEY (batch_id, batch_day_id)",
            "FOREIGN KEY (batch_id, batch_service_id)");
  }

  @Test
  void examinationDetailMigrationsOnlyAddPermissionsAndAnImportType() throws Exception {
    Path directory = Path.of("src/main/resources/db/migration");
    String permissions =
        Files.readString(
            directory.resolve("V005__add_examination_detail_and_report_permissions.sql"));
    assertThat(permissions)
        .contains(
            "'HEALTH_EXAMINATION_SERVICE_READ'",
            "'HEALTH_EXAMINATION_SERVICE_RECONCILE'",
            "'HEALTH_EXAMINATION_REPORT_READ'",
            "WHERE role.code IN ('ADMIN', 'CLINIC_MANAGER')")
        .doesNotContain("DROP ", "DELETE ", "TRUNCATE ", "ALTER TABLE");

    String importType =
        Files.readString(directory.resolve("V006__add_service_reconciliation_import_type.sql"));
    assertThat(importType)
        .contains(
            "DROP CONSTRAINT ck_import_jobs_type",
            "'ORGANIZATION_PARTICIPANT'",
            "'HEALTH_EXAMINATION_RESULT'",
            "'HEALTH_EXAMINATION_SERVICE_RECONCILIATION'")
        .doesNotContain("DROP TABLE", "DELETE ", "TRUNCATE ");
  }
}
