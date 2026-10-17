package com.ngockhanh.clinic.document.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
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
 * V001 holds the background print job tables and V002 binds three permissions to three endpoints.
 */
@Testcontainers(disabledWithoutDocker = true)
class DocumentRenderJobsMigrationIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  private static JdbcTemplate jdbc;
  private static UUID account;

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
    UUID staff = UUID.randomUUID();
    account = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.staff_members(id,staff_code,full_name,status) VALUES (?,'S1','Test staff','ACTIVE')",
        staff);
    jdbc.update(
        "INSERT INTO public.accounts(id,account_type,role_id,username,password_hash,staff_member_id,status)"
            + " VALUES (?,'STAFF',(SELECT id FROM public.roles WHERE code = 'CLINIC_MANAGER'),'job-migration','x',?,'ACTIVE')",
        account,
        staff);
  }

  private static UUID job(String status, int total, int idCount, UUID key) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.document_render_jobs(id,document_type,requested_by,idempotency_key,context,"
            + "source_ids,status,total_count) VALUES (?,'T',?,?,'{}'::jsonb,?::uuid[],?,?)",
        id,
        account,
        key,
        "{"
            + String.join(
                ",",
                IntStream.range(0, idCount).mapToObj(i -> UUID.randomUUID().toString()).toList())
            + "}",
        status,
        total);
    return id;
  }

  private static UUID queued() {
    return job("QUEUED", 1, 1, UUID.randomUUID());
  }

  @Test
  void bindsThreePermissionsToThreeEndpointsForTheSameRolesAsSinglePrinting() {
    var rows =
        jdbc.queryForList(
            "SELECT code, http_method, endpoint FROM public.permissions WHERE code IN"
                + " ('PARTICIPANT_EXAMINATION_FORM_BULK_PRINT','DOCUMENT_RENDER_JOB_READ','DOCUMENT_RENDER_JOB_DOWNLOAD')"
                + " ORDER BY code");
    assertThat(rows)
        .extracting(r -> r.get("code") + " " + r.get("http_method") + " " + r.get("endpoint"))
        .containsExactly(
            "DOCUMENT_RENDER_JOB_DOWNLOAD GET /api/v1/document-render-jobs/{jobId}/parts/{partNo}",
            "DOCUMENT_RENDER_JOB_READ GET /api/v1/document-render-jobs/{jobId}",
            "PARTICIPANT_EXAMINATION_FORM_BULK_PRINT POST"
                + " /api/v1/organizations/{organizationId}/agreements/{agreementId}/participants/examination-form-print-jobs");
    for (String permission :
        List.of(
            "PARTICIPANT_EXAMINATION_FORM_BULK_PRINT",
            "DOCUMENT_RENDER_JOB_READ",
            "DOCUMENT_RENDER_JOB_DOWNLOAD"))
      assertThat(
              jdbc.queryForList(
                  "SELECT r.code FROM public.role_permissions rp JOIN public.roles r ON r.id = rp.role_id"
                      + " JOIN public.permissions p ON p.id = rp.permission_id WHERE p.code = ?",
                  String.class,
                  permission))
          .as(permission)
          .containsExactlyInAnyOrder("CLINIC_MANAGER", "NURSE", "RECEPTIONIST");
  }

  @Test
  void aJobHasAKnownStatusAndAsManyIdentifiersAsItsTotal() {
    UUID key = UUID.randomUUID();
    job("QUEUED", 2, 2, key);

    assertThatThrownBy(() -> job("DONE", 1, 1, UUID.randomUUID()))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_render_jobs_status");
    assertThatThrownBy(() -> job("QUEUED", 2, 1, UUID.randomUUID()))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_render_jobs_ids");
    assertThatThrownBy(() -> job("QUEUED", 0, 0, UUID.randomUUID()))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_render_jobs_total");
    assertThatThrownBy(() -> job("QUEUED", 5001, 5001, UUID.randomUUID()))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_render_jobs_total");
  }

  @Test
  void aLeaseExistsExactlyWhileTheJobRuns() {
    UUID id = queued();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.document_render_jobs SET status='RUNNING' WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_render_jobs_lease");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.document_render_jobs SET lock_token = ?, locked_until = now() WHERE id = ?",
                    UUID.randomUUID(),
                    id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_render_jobs_lease");
    jdbc.update(
        "UPDATE public.document_render_jobs SET status='RUNNING', lock_token = ?, locked_until = now() WHERE id = ?",
        UUID.randomUUID(),
        id);
  }

  @Test
  void progressNeverExceedsTheTotalAndAFinishedJobHasItsTimes() {
    UUID id = queued();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.document_render_jobs SET processed_count = 2 WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_render_jobs_progress");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.document_render_jobs SET status='SUCCEEDED' WHERE id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_render_jobs_finished");
    jdbc.update(
        "UPDATE public.document_render_jobs SET status='SUCCEEDED', finished_at = now(),"
            + " expires_at = now() + interval '1 day' WHERE id = ?",
        id);
  }

  @Test
  void aPartHasAPositiveNumberAndCountAndItsOwnFile() {
    UUID id = queued();
    UUID file = file("a");
    UUID other = file("b");

    assertThatThrownBy(() -> part(id, 0, file, 1))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_render_job_outputs_part");
    assertThatThrownBy(() -> part(id, 1, file, 0))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("ck_document_render_job_outputs_count");
    part(id, 1, file, 1);
    assertThatThrownBy(() -> part(id, 2, file, 1)).isInstanceOf(DataAccessException.class);
    part(id, 2, other, 1);
  }

  private static UUID file(String key) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.files(id,storage_provider,storage_key,original_filename,content_type,size_bytes,checksum)"
            + " VALUES (?,'LOCAL',?,'part.pdf','application/pdf',1,decode(repeat('00',32),'hex'))",
        id,
        "render-jobs/" + key + "-" + id + ".pdf");
    return id;
  }

  private static void part(UUID job, int partNo, UUID file, int count) {
    jdbc.update(
        "INSERT INTO public.document_render_job_outputs(job_id,part_no,file_id,record_count) VALUES (?,?,?,?)",
        job,
        partNo,
        file,
        count);
  }
}
