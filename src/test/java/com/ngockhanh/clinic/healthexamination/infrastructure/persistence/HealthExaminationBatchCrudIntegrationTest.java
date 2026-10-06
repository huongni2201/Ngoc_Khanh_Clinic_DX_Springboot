package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.BatchConfigurationAssembler;
import com.ngockhanh.clinic.healthexamination.application.service.BatchDetailResponseMapper;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetHealthExaminationBatchByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the five batch use cases against a real PostgreSQL database (Testcontainers; skipped when
 * Docker is unavailable): persistence, optimistic locking, soft delete visibility, delete guards,
 * rollback together with the audit event, and concurrent writers.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = HealthExaminationBatchCrudIntegrationTest.BatchTestConfiguration.class)
class HealthExaminationBatchCrudIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = {
        "com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.audit.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.integration.infrastructure.persistence.mapper"
      })
  @org.springframework.context.annotation.Import({
    CreateHealthExaminationBatchUseCase.class,
    GetHealthExaminationBatchByIdUseCase.class,
    UpdateHealthExaminationBatchUseCase.class,
    ListHealthExaminationBatchUseCase.class,
    DeleteHealthExaminationBatchUseCase.class,
    BatchConfigurationAssembler.class,
    BatchDetailResponseMapper.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationBatchRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisOrganizationRepository.class,
    com.ngockhanh.clinic.catalog.infrastructure.persistence.repository.MyBatisServiceCatalogQuery
        .class,
    com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter.class,
    com.ngockhanh.clinic.integration.infrastructure.persistence.repository.MyBatisBatchHistoryQuery
        .class
  })
  static class BatchTestConfiguration {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  private static final LocalDate D4 = LocalDate.of(2026, 10, 4);
  private static final LocalDate D6 = LocalDate.of(2026, 10, 6);
  private static final LocalDate D8 = LocalDate.of(2026, 10, 8);

  @Autowired JdbcTemplate jdbc;
  @Autowired CreateHealthExaminationBatchUseCase create;
  @Autowired GetHealthExaminationBatchByIdUseCase get;
  @Autowired UpdateHealthExaminationBatchUseCase update;
  @Autowired ListHealthExaminationBatchUseCase list;
  @Autowired DeleteHealthExaminationBatchUseCase delete;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.ngockhanh.clinic.audit.application.port.AuditWriter audit;

  UUID org, actor, service, secondService;

  @BeforeEach
  void fixture() {
    jdbc.execute(
        "TRUNCATE public.organizations,public.staff_members,public.services,public.departments CASCADE");
    org = UUID.randomUUID();
    actor = UUID.randomUUID();
    service = UUID.randomUUID();
    secondService = UUID.randomUUID();
    var staff = UUID.randomUUID();
    var department = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.staff_members(id,staff_code,full_name,status) VALUES (?,'ACTOR','Synthetic Actor','ACTIVE')",
        staff);
    jdbc.update(
        "INSERT INTO public.accounts(id,account_type,username,password_hash,staff_member_id,status) VALUES (?,'STAFF','actor','test-password-hash',?,'ACTIVE')",
        actor,
        staff);
    jdbc.update(
        "INSERT INTO public.organizations(id,name,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES (?,'Synthetic Organization','0901','o@example.test','Address','Contact','0902','c@example.test','ACTIVE')",
        org);
    jdbc.update(
        "INSERT INTO public.departments(id,code,name,department_type) VALUES (?,'D1','Exam','CLINICAL')",
        department);
    jdbc.update(
        "INSERT INTO public.services(id,code,name,service_type,performing_department_id,unit_price) VALUES (?,'S1','Exam','CONSULTATION',?,200)",
        service,
        department);
    jdbc.update(
        "INSERT INTO public.services(id,code,name,service_type,performing_department_id,unit_price) VALUES (?,'S2','Exam 2','CONSULTATION',?,300)",
        secondService,
        department);
  }

  private BatchConfiguration config(String code, List<LocalDate> dates, UUID... services) {
    return BatchConfiguration.builder()
        .batchCode(code)
        .batchName("Campaign%_")
        .examinationDates(dates)
        .examinationSiteType("ORGANIZATION_SITE")
        .examinationSiteName("Site")
        .examinationSiteAddress("Address")
        .services(
            Arrays.stream(services)
                .map(
                    s ->
                        BatchConfiguration.ServicePrice.builder()
                            .serviceId(s)
                            .negotiatedPrice(new BigDecimal("100"))
                            .build())
                .toList())
        .build();
  }

  private BatchDetailResponse createBatch(String code, UUID... services) {
    return create.execute(
        org,
        CreateHealthExaminationBatchCommand.builder()
            .configuration(config(code, List.of(D4, D8), services))
            .build(),
        actor);
  }

  private BatchDetailResponse updateBatch(
      BatchDetailResponse current, BatchConfiguration configuration, long rowVersion) {
    return update.execute(
        org,
        current.id(),
        UpdateHealthExaminationBatchCommand.builder()
            .configuration(configuration)
            .rowVersion(rowVersion)
            .build(),
        actor);
  }

  private void deleteBatch(UUID batchId, long rowVersion) {
    delete.execute(
        org,
        batchId,
        DeleteHealthExaminationBatchCommand.builder().rowVersion(rowVersion).build(),
        actor);
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private int audits(String action, UUID resourceId) {
    return count(
        "SELECT COUNT(*) FROM public.audit_events WHERE action=? AND resource_id=?",
        action,
        resourceId);
  }

  private HealthExaminationBatchListQuery listQuery(int page, int size, String search) {
    return HealthExaminationBatchListQuery.builder()
        .page(page)
        .size(size)
        .searchKey(search)
        .sortKey("batchCode")
        .sortBy("ASC")
        .build();
  }

  @Test
  void createPersistsDaysSnapshotsAuditAndListsOnlyTheOrganizationBatches() {
    var first = createBatch("B1", service);

    assertThat(first.days()).hasSize(2);
    assertThat(first.startDate()).isEqualTo(D4);
    assertThat(first.endDate()).isEqualTo(D8);
    assertThat(first.rowVersion()).isZero();
    assertThat(first.createdBy()).isEqualTo(actor);
    assertThat(first.services().getFirst().referencePriceSnapshot()).isEqualByComparingTo("200");
    assertThat(first.services().getFirst().negotiatedPrice()).isEqualByComparingTo("100");
    assertThat(first.services().getFirst().serviceCode()).isEqualTo("S1");
    assertThat(first.services().getFirst().serviceName()).isEqualTo("Exam");
    assertThat(audits("CREATE_HEALTH_EXAMINATION_BATCH", first.id())).isEqualTo(1);

    createBatch("B2", service);
    UUID otherOrganization = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.organizations(id,name,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES (?,'Other Organization','0901','other@example.test','Address','Contact','0902','other-contact@example.test','ACTIVE')",
        otherOrganization);
    create.execute(
        otherOrganization,
        CreateHealthExaminationBatchCommand.builder()
            .configuration(config("B3", List.of(D4), service))
            .build(),
        actor);

    var firstPage = list.execute(org, listQuery(1, 1, null));
    var secondPage = list.execute(org, listQuery(2, 1, null));
    var pastTheEnd = list.execute(org, listQuery(3, 1, null));

    assertThat(firstPage.totalElements()).isEqualTo(2);
    assertThat(firstPage.items()).extracting(item -> item.batchCode()).containsExactly("B1");
    assertThat(firstPage.items().getFirst().rowVersion()).isZero();
    assertThat(secondPage.items()).extracting(item -> item.batchCode()).containsExactly("B2");
    assertThat(pastTheEnd.items()).isEmpty();
    assertThat(pastTheEnd.totalElements()).isEqualTo(2);
    assertThat(list.execute(org, listQuery(1, 10, "%_")).totalElements()).isEqualTo(2);
    assertThat(list.execute(org, listQuery(1, 10, "b2")).items())
        .extracting(item -> item.batchCode())
        .containsExactly("B2");
    assertThat(list.execute(org, listQuery(1, 10, "no such batch")).totalElements()).isZero();
  }

  @Test
  void getIsScopedByOrganization() {
    var created = createBatch("B1", service);
    UUID otherOrganization = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.organizations(id,name,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES (?,'Other Organization','0901','other@example.test','Address','Contact','0902','other-contact@example.test','ACTIVE')",
        otherOrganization);

    assertThat(get.execute(org, created.id()).rowVersion()).isZero();
    assertThatThrownBy(() -> get.execute(otherOrganization, created.id()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> get.execute(org, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void duplicateBatchCodeIsRejectedWithoutPartialChildren() {
    createBatch("B1", service);

    assertThatThrownBy(() -> createBatch("B1", service)).isInstanceOf(DuplicateKeyException.class);

    assertThat(count("SELECT COUNT(*) FROM public.health_examination_batches")).isEqualTo(1);
    assertThat(count("SELECT COUNT(*) FROM public.health_examination_batch_days")).isEqualTo(2);
    assertThat(count("SELECT COUNT(*) FROM public.health_examination_batch_services")).isEqualTo(1);
  }

  @Test
  void failedAuditRollsBackHeaderDaysAndServicesOfACreate() {
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> createBatch("BAD", service)).isInstanceOf(IllegalStateException.class);

    assertThat(count("SELECT COUNT(*) FROM public.health_examination_batches")).isZero();
    assertThat(count("SELECT COUNT(*) FROM public.health_examination_batch_days")).isZero();
    assertThat(count("SELECT COUNT(*) FROM public.health_examination_batch_services")).isZero();
  }

  @Test
  void unknownActorForeignKeyRollsBackCreation() {
    assertThatThrownBy(
            () ->
                create.execute(
                    org,
                    CreateHealthExaminationBatchCommand.builder()
                        .configuration(config("BAD", List.of(D4), service))
                        .build(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("SELECT COUNT(*) FROM public.health_examination_batches")).isZero();
  }

  @Test
  void updateReplacesTheConfigurationKeepsIdentitiesAndBumpsVersions() {
    var created = createBatch("B1", service);
    var keptDay =
        created.days().stream()
            .filter(d -> d.examinationDate().equals(D4))
            .findFirst()
            .orElseThrow();
    var keptService = created.services().getFirst();

    var updated =
        updateBatch(
            created,
            config("B1-R", List.of(D4, D6), secondService, service).toBuilder()
                .batchName("Renamed")
                .build(),
            0);

    assertThat(updated.rowVersion()).isEqualTo(1);
    assertThat(updated.batchCode()).isEqualTo("B1-R");
    assertThat(updated.batchName()).isEqualTo("Renamed");
    assertThat(updated.startDate()).isEqualTo(D4);
    assertThat(updated.endDate()).isEqualTo(D6);
    assertThat(updated.days()).hasSize(2);
    assertThat(updated.days().stream().map(d -> d.id())).contains(keptDay.id());
    assertThat(updated.services()).hasSize(2);
    // the order of the request is the display order: the added service is now first
    assertThat(updated.services().get(0).serviceId()).isEqualTo(secondService);
    assertThat(updated.services().get(0).displayOrder()).isEqualTo(1);
    assertThat(updated.services().get(0).referencePriceSnapshot()).isEqualByComparingTo("300");
    var retained = updated.services().get(1);
    assertThat(retained.id()).isEqualTo(keptService.id());
    assertThat(retained.displayOrder()).isEqualTo(2);
    assertThat(retained.referencePriceSnapshot()).isEqualByComparingTo("200");
    assertThat(retained.rowVersion()).isEqualTo(keptService.rowVersion() + 1);
    assertThat(get.execute(org, created.id()).rowVersion()).isEqualTo(1);
    assertThat(audits("UPDATE_HEALTH_EXAMINATION_BATCH", created.id())).isEqualTo(1);
  }

  @Test
  void updateCanSwapTheOrderOfTwoServices() {
    var created = createBatch("B1", service, secondService);

    var updated = updateBatch(created, config("B1", List.of(D4, D8), secondService, service), 0);

    assertThat(updated.services())
        .extracting(s -> s.serviceId())
        .containsExactly(secondService, service);
    assertThat(updated.services()).extracting(s -> s.displayOrder()).containsExactly(1, 2);
  }

  @Test
  void aStaleVersionIsAConflictAndLeavesTheBatchUnchanged() {
    var created = createBatch("B1", service);
    updateBatch(created, config("B1", List.of(D4), service), 0);

    assertThatThrownBy(() -> updateBatch(created, config("B1-X", List.of(D4), service), 0))
        .isInstanceOf(ConcurrentUpdateException.class);

    var stored = get.execute(org, created.id());
    assertThat(stored.batchCode()).isEqualTo("B1");
    assertThat(stored.rowVersion()).isEqualTo(1);
    assertThat(audits("UPDATE_HEALTH_EXAMINATION_BATCH", created.id())).isEqualTo(1);
  }

  @Test
  void updateToAnotherBatchCodeIsRejectedAndRollsBackEverything() {
    createBatch("B1", service);
    var other = createBatch("B2", service);

    assertThatThrownBy(() -> updateBatch(other, config("B1", List.of(D6), secondService), 0))
        .isInstanceOf(DuplicateKeyException.class);

    var stored = get.execute(org, other.id());
    assertThat(stored.batchCode()).isEqualTo("B2");
    assertThat(stored.rowVersion()).isZero();
    assertThat(stored.days()).hasSize(2);
    assertThat(stored.services()).extracting(s -> s.serviceId()).containsExactly(service);
  }

  @Test
  void failedAuditRollsBackAnUpdateAndADelete() {
    var created = createBatch("B1", service);
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> updateBatch(created, config("B1-X", List.of(D6), secondService), 0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> deleteBatch(created.id(), 0))
        .isInstanceOf(IllegalStateException.class);

    var stored = get.execute(org, created.id());
    assertThat(stored.batchCode()).isEqualTo("B1");
    assertThat(stored.rowVersion()).isZero();
    assertThat(stored.days()).hasSize(2);
    assertThat(
            count(
                "SELECT COUNT(*) FROM public.health_examination_batches WHERE id=? AND deleted_at IS NULL",
                created.id()))
        .isEqualTo(1);
  }

  @Test
  void updateCannotRemoveADayThatAParticipantUses() {
    var created = createBatch("B1", service);
    var usedDay = created.days().getFirst();
    insertParticipant(created.id(), usedDay.id());
    var keepOnlyTheOtherDay =
        created.days().stream()
            .map(d -> d.examinationDate())
            .filter(d -> !d.equals(usedDay.examinationDate()))
            .toList();

    assertThatThrownBy(() -> updateBatch(created, config("B1", keepOnlyTheOtherDay, service), 0))
        .isInstanceOf(DomainRuleViolation.class);

    assertThat(get.execute(org, created.id()).days()).hasSize(2);
    // adding a day and keeping the used one is still allowed
    var withExtraDay =
        new ArrayList<>(created.days().stream().map(d -> d.examinationDate()).toList());
    withExtraDay.add(D6);
    assertThat(updateBatch(created, config("B1", withExtraDay, service), 0).days()).hasSize(3);
  }

  @Test
  void aBatchThatIsNoLongerADraftCannotBeUpdatedOrDeleted() {
    var created = createBatch("B1", service);
    jdbc.update(
        "UPDATE public.health_examination_batches SET status='READY' WHERE id=?", created.id());

    assertThatThrownBy(() -> updateBatch(created, config("B1-X", List.of(D4), service), 0))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> deleteBatch(created.id(), 0)).isInstanceOf(DomainRuleViolation.class);
  }

  @Test
  void deleteIsASoftDeleteThatHidesTheBatchButKeepsRowsAndTheCode() {
    var created = createBatch("B1", service);

    deleteBatch(created.id(), 0);

    assertThat(
            count(
                "SELECT COUNT(*) FROM public.health_examination_batches WHERE id=? AND deleted_at IS NOT NULL AND row_version=1",
                created.id()))
        .isEqualTo(1);
    assertThat(count("SELECT COUNT(*) FROM public.health_examination_batch_days")).isEqualTo(2);
    assertThat(count("SELECT COUNT(*) FROM public.health_examination_batch_services")).isEqualTo(1);
    assertThat(audits("DELETE_HEALTH_EXAMINATION_BATCH", created.id())).isEqualTo(1);
    assertThatThrownBy(() -> get.execute(org, created.id()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(list.execute(org, listQuery(1, 10, null)).totalElements()).isZero();
    // a second delete (even with the version it had) and an update both see nothing
    assertThatThrownBy(() -> deleteBatch(created.id(), 0))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> updateBatch(created, config("B1", List.of(D4), service), 0))
        .isInstanceOf(ResourceNotFoundException.class);
    // the unique batch code is not released
    assertThatThrownBy(() -> createBatch("B1", service)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void deleteWithAStaleVersionIsAConflict() {
    var created = createBatch("B1", service);
    updateBatch(created, config("B1", List.of(D4), service), 0);

    assertThatThrownBy(() -> deleteBatch(created.id(), 0))
        .isInstanceOf(ConcurrentUpdateException.class);

    assertThat(get.execute(org, created.id()).rowVersion()).isEqualTo(1);
  }

  @Test
  void aBatchWithAParticipantOrImportHistoryCannotBeDeleted() {
    var withParticipant = createBatch("B1", service);
    insertParticipant(withParticipant.id(), withParticipant.days().getFirst().id());
    var withImport = createBatch("B2", service);
    jdbc.update(
        "INSERT INTO public.import_jobs(import_type,batch_id,configuration,status,created_by) VALUES ('ORGANIZATION_PARTICIPANT',?,'{}'::jsonb,'VALIDATED',?)",
        withImport.id(),
        actor);

    assertThatThrownBy(() -> deleteBatch(withParticipant.id(), 0))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> deleteBatch(withImport.id(), 0))
        .isInstanceOf(DomainRuleViolation.class);

    assertThat(
            count(
                "SELECT COUNT(*) FROM public.health_examination_batches WHERE deleted_at IS NULL"))
        .isEqualTo(2);
    assertThat(audits("DELETE_HEALTH_EXAMINATION_BATCH", withParticipant.id())).isZero();
  }

  @Test
  void ofTwoConcurrentUpdatesWithTheSameVersionExactlyOneWins() throws Exception {
    var created = createBatch("B1", service);
    var results =
        runTogether(
            () -> updateBatch(created, config("B1-A", List.of(D4), service), 0),
            () -> updateBatch(created, config("B1-B", List.of(D6), service), 0));

    assertThat(results.stream().filter(r -> r instanceof BatchDetailResponse)).hasSize(1);
    assertThat(results.stream().filter(r -> r instanceof ConcurrentUpdateException)).hasSize(1);
    assertThat(get.execute(org, created.id()).rowVersion()).isEqualTo(1);
  }

  @Test
  void ofAConcurrentUpdateAndDeleteWithTheSameVersionExactlyOneWins() throws Exception {
    var created = createBatch("B1", service);
    var results =
        runTogether(
            () -> updateBatch(created, config("B1-A", List.of(D4), service), 0),
            () -> {
              deleteBatch(created.id(), 0);
              return "deleted";
            });

    var updateResult = results.getFirst();
    var deleteResult = results.get(1);
    if (updateResult instanceof BatchDetailResponse) {
      assertThat(deleteResult).isInstanceOf(ConcurrentUpdateException.class);
    } else {
      assertThat(updateResult).isInstanceOf(ResourceNotFoundException.class);
      assertThat(deleteResult).isEqualTo("deleted");
    }
    assertThat(
            count(
                "SELECT row_version FROM public.health_examination_batches WHERE id=?",
                created.id()))
        .isEqualTo(1);
  }

  private List<Object> runTogether(Callable<Object> first, Callable<Object> second)
      throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var futures =
          List.of(first, second).stream()
              .map(
                  task ->
                      pool.submit(
                          () -> {
                            start.await();
                            try {
                              return task.call();
                            } catch (Exception e) {
                              return e;
                            }
                          }))
              .toList();
      start.countDown();
      var results = new ArrayList<Object>();
      for (var future : futures) {
        try {
          results.add(future.get());
        } catch (ExecutionException e) {
          results.add(e.getCause());
        }
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  private UUID insertParticipant(UUID batchId, UUID dayId) {
    return jdbc.queryForObject(
        "INSERT INTO public.health_examination_batch_participants(batch_id,batch_day_id,full_name,date_of_birth,sex,identification_number,department_name,position_name) VALUES (?,?,'Test Participant',DATE '2000-01-01','MALE','000000000001','Test Department','Test Position') RETURNING id",
        UUID.class,
        batchId,
        dayId);
  }
}
