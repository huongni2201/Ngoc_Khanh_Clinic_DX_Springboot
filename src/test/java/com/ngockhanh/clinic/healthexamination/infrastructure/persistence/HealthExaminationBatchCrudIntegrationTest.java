package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ngockhanh.clinic.healthexamination.application.command.*;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.usecase.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationBatchParticipantMyBatisMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.HealthExaminationImportJobMyBatisMapper;
import java.math.BigDecimal;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = HealthExaminationBatchCrudIntegrationTest.BatchTestConfiguration.class)
class HealthExaminationBatchCrudIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = {
        "com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.document.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.shared.infrastructure.persistence.mapper"
      })
  @org.springframework.context.annotation.Import({
    CreateHealthExaminationBatchUseCase.class,
    UpdateHealthExaminationBatchUseCase.class,
    GetHealthExaminationBatchUseCase.class,
    ListHealthExaminationBatchUseCase.class,
    DeleteHealthExaminationBatchUseCase.class,
    BatchDraftEditor.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationBatchRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisOrganizationRepository.class,
    com.ngockhanh.clinic.catalog.infrastructure.persistence.repository.MyBatisServiceCatalogQuery
        .class,
    com.ngockhanh.clinic.document.infrastructure.persistence.repository
        .MyBatisMasterHealthExaminationTemplateQuery.class,
    com.ngockhanh.clinic.shared.infrastructure.persistence.repository.MyBatisAuditWriter.class,
    com.ngockhanh.clinic.shared.infrastructure.id.UuidV7Generator.class
  })
  static class BatchTestConfiguration {}

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired CreateHealthExaminationBatchUseCase create;
  @Autowired UpdateHealthExaminationBatchUseCase update;
  @Autowired GetHealthExaminationBatchUseCase get;
  @Autowired DeleteHealthExaminationBatchUseCase delete;
  @Autowired ListHealthExaminationBatchUseCase list;
  @Autowired HealthExaminationBatchRepository batches;
  @Autowired HealthExaminationBatchParticipantMyBatisMapper roster;
  @Autowired HealthExaminationImportJobMyBatisMapper importJobs;

  @Autowired
  com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper
          .ImportAttachmentMetadataMapper
      sourceMetadata;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.ngockhanh.clinic.shared.audit.AuditWriter audit;

  UUID org, actor, service, template, attachment;

  @Test
  void localMockActorFixtureSatisfiesTheCreatorForeignKey() {
    Flyway.configure()
        .dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
        .locations("classpath:db/migration", "classpath:db/local")
        .load()
        .migrate();
    UUID localActor = UUID.fromString("01990000-0000-7000-8000-000000000001");
    var result =
        create.execute(
            org, new CreateHealthExaminationBatchCommand(localActor, config("LOCAL-ACTOR", "10")));
    assertThat(result.createdBy()).isEqualTo(localActor);
  }

  @Test
  void auditFailureRollsBackHeaderAndAllServices() {
    doThrow(new IllegalStateException("audit failure"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());
    assertThatThrownBy(
            () ->
                create.execute(
                    org,
                    new CreateHealthExaminationBatchCommand(actor, config("AUDIT-FAIL", "10"))))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.health_examination_batches", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.health_examination_batch_services", Integer.class))
        .isZero();
  }

  @Test
  void batchReferenceLookupsAreScopedToOrganization() {
    var batch =
        create.execute(org, new CreateHealthExaminationBatchCommand(actor, config("SCOPED", "10")));
    AggregateId batchId = AggregateId.of(batch.id());
    AggregateId organizationId = AggregateId.of(org);

    assertThat(batches.findByIdAndOrganizationId(batchId, organizationId)).isPresent();
    assertThat(batches.findByIdAndOrganizationId(batchId, AggregateId.of(UUID.randomUUID())))
        .isEmpty();
    assertThat(batches.findByIdAndOrganizationIdForUpdate(batchId, organizationId)).isPresent();
  }

  @Test
  void importJobLookupsAreScopedToBatch() {
    var batch =
        create.execute(
            org, new CreateHealthExaminationBatchCommand(actor, config("JOB-SCOPE", "10")));
    var otherBatch =
        create.execute(
            org, new CreateHealthExaminationBatchCommand(actor, config("OTHER-JOB", "10")));
    UUID importId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.health_examination_import_jobs"
            + "(id,health_examination_batch_id,import_type,source_file_attachment_id,status,created_by_user_id,created_at)"
            + " VALUES (?,?,'PARTICIPANT_LIST',?,'UPLOADED',?,CURRENT_TIMESTAMP)",
        importId,
        batch.id(),
        attachment,
        actor);

    assertThat(importJobs.findJobByIdAndBatchId(importId, batch.id())).isNotNull();
    assertThat(importJobs.findJobByIdAndBatchId(importId, otherBatch.id())).isNull();
    assertThat(importJobs.findJobByIdAndBatchIdForUpdate(importId, batch.id())).isNotNull();

    insertImportRow(importId, 3, "First Person", "012345678901", "CREATE", "[]");
    insertImportRow(
        importId, 8, "Second Person", "012345678902", "UPDATE", "[\"OPTIONAL_FIELDS_MISSING\"]");

    assertThat(importJobs.countRowsByJobId(importId, "VALID")).isEqualTo(2);
    assertThat(importJobs.findRowsPage(importId, "VALID", 1, 1))
        .extracting(row -> row.rowNumber())
        .containsExactly(8);
    assertThat(importJobs.countRowsByJobId(importId, "WARNING")).isEqualTo(1);
    assertThat(importJobs.findRowsPage(importId, "CREATE", 0, 10))
        .extracting(row -> row.rowNumber())
        .containsExactly(3);
  }

  private void insertImportRow(
      UUID jobId,
      int rowNumber,
      String name,
      String identificationNumber,
      String action,
      String warningsJson) {
    String payload =
        "{\"fullName\":\""
            + name
            + "\",\"dateOfBirth\":\"1990-01-01\","
            + "\"sex\":\"MALE\",\"identificationNumber\":\""
            + identificationNumber
            + "\","
            + "\"warningCodes\":"
            + warningsJson
            + ",\"appliedAction\":\""
            + action
            + "\"}";
    jdbc.update(
        "INSERT INTO public.health_examination_import_rows "
            + "(id,health_examination_import_job_id,row_number,validation_status,error_codes_json,normalized_payload_json) "
            + "VALUES (?,?,?,'VALID','[]',?)",
        UUID.randomUUID(),
        jobId,
        rowNumber,
        payload);
  }

  @Test
  void addsRemovesServicesAndRollsBackConflictingUpdate() {
    var first =
        create.execute(org, new CreateHealthExaminationBatchCommand(actor, config("B1", "10")));
    var second =
        create.execute(org, new CreateHealthExaminationBatchCommand(actor, config("B2", "20")));
    UUID addedService = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO"
            + " public.services(id,service_code,service_name,service_type,health_examination_eligible,created_at,updated_at)"
            + " VALUES (?,'S2','Second exam','EXAM',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        addedService);
    var original = config("B1", "11");
    var desired =
        new BatchConfigurationCommand(
            original.batchCode(),
            original.batchName(),
            null,
            null,
            null,
            null,
            "CLINIC",
            "Clinic",
            null,
            List.of(
                original.services().getFirst(),
                new BatchConfigurationCommand.ServicePrice(addedService, new BigDecimal("30"))));
    var expanded = update.execute(org, first.id(), desired, actor);
    assertThat(expanded.services()).hasSize(2);
    assertThat(expanded.services().getFirst().id()).isEqualTo(first.services().getFirst().id());
    assertThatThrownBy(() -> update.execute(org, first.id(), config("B2", "99"), actor))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    var restored = get.execute(org, first.id());
    assertThat(restored.batchCode()).isEqualTo("B1");
    assertThat(restored.services()).hasSize(2);
    assertThat(restored.services().getFirst().negotiatedUnitPrice()).isEqualByComparingTo("11");
    var shrunk = update.execute(org, first.id(), original, actor);
    assertThat(shrunk.services()).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.health_examination_batch_services WHERE"
                    + " health_examination_batch_id=?",
                Integer.class,
                first.id()))
        .isEqualTo(1);
    assertThat(get.execute(org, second.id()).services().getFirst().negotiatedUnitPrice())
        .isEqualByComparingTo("20");
  }

  @Test
  void bulkRosterUpdateAcceptsAnAbsentIdentificationIssueDate() {
    var batch =
        create.execute(org, new CreateHealthExaminationBatchCommand(actor, config("ROSTER", "10")));
    UUID participantId = UUID.randomUUID();
    UUID membershipId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO public.health_examination_participants
          (id, organization_id, participant_code, identification_number, full_name,
           date_of_birth, sex, created_at, updated_at)
        VALUES (?, ?, 'TEST-PARTICIPANT', '012345678901', 'Synthetic Participant',
                DATE '1990-01-01', 'MALE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        participantId,
        org);
    jdbc.update(
        """
        INSERT INTO public.health_examination_batch_participants
          (id, health_examination_batch_id, health_examination_participant_id,
           participant_code_snapshot, full_name_snapshot, date_of_birth_snapshot,
           sex_snapshot, identification_number_snapshot, created_at)
        VALUES (?, ?, ?, 'TEST-PARTICIPANT', 'Synthetic Participant', DATE '1990-01-01',
                'MALE', '012345678901', CURRENT_TIMESTAMP)
        """,
        membershipId,
        batch.id(),
        participantId);
    var original = roster.findById(membershipId);

    assertThat(roster.updateRosterSnapshots(batch.id(), List.of(original))).isEqualTo(1);

    var restored = roster.findById(membershipId);
    assertThat(restored.identificationNumberIssueDateSnapshot()).isNull();
    assertThat(restored.fullNameSnapshot()).isEqualTo("Synthetic Participant");
    assertThat(restored.healthExaminationParticipantId()).isEqualTo(participantId);
  }

  @BeforeEach
  void fixture() {
    jdbc.execute(
        "TRUNCATE public.organizations,public.staff,public.document_templates,public.services"
            + " CASCADE");
    org = UUID.randomUUID();
    actor = UUID.randomUUID();
    service = UUID.randomUUID();
    template = UUID.randomUUID();
    attachment = UUID.randomUUID();
    UUID staff = UUID.randomUUID(), version = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.staff(id,staff_code,full_name,staff_type) VALUES (?,'TEST_ACTOR','Test"
            + " actor','ADMIN')",
        staff);
    jdbc.update(
        "INSERT INTO"
            + " public.users(id,principal_type,staff_id,status,created_at)"
            + " VALUES (?,'STAFF',?,'ACTIVE',CURRENT_TIMESTAMP)",
        actor,
        staff);
    jdbc.update(
        "INSERT INTO"
            + " public.organizations(id,organization_name,contact_name,contact_phone,created_at,updated_at)"
            + " VALUES (?,'Org','Contact','0900',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        org);
    jdbc.update(
        "INSERT INTO"
            + " public.services(id,service_code,service_name,service_type,health_examination_eligible,created_at,updated_at)"
            + " VALUES (?,'S1','Exam','EXAM',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        service);
    jdbc.update(
        "INSERT INTO"
            + " public.document_templates(id,template_code,template_name,template_type,is_master_health_examination_form,created_at,updated_at)"
            + " VALUES"
            + " (?,'FORM03','Master','HEALTH_EXAMINATION',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        template);
    jdbc.update(
        "INSERT INTO"
            + " public.file_attachments(id,entity_type,entity_id,document_type,storage_provider,storage_key,file_name,mime_type,size_bytes,created_at)"
            + " VALUES"
            + " (?,'TEMPLATE',?,'TEMPLATE','TEST','fixture','form.html','text/html',1,CURRENT_TIMESTAMP)",
        attachment,
        template);
    jdbc.update(
        "INSERT INTO"
            + " public.document_template_versions(id,document_template_id,version_number,source_file_attachment_id,paper_size,render_mode,renderer_type,schema_json,effective_from,created_by_user_id,created_at)"
            + " VALUES (?,?,1,?,'A4','HTML','HTML','{}',CURRENT_TIMESTAMP-INTERVAL '1"
            + " day',?,CURRENT_TIMESTAMP)",
        version,
        template,
        attachment,
        actor);
  }

  BatchConfigurationCommand config(String code, String price) {
    return new BatchConfigurationCommand(
        code,
        "Campaign%_",
        java.time.LocalDate.of(2026, 9, 30),
        null,
        null,
        null,
        "COMPANY",
        "Site",
        null,
        List.of(new BatchConfigurationCommand.ServicePrice(service, new BigDecimal(price))));
  }

  @Test
  void roundTripsSnapshotsAtomicUpdatesAndIdempotentSoftDelete() {
    var first =
        create.execute(org, new CreateHealthExaminationBatchCommand(actor, config("B1", "123.00")));
    assertThat(first.createdBy()).isEqualTo(actor);
    assertThat(first.services().getFirst().negotiatedUnitPrice()).isEqualByComparingTo("123.00");
    assertThat(batches.findDetails(UUID.randomUUID(), first.id(), false)).isEmpty();
    var row = first.services().getFirst().id();
    jdbc.update("UPDATE public.services SET service_name='Renamed' WHERE id=?", service);
    var changed = update.execute(org, first.id(), config("B2", "150.00"), actor);
    assertThat(changed.services().getFirst().id()).isEqualTo(row);
    assertThat(changed.services().getFirst().serviceName()).isEqualTo("Exam");
    assertThat(
            list.execute(org, new HealthExaminationBatchListQuery(1, 10, "%_", "batchCode", "DESC"))
                .totalElements())
        .isEqualTo(1);
    delete.execute(org, first.id(), actor);
    delete.execute(org, first.id(), actor);
    assertThat(batches.findDetails(org, first.id(), false)).isEmpty();
    assertThat(batches.findDetailsIncludingDeleted(org, first.id(), false))
        .get()
        .extracting(details -> details.batch().status().name())
        .isEqualTo("DELETED");
    assertThatThrownBy(() -> get.execute(org, first.id()))
        .isInstanceOf(com.ngockhanh.clinic.shared.exception.ResourceNotFoundException.class);
    assertThatThrownBy(() -> update.execute(org, first.id(), config("B3", "200"), actor))
        .isInstanceOf(com.ngockhanh.clinic.shared.exception.ResourceNotFoundException.class);
    assertThat(
            list.execute(org, new HealthExaminationBatchListQuery(1, 10, null, "id", "ASC"))
                .totalElements())
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.health_examination_batch_services WHERE id=?",
                Integer.class,
                row))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.audit_logs WHERE entity_id=?",
                Integer.class,
                first.id().toString()))
        .isEqualTo(3);
    assertThatThrownBy(
            () ->
                create.execute(
                    org, new CreateHealthExaminationBatchCommand(actor, config("B2", "1"))))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  }

  @Test
  void readsAnImportSourceOnlyForItsOwningJob() {
    UUID source = UUID.randomUUID();
    UUID job = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.file_attachments"
            + " (id,entity_type,entity_id,document_type,storage_provider,storage_key,file_name,mime_type,size_bytes,created_at)"
            + " VALUES (?,'HEALTH_EXAMINATION_IMPORT',?,'PARTICIPANT_ROSTER_SOURCE','LOCAL_AES_GCM',"
            + " 'imports/test.gcm','roster.xlsx','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',1,CURRENT_TIMESTAMP)",
        source,
        job);
    assertThat(sourceMetadata.findByIdAndImportJobId(source, job).importJobId()).isEqualTo(job);
    assertThat(sourceMetadata.findByIdAndImportJobId(source, UUID.randomUUID())).isNull();
  }

  @Test
  void rollsBackInvalidActorAndBlocksDeleteWithImport() {
    assertThatThrownBy(
            () ->
                create.execute(
                    org,
                    new CreateHealthExaminationBatchCommand(
                        UUID.randomUUID(), config("BAD", "10"))))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.health_examination_batches", Integer.class))
        .isZero();
    var b = create.execute(org, new CreateHealthExaminationBatchCommand(actor, config("B1", "10")));
    jdbc.update(
        "INSERT INTO"
            + " public.health_examination_import_jobs(id,health_examination_batch_id,import_type,source_file_attachment_id,status,created_by_user_id,created_at)"
            + " VALUES (?,?,'PARTICIPANT_LIST',?,'UPLOADED',?,CURRENT_TIMESTAMP)",
        UUID.randomUUID(),
        b.id(),
        attachment,
        actor);
    assertThatThrownBy(() -> delete.execute(org, b.id(), actor))
        .isInstanceOf(com.ngockhanh.clinic.shared.exception.BusinessRuleException.class);
    assertThat(get.execute(org, b.id()).status()).isEqualTo("DRAFT");
  }
}
