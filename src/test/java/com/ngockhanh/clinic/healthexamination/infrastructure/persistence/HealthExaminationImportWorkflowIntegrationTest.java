package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.healthexamination.application.usecase.*;
import com.ngockhanh.clinic.healthexamination.domain.entity.*;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.*;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = HealthExaminationImportWorkflowIntegrationTest.Config.class)
class HealthExaminationImportWorkflowIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = {
        "com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.integration.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.audit.infrastructure.persistence.mapper"
      })
  @org.springframework.context.annotation.Import({
    StoreValidatedParticipantImportUseCase.class,
    ValidateParticipantImportUseCase.class,
    ConfirmParticipantImportUseCase.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationBatchRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationBatchParticipantRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationImportJobRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisParticipantImportAuditWriter.class,
    com.ngockhanh.clinic.integration.infrastructure.persistence.repository.MyBatisImportStore.class,
    com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter.class
  })
  static class Config {
    @org.springframework.context.annotation.Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired StoreValidatedParticipantImportUseCase stage;
  @Autowired ValidateParticipantImportUseCase preview;
  @Autowired ConfirmParticipantImportUseCase confirm;
  @Autowired HealthExaminationBatchParticipantRepository participants;
  @Autowired org.springframework.transaction.PlatformTransactionManager tx;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.ngockhanh.clinic.audit.application.port.AuditWriter audit;

  UUID org, batch, actor, day1, day2, batchService;

  @BeforeEach
  void fixture() {
    jdbc.execute(
        "TRUNCATE public.organizations,public.staff_members,public.services,public.departments CASCADE");
    org = UUID.randomUUID();
    batch = UUID.randomUUID();
    actor = UUID.randomUUID();
    day1 = UUID.randomUUID();
    day2 = UUID.randomUUID();
    batchService = UUID.randomUUID();
    var staff = UUID.randomUUID();
    var department = UUID.randomUUID();
    var service = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.staff_members(id,staff_code,full_name,status) VALUES (?,'ACTOR','Synthetic Actor','ACTIVE')",
        staff);
    jdbc.update(
        "INSERT INTO public.accounts(id,account_type,username,password_hash,staff_member_id,status) VALUES (?,'STAFF','actor','test-password-hash',?,'ACTIVE')",
        actor,
        staff);
    jdbc.update(
        "INSERT INTO public.organizations(id,code,name,organization_type,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES (?,'ORG','Synthetic Organization','COMPANY','0901','o@example.test','Address','Contact','0902','c@example.test','ACTIVE')",
        org);
    jdbc.update(
        "INSERT INTO public.health_examination_batches(id,organization_id,batch_code,name,examination_site_type,examination_site_name,examination_site_address,status,created_by) VALUES (?,?,'BATCH','Synthetic Batch','CLINIC','Clinic','Address','READY',?)",
        batch,
        org,
        actor);
    jdbc.update(
        "INSERT INTO public.health_examination_batch_days(id,batch_id,examination_date) VALUES (?,?,'2026-10-04'),(?,?,'2026-10-05')",
        day1,
        batch,
        day2,
        batch);
    jdbc.update(
        "INSERT INTO public.departments(id,code,name,department_type) VALUES (?,'D1','Exam','CLINICAL')",
        department);
    jdbc.update(
        "INSERT INTO public.services(id,code,name,service_type,performing_department_id,unit_price) VALUES (?,'S1','Exam','CONSULTATION',?,200)",
        service,
        department);
    jdbc.update(
        "INSERT INTO public.health_examination_batch_services(id,batch_id,service_id,reference_price_snapshot,negotiated_price,display_order) VALUES (?,?,?,200,100,1)",
        batchService,
        batch,
        service);
  }

  private HealthExaminationImportRow row(int n) {
    return new HealthExaminationImportRow(
        AggregateId.of(UUID.randomUUID()),
        n,
        null,
        "Synthetic Person " + n,
        LocalDate.of(1990, 1, 1),
        "MALE",
        IdentificationNumber.of(String.format("%012d", n)),
        null,
        null,
        "Department",
        "Position",
        List.of());
  }

  private List<com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService>
      scope() {
    return List.of(
        new com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService(
            AggregateId.of(batchService),
            AggregateId.of(UUID.randomUUID()),
            AggregateId.of(batch),
            Money.vnd("200"),
            Money.vnd("100"),
            1,
            true,
            0));
  }

  @Test
  void validOnlyStagingPreviewVersionAndFrozenConfirmationRoundTrip() {
    var invalid = row(1);
    invalid.reject("MISSING_POSITION_NAME");
    assertThat(stage.execute(org, batch, actor, List.of(day1, day2), List.of(invalid)).importId())
        .isNull();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public.import_jobs", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public.import_rows", Integer.class))
        .isZero();
    var staged = stage.execute(org, batch, actor, List.of(day1, day2), List.of(row(1), row(2)));
    var changed =
        preview.execute(
            org, batch, staged.importId(), actor, 0, List.of(day1, day2), Map.of(1, day2, 2, day2));
    assertThat(changed.rowVersion()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT row_version FROM public.import_jobs WHERE id=?",
                Long.class,
                staged.importId()))
        .isEqualTo(1);
    assertThatThrownBy(() -> confirm.execute(org, batch, staged.importId(), actor, 0))
        .isInstanceOf(ConcurrentUpdateException.class);
    var committed = confirm.execute(org, batch, staged.importId(), actor, 1);
    assertThat(committed.importedRows()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.health_examination_batch_participants WHERE batch_day_id=?",
                Integer.class,
                day2))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public.patients", Integer.class)).isZero();
    assertThat(confirm.execute(org, batch, staged.importId(), actor, 0)).isEqualTo(committed);
    assertThat(
            jdbc.queryForObject(
                "SELECT row_version FROM public.import_jobs WHERE id=?",
                Long.class,
                staged.importId()))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.import_rows WHERE committed_resource_id IS NOT NULL",
                Integer.class))
        .isEqualTo(2);
  }

  @Test
  void auditFailureRollsBackAllInsertedParticipantsAndConfirmation() {
    var staged = stage.execute(org, batch, actor, List.of(day1), List.of(row(1)));
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), eq("PARTICIPANT_ROSTER_IMPORT_CONFIRMED"), any(), any(), any(), any());
    assertThatThrownBy(() -> confirm.execute(org, batch, staged.importId(), actor, 0))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.health_examination_batch_participants", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM public.import_jobs WHERE id=?",
                String.class,
                staged.importId()))
        .isEqualTo("VALIDATED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.import_rows WHERE committed_resource_id IS NOT NULL",
                Integer.class))
        .isZero();
  }

  @Test
  void supplementaryDuplicateCannotOverwriteRoster() {
    var first = stage.execute(org, batch, actor, List.of(day1), List.of(row(1)));
    confirm.execute(org, batch, first.importId(), actor, 0);
    var rejected = stage.execute(org, batch, actor, List.of(day2), List.of(row(1), row(2)));
    assertThat(rejected.importId()).isNull();
    assertThat(rejected.rows().getFirst().errors()).contains("DUPLICATE_IN_BATCH");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public.import_jobs", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT batch_day_id FROM public.health_examination_batch_participants",
                UUID.class))
        .isEqualTo(day1);
  }

  @Test
  void reconciliationRoundTripRetainsUncheckedRowsAndHeaderConflictsRollbackServices() {
    var first = stage.execute(org, batch, actor, List.of(day1), List.of(row(1)));
    confirm.execute(org, batch, first.importId(), actor, 0);
    var p = participants.findByBatch(AggregateId.of(batch), 0, 10, null, "id", "ASC").getFirst();
    var now = Instant.parse("2026-10-04T00:00:00Z");
    var s =
        new HealthExaminationBatchParticipantService(
            AggregateId.of(UUID.randomUUID()),
            p.batchId(),
            p.id(),
            AggregateId.of(batchService),
            true,
            null,
            Money.vnd("100"),
            AggregateId.of(actor),
            now,
            now,
            now,
            0);
    p.reconcileServices(List.of(s), scope(), AggregateId.of(actor), now);
    var transaction = new TransactionTemplate(tx);
    transaction.executeWithoutResult(status -> participants.save(p, 0));
    var restored = participants.findById(p.id()).orElseThrow();
    assertThat(restored.rowVersion()).isEqualTo(1);
    assertThat(restored.services()).hasSize(1);
    var retained =
        restored
            .services()
            .getFirst()
            .recordPerformed(false, AggregateId.of(actor), now.plusSeconds(1));
    restored.reconcileServices(
        List.of(retained), scope(), AggregateId.of(actor), now.plusSeconds(1));
    transaction.executeWithoutResult(status -> participants.save(restored, 1));
    var reread = participants.findById(p.id()).orElseThrow();
    assertThat(reread.services().getFirst().performed()).isFalse();
    assertThat(reread.services().getFirst().rowVersion()).isEqualTo(1);
    assertThat(reread.services().getFirst().unitPriceSnapshot().amount())
        .isEqualByComparingTo("100");
    assertThatThrownBy(
            () -> transaction.executeWithoutResult(status -> participants.save(restored, 1)))
        .isInstanceOf(ConcurrentUpdateException.class);
  }
}
