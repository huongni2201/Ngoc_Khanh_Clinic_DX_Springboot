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
        Files.readString(Path.of("src/main/resources/db/migration/V001__create_schema.sql"));
    var tables =
        Pattern.compile("(?m)^\\h*CREATE TABLE public\\.([a-z_]+)")
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
  void schemaAndSeedMigrationsKeepSeparateResponsibilities() throws Exception {
    Path directory = Path.of("src/main/resources/db/migration");
    String schema = Files.readString(directory.resolve("V001__create_schema.sql"));
    String seed = Files.readString(directory.resolve("V002__seed_roles_and_permissions.sql"));
    String catalog =
        Files.readString(directory.resolve("V003__seed_paraclinical_service_catalog.sql"));
    assertThat(schema).doesNotContain("INSERT INTO");
    assertThat(seed)
        .contains(
            "INSERT INTO public.roles",
            "INSERT INTO public.permissions",
            "INSERT INTO public.role_permissions")
        .doesNotContain(
            "CREATE TABLE", "ALTER TABLE", "CREATE INDEX", "CREATE FUNCTION", "CREATE TRIGGER");
    assertThat(catalog)
        .contains(
            "INSERT INTO public.departments",
            "INSERT INTO public.rooms",
            "INSERT INTO public.services")
        .doesNotContain(
            "CREATE TABLE",
            "ALTER TABLE",
            "CREATE INDEX",
            "CREATE FUNCTION",
            "CREATE TRIGGER",
            "UPDATE public.",
            "DROP ",
            "CLS_");
  }
}
