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
              "V004__seed_access_control_roles_and_permissions.sql");
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
}
