package com.ngockhanh.clinic.document.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V001 gives a template version a lifecycle (DRAFT to PUBLISHED or DISCARDED) while keeping its
 * content immutable; V002 binds the print permission to one endpoint.
 */
@Testcontainers(disabledWithoutDocker = true)
class DocumentTemplateLifecycleMigrationIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  private static final String PAST = "now() - interval '2 days'";
  private static final String FUTURE = "now() + interval '10 days'";
  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
  }

  @Test
  void aTemplateTypeHasOneDefaultThatIsActive() {
    String type = uniqueType();
    template(type, true, true);
    template(type, true, false);

    assertThatThrownBy(() -> template(type, true, true))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ux_document_templates_default");
    assertThatThrownBy(() -> template(uniqueType(), false, true))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_templates_default_active");
  }

  @Test
  void aDraftHasNoPeriodAndAPublishedVersionHasOne() {
    UUID template = template(uniqueType(), true, true);

    version(template, 1, file("LOCAL"), "PUBLISHED", PAST);
    version(template, 2, file("LOCAL"), "DRAFT", null);

    // A template of its own: the period trigger would otherwise report an overlap first.
    UUID alone = template(uniqueType(), true, true);
    assertThatThrownBy(() -> version(alone, 1, file("LOCAL"), "PUBLISHED", null))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_template_versions_published");
    assertThatThrownBy(() -> version(template, 4, file("LOCAL"), "DRAFT", FUTURE))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_template_versions_draft_clean");
  }

  @Test
  void contentAndLayoutOfAVersionNeverChange() {
    UUID template = template(uniqueType(), true, true);
    UUID draft = version(template, 1, file("LOCAL"), "DRAFT", null);
    UUID otherFile = file("LOCAL");

    jdbc.update(
        "UPDATE public.template_versions SET note = 'edited', validation_report = '{}'::jsonb WHERE id = ?",
        draft);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.template_versions SET file_id = ? WHERE id = ?",
                    otherFile,
                    draft))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("immutable");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.template_versions SET paper_size = 'A4' WHERE id = ?", draft))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("immutable");
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM public.template_versions WHERE id = ?", draft))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("cannot be deleted");
  }

  @Test
  void aNewVersionTakesOverAtTheInstantTheOldOneRetires() {
    UUID template = template(uniqueType(), true, true);
    UUID first = version(template, 1, file("LOCAL"), "PUBLISHED", PAST);
    UUID second = version(template, 2, file("LOCAL"), "DRAFT", null);

    assertThatThrownBy(() -> publish(second, FUTURE))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("overlap");

    jdbc.update(
        "UPDATE public.template_versions SET retired_at = " + FUTURE + " WHERE id = ?", first);
    publish(second, FUTURE);

    assertThatThrownBy(
            () ->
                jdbc.update("UPDATE public.template_versions SET note = 'x' WHERE id = ?", second))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("can only be retired");
  }

  @Test
  void aScheduledVersionCanBeCancelledButAnEffectiveOneCannot() {
    UUID template = template(uniqueType(), true, true);
    UUID first = version(template, 1, file("LOCAL"), "PUBLISHED", PAST);
    UUID second = version(template, 2, file("LOCAL"), "DRAFT", null);
    jdbc.update(
        "UPDATE public.template_versions SET retired_at = " + FUTURE + " WHERE id = ?", first);
    publish(second, FUTURE);

    jdbc.update("UPDATE public.template_versions SET status = 'DISCARDED' WHERE id = ?", second);
    jdbc.update("UPDATE public.template_versions SET retired_at = NULL WHERE id = ?", first);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.template_versions SET status = 'DISCARDED' WHERE id = ?", first))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("not yet effective");
    assertThatThrownBy(
            () ->
                jdbc.update("UPDATE public.template_versions SET note = 'z' WHERE id = ?", second))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("discarded");
  }

  @Test
  void aRetirementThatTookEffectIsHistory() {
    UUID template = template(uniqueType(), true, true);
    UUID version =
        jdbc.queryForObject(
            "INSERT INTO public.template_versions(template_id,version_no,file_id,paper_size,orientation,"
                + "render_mode,status,contract_version,active_from,retired_at,published_at) VALUES "
                + "(?,1,?,'A3','LANDSCAPE','MASTER_FORM','PUBLISHED',1,now() - interval '30 days',"
                + "now() - interval '1 day',now() - interval '30 days') RETURNING id",
            UUID.class,
            template,
            file("LOCAL"));

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.template_versions SET retired_at = NULL WHERE id = ?", version))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("already retired");
  }

  @Test
  void aFileUsedByAVersionCannotBeChangedOrDeleted() {
    UUID template = template(uniqueType(), true, true);
    UUID source = file("LOCAL");
    UUID preview = file("LOCAL");
    UUID version = version(template, 1, source, "DRAFT", null);
    jdbc.update(
        "UPDATE public.template_versions SET preview_file_id = ? WHERE id = ?", preview, version);

    assertThatThrownBy(() -> jdbc.update("DELETE FROM public.files WHERE id = ?", source))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("referenced");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM public.files WHERE id = ?", preview))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("referenced");
  }

  @Test
  void thePrintPermissionProtectsOneEndpointAndIsGrantedToThreeRoles() {
    assertThat(
            jdbc.queryForMap(
                "SELECT http_method, endpoint FROM public.permissions WHERE code = 'PARTICIPANT_EXAMINATION_FORM_PRINT'"))
        .containsEntry("http_method", "GET")
        .containsEntry(
            "endpoint",
            "/api/v1/organizations/{organizationId}/agreements/{agreementId}/participants/{participantId}/examination-form");
    assertThat(
            jdbc.queryForList(
                """
                SELECT r.code FROM public.role_permissions rp
                JOIN public.roles r ON r.id = rp.role_id
                JOIN public.permissions p ON p.id = rp.permission_id
                WHERE p.code = 'PARTICIPANT_EXAMINATION_FORM_PRINT'
                """,
                String.class))
        .containsExactlyInAnyOrder("CLINIC_MANAGER", "NURSE", "RECEPTIONIST");
  }

  private static void publish(UUID version, String activeFrom) {
    jdbc.update(
        "UPDATE public.template_versions SET status = 'PUBLISHED', active_from = "
            + activeFrom
            + ", published_at = now() WHERE id = ?",
        version);
  }

  private static String uniqueType() {
    return "TEST_TYPE_" + SEQUENCE.incrementAndGet();
  }

  private static UUID template(String type, boolean active, boolean isDefault) {
    int n = SEQUENCE.incrementAndGet();
    return jdbc.queryForObject(
        "INSERT INTO public.templates(code, name, template_type, active, is_default) VALUES (?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        "TEST_TEMPLATE_" + n,
        "Test template " + n,
        type,
        active,
        isDefault);
  }

  private static UUID file(String provider) {
    int n = SEQUENCE.incrementAndGet();
    return jdbc.queryForObject(
        "INSERT INTO public.files(storage_provider, storage_key, original_filename, content_type, size_bytes, checksum) "
            + "VALUES (?, ?, 'f.docx', 'application/octet-stream', 1, '\\x00') RETURNING id",
        UUID.class,
        provider,
        "test/" + n);
  }

  /** Inserts a version; {@code activeFrom} is an SQL expression, or null for a draft. */
  private static UUID version(
      UUID template, int number, UUID file, String status, String activeFrom) {
    String published = activeFrom == null ? "NULL" : "now()";
    return jdbc.queryForObject(
        "INSERT INTO public.template_versions(template_id, version_no, file_id, paper_size, orientation, "
            + "render_mode, status, contract_version, active_from, published_at) "
            + "VALUES (?, ?, ?, 'A3', 'LANDSCAPE', 'MASTER_FORM', ?, 1, "
            + (activeFrom == null ? "NULL" : activeFrom)
            + ", "
            + published
            + ") RETURNING id",
        UUID.class,
        template,
        number,
        file,
        status);
  }
}
