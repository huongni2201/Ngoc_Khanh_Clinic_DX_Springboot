package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ngockhanh.clinic.healthexamination.application.command.ActivateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.AgreementConfiguration;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationAgreementListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ActivateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetHealthExaminationAgreementByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the five agreement use cases against a real PostgreSQL database (Testcontainers; skipped
 * when Docker is unavailable): persistence, optimistic locking, soft delete visibility, delete
 * guards, rollback together with the audit event, and concurrent writers.
 */
// Close cached connections when this class finishes; its containers are class-scoped.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = HealthExaminationAgreementCrudIntegrationTest.AgreementTestConfiguration.class)
class HealthExaminationAgreementCrudIntegrationTest {
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
    CreateHealthExaminationAgreementUseCase.class,
    com.ngockhanh.clinic.healthexamination.application.service.AgreementCodeGenerator.class,
    com.ngockhanh.clinic.healthexamination.application.service.AgreementSupport.class,
    GetHealthExaminationAgreementByIdUseCase.class,
    UpdateHealthExaminationAgreementUseCase.class,
    ListHealthExaminationAgreementUseCase.class,
    DeleteHealthExaminationAgreementUseCase.class,
    ActivateHealthExaminationAgreementUseCase.class,
    com.ngockhanh.clinic.healthexamination.application.usecase.EndHealthExaminationAgreementUseCase
        .class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationAgreementRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisOrganizationRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationParticipantRepository.class,
    com.ngockhanh.clinic.catalog.infrastructure.persistence.repository.MyBatisServiceCatalogQuery
        .class,
    com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter.class,
    com.ngockhanh.clinic.integration.infrastructure.persistence.repository
        .MyBatisAgreementHistoryQuery.class
  })
  static class AgreementTestConfiguration {
    @Bean
    Clock clock() {
      // Fixed so the year in generated agreement codes (KSK-2026-001) is stable.
      return Clock.fixed(Instant.parse("2026-10-08T03:00:00Z"), ZoneOffset.UTC);
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
  @Autowired CreateHealthExaminationAgreementUseCase create;
  @Autowired GetHealthExaminationAgreementByIdUseCase get;
  @Autowired UpdateHealthExaminationAgreementUseCase update;
  @Autowired ListHealthExaminationAgreementUseCase list;
  @Autowired DeleteHealthExaminationAgreementUseCase delete;
  @Autowired ActivateHealthExaminationAgreementUseCase start;

  @Autowired
  com.ngockhanh.clinic.healthexamination.application.usecase.EndHealthExaminationAgreementUseCase
      end;

  @Autowired HealthExaminationParticipantRepository participants;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.ngockhanh.clinic.audit.application.port.out.AuditWriter audit;

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
        "INSERT INTO public.accounts(id,account_type,role_id,username,password_hash,staff_member_id,status) VALUES (?,'STAFF',(SELECT id FROM public.roles WHERE code = 'CLINIC_MANAGER'),'actor','test-password-hash',?,'ACTIVE')",
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

  /** The validity runs from the first to the last of the given dates. */
  private AgreementConfiguration config(List<LocalDate> period, UUID... services) {
    return AgreementConfiguration.builder()
        .agreementName("Campaign%_")
        .validFrom(period.getFirst())
        .validTo(period.getLast())
        .services(
            Arrays.stream(services)
                .map(
                    s ->
                        AgreementConfiguration.ServicePrice.builder()
                            .serviceId(s)
                            .negotiatedPrice(new BigDecimal("100"))
                            .build())
                .toList())
        .build();
  }

  private AgreementDetailResponse createAgreement(UUID... services) {
    return create.execute(
        org,
        CreateHealthExaminationAgreementCommand.builder()
            .configuration(config(List.of(D4, D8), services))
            .build(),
        actor);
  }

  private AgreementDetailResponse updateAgreement(
      AgreementDetailResponse current, AgreementConfiguration configuration, long rowVersion) {
    return update.execute(
        org,
        current.id(),
        UpdateHealthExaminationAgreementCommand.builder()
            .configuration(configuration)
            .rowVersion(rowVersion)
            .build(),
        actor);
  }

  private void deleteAgreement(UUID agreementId, long rowVersion) {
    delete.execute(
        org,
        agreementId,
        DeleteHealthExaminationAgreementCommand.builder().rowVersion(rowVersion).build(),
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

  private HealthExaminationAgreementListQuery listQuery(int page, int size, String search) {
    return HealthExaminationAgreementListQuery.builder()
        .page(page)
        .size(size)
        .searchKey(search)
        .sortKey("agreementCode")
        .sortBy("ASC")
        .build();
  }

  @Test
  void createPersistsTheValiditySnapshotsAuditAndListsOnlyTheOrganizationAgreements() {
    var first = createAgreement(service);

    assertThat(first.validFrom()).isEqualTo(D4);
    assertThat(first.validTo()).isEqualTo(D8);
    assertThat(first.status()).isEqualTo("DRAFT");
    assertThat(first.rowVersion()).isZero();
    assertThat(first.createdBy()).isEqualTo(actor);
    assertThat(first.services().getFirst().referencePriceSnapshot()).isEqualByComparingTo("200");
    assertThat(first.services().getFirst().negotiatedPrice()).isEqualByComparingTo("100");
    assertThat(first.services().getFirst().serviceCode()).isEqualTo("S1");
    assertThat(first.services().getFirst().serviceName()).isEqualTo("Exam");
    assertThat(audits("CREATE_HEALTH_EXAMINATION_AGREEMENT", first.id())).isEqualTo(1);

    createAgreement(service);
    UUID otherOrganization = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.organizations(id,name,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES (?,'Other Organization','0901','other@example.test','Address','Contact','0902','other-contact@example.test','ACTIVE')",
        otherOrganization);
    create.execute(
        otherOrganization,
        CreateHealthExaminationAgreementCommand.builder()
            .configuration(config(List.of(D4), service))
            .build(),
        actor);

    var firstPage = list.execute(org, listQuery(1, 1, null));
    var secondPage = list.execute(org, listQuery(2, 1, null));
    var pastTheEnd = list.execute(org, listQuery(3, 1, null));

    assertThat(firstPage.totalElements()).isEqualTo(2);
    assertThat(firstPage.items())
        .extracting(item -> item.agreementCode())
        .containsExactly("KSK-2026-001");
    assertThat(firstPage.items().getFirst().rowVersion()).isZero();
    assertThat(secondPage.items())
        .extracting(item -> item.agreementCode())
        .containsExactly("KSK-2026-002");
    assertThat(pastTheEnd.items()).isEmpty();
    assertThat(pastTheEnd.totalElements()).isEqualTo(2);
    assertThat(list.execute(org, listQuery(1, 10, "%_")).totalElements()).isEqualTo(2);
    assertThat(list.execute(org, listQuery(1, 10, "ksk-2026-002")).items())
        .extracting(item -> item.agreementCode())
        .containsExactly("KSK-2026-002");
    assertThat(list.execute(org, listQuery(1, 10, "no such agreement")).totalElements()).isZero();
  }

  @Test
  void getIsScopedByOrganization() {
    var created = createAgreement(service);
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
  void agreementCodesAreGeneratedInSequenceAndNeverSuppliedByTheCaller() {
    var first = createAgreement(service);
    var second = createAgreement(service);
    var third = createAgreement(service);

    assertThat(first.agreementCode()).isEqualTo("KSK-2026-001");
    assertThat(second.agreementCode()).isEqualTo("KSK-2026-002");
    assertThat(third.agreementCode()).isEqualTo("KSK-2026-003");
  }

  @Test
  void numberingContinuesAfterTheHighestCodeOfTheYearAndIgnoresOtherCodes() {
    insertAgreementRow("KSK-2026-007");
    insertAgreementRow("KSK-2025-099");
    insertAgreementRow("ABC01");
    insertAgreementRow("KSK-2026-12x");

    assertThat(createAgreement(service).agreementCode()).isEqualTo("KSK-2026-008");
  }

  @Test
  void aDeletedAgreementKeepsItsCodeReserved() {
    var created = createAgreement(service);
    deleteAgreement(created.id(), 0);

    assertThat(createAgreement(service).agreementCode()).isEqualTo("KSK-2026-002");
  }

  @Test
  void concurrentCreatesGetDistinctCodes() throws Exception {
    var results = runTogether(() -> createAgreement(service), () -> createAgreement(service));

    assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(AgreementDetailResponse.class));
    assertThat(results)
        .extracting(r -> ((AgreementDetailResponse) r).agreementCode())
        .containsExactlyInAnyOrder("KSK-2026-001", "KSK-2026-002");
  }

  @Test
  void failedAuditRollsBackHeaderAndServicesOfACreate() {
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> createAgreement(service)).isInstanceOf(IllegalStateException.class);

    assertThat(count("SELECT COUNT(*) FROM public.health_examination_agreements")).isZero();
    assertThat(count("SELECT COUNT(*) FROM public.health_examination_agreement_services")).isZero();
  }

  @Test
  void unknownActorForeignKeyRollsBackCreation() {
    assertThatThrownBy(
            () ->
                create.execute(
                    org,
                    CreateHealthExaminationAgreementCommand.builder()
                        .configuration(config(List.of(D4), service))
                        .build(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("SELECT COUNT(*) FROM public.health_examination_agreements")).isZero();
  }

  @Test
  void updateReplacesTheConfigurationKeepsLineIdentitiesAndBumpsVersions() {
    var created = createAgreement(service);
    var keptService = created.services().getFirst();

    var updated =
        updateAgreement(
            created,
            config(List.of(D4, D6), secondService, service).toBuilder()
                .agreementName("Renamed")
                .build(),
            0);

    assertThat(updated.rowVersion()).isEqualTo(1);
    assertThat(updated.agreementCode()).isEqualTo(created.agreementCode());
    assertThat(updated.agreementName()).isEqualTo("Renamed");
    assertThat(updated.validFrom()).isEqualTo(D4);
    assertThat(updated.validTo()).isEqualTo(D6);
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
    assertThat(audits("UPDATE_HEALTH_EXAMINATION_AGREEMENT", created.id())).isEqualTo(1);
  }

  @Test
  void updateCanSwapTheOrderOfTwoServices() {
    var created = createAgreement(service, secondService);

    var updated = updateAgreement(created, config(List.of(D4, D8), secondService, service), 0);

    assertThat(updated.services())
        .extracting(s -> s.serviceId())
        .containsExactly(secondService, service);
    assertThat(updated.services()).extracting(s -> s.displayOrder()).containsExactly(1, 2);
  }

  @Test
  void aStaleVersionIsAConflictAndLeavesTheAgreementUnchanged() {
    var created = createAgreement(service);
    updateAgreement(created, config(List.of(D4), service), 0);

    assertThatThrownBy(() -> updateAgreement(created, config(List.of(D4), service), 0))
        .isInstanceOf(ConcurrentUpdateException.class);

    var stored = get.execute(org, created.id());
    assertThat(stored.agreementCode()).isEqualTo(created.agreementCode());
    assertThat(stored.rowVersion()).isEqualTo(1);
    assertThat(audits("UPDATE_HEALTH_EXAMINATION_AGREEMENT", created.id())).isEqualTo(1);
  }

  @Test
  void updateNeverChangesTheAgreementCode() {
    var created = createAgreement(service);

    updateAgreement(created, config(List.of(D6), secondService), 0);

    assertThat(get.execute(org, created.id()).agreementCode()).isEqualTo(created.agreementCode());
    assertThat(
            jdbc.queryForObject(
                "SELECT agreement_code FROM public.health_examination_agreements WHERE id=?",
                String.class,
                created.id()))
        .isEqualTo(created.agreementCode());
  }

  @Test
  void failedAuditRollsBackAnUpdateAndADelete() {
    var created = createAgreement(service);
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());

    assertThatThrownBy(() -> updateAgreement(created, config(List.of(D6), secondService), 0))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> deleteAgreement(created.id(), 0))
        .isInstanceOf(IllegalStateException.class);

    var stored = get.execute(org, created.id());
    assertThat(stored.agreementCode()).isEqualTo(created.agreementCode());
    assertThat(stored.rowVersion()).isZero();
    assertThat(stored.validTo()).isEqualTo(D8);
    assertThat(
            count(
                "SELECT COUNT(*) FROM public.health_examination_agreements WHERE id=? AND deleted_at IS NULL",
                created.id()))
        .isEqualTo(1);
  }

  @Test
  void anAgreementThatIsNoLongerADraftCannotBeUpdatedOrDeleted() {
    var created = createAgreement(service);
    jdbc.update(
        "UPDATE public.health_examination_agreements SET status='ACTIVE' WHERE id=?", created.id());

    assertThatThrownBy(() -> updateAgreement(created, config(List.of(D4), service), 0))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> deleteAgreement(created.id(), 0))
        .isInstanceOf(DomainRuleViolation.class);
  }

  private AgreementDetailResponse activateAgreement(UUID agreementId, long rowVersion) {
    return start.execute(
        org,
        agreementId,
        ActivateHealthExaminationAgreementCommand.builder().rowVersion(rowVersion).build(),
        actor);
  }

  @Test
  void activateMovesADraftToActiveOnceBumpsTheVersionAuditsAndLocksTheConfiguration() {
    var created = createAgreement(service);

    var started = activateAgreement(created.id(), 0);

    assertThat(started.status()).isEqualTo("ACTIVE");
    assertThat(started.rowVersion()).isEqualTo(1);
    assertThat(started.agreementCode()).isEqualTo(created.agreementCode());
    assertThat(audits("ACTIVATE_HEALTH_EXAMINATION_AGREEMENT", created.id())).isEqualTo(1);
    assertThatThrownBy(() -> activateAgreement(created.id(), 1))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> updateAgreement(created, config(List.of(D4), service), 1))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> deleteAgreement(created.id(), 1))
        .isInstanceOf(DomainRuleViolation.class);
  }

  @Test
  void aStaleActivateIsAConflictAndAFailedAuditRollsItBack() {
    var created = createAgreement(service);
    assertThatThrownBy(() -> activateAgreement(created.id(), 5))
        .isInstanceOf(ConcurrentUpdateException.class);

    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> activateAgreement(created.id(), 0))
        .isInstanceOf(IllegalStateException.class);

    var stored = get.execute(org, created.id());
    assertThat(stored.status()).isEqualTo("DRAFT");
    assertThat(stored.rowVersion()).isZero();
  }

  @Test
  void deleteIsASoftDeleteThatHidesTheAgreementButKeepsRowsAndTheCode() {
    var created = createAgreement(service);

    deleteAgreement(created.id(), 0);

    assertThat(
            count(
                "SELECT COUNT(*) FROM public.health_examination_agreements WHERE id=? AND deleted_at IS NOT NULL AND row_version=1",
                created.id()))
        .isEqualTo(1);
    assertThat(count("SELECT COUNT(*) FROM public.health_examination_agreement_services"))
        .isEqualTo(1);
    assertThat(audits("DELETE_HEALTH_EXAMINATION_AGREEMENT", created.id())).isEqualTo(1);
    assertThatThrownBy(() -> get.execute(org, created.id()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(list.execute(org, listQuery(1, 10, null)).totalElements()).isZero();
    // a second delete (even with the version it had) and an update both see nothing
    assertThatThrownBy(() -> deleteAgreement(created.id(), 0))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> updateAgreement(created, config(List.of(D4), service), 0))
        .isInstanceOf(ResourceNotFoundException.class);
    // the code is not released: the next agreement gets the next number
    assertThat(createAgreement(service).agreementCode()).isEqualTo("KSK-2026-002");
  }

  @Test
  void deleteWithAStaleVersionIsAConflict() {
    var created = createAgreement(service);
    updateAgreement(created, config(List.of(D4), service), 0);

    assertThatThrownBy(() -> deleteAgreement(created.id(), 0))
        .isInstanceOf(ConcurrentUpdateException.class);

    assertThat(get.execute(org, created.id()).rowVersion()).isEqualTo(1);
  }

  @Test
  void anAgreementWithAParticipantOrImportHistoryCannotBeDeleted() {
    var withParticipant = createAgreement(service);
    insertParticipant(withParticipant.id());
    var withImport = createAgreement(service);
    jdbc.update(
        "INSERT INTO public.import_jobs(import_type,agreement_id,configuration,status,created_by) VALUES ('ORGANIZATION_PARTICIPANT',?,'{}'::jsonb,'VALIDATED',?)",
        withImport.id(),
        actor);

    assertThatThrownBy(() -> deleteAgreement(withParticipant.id(), 0))
        .isInstanceOf(DomainRuleViolation.class);
    assertThatThrownBy(() -> deleteAgreement(withImport.id(), 0))
        .isInstanceOf(DomainRuleViolation.class);

    assertThat(
            count(
                "SELECT COUNT(*) FROM public.health_examination_agreements WHERE deleted_at IS NULL"))
        .isEqualTo(2);
    assertThat(audits("DELETE_HEALTH_EXAMINATION_AGREEMENT", withParticipant.id())).isZero();
  }

  @Test
  void ofTwoConcurrentUpdatesWithTheSameVersionExactlyOneWins() throws Exception {
    var created = createAgreement(service);
    var results =
        runTogether(
            () -> updateAgreement(created, config(List.of(D4), service), 0),
            () -> updateAgreement(created, config(List.of(D6), service), 0));

    assertThat(results.stream().filter(r -> r instanceof AgreementDetailResponse)).hasSize(1);
    assertThat(results.stream().filter(r -> r instanceof ConcurrentUpdateException)).hasSize(1);
    assertThat(get.execute(org, created.id()).rowVersion()).isEqualTo(1);
  }

  @Test
  void ofAConcurrentUpdateAndDeleteWithTheSameVersionExactlyOneWins() throws Exception {
    var created = createAgreement(service);
    var results =
        runTogether(
            () -> updateAgreement(created, config(List.of(D4), service), 0),
            () -> {
              deleteAgreement(created.id(), 0);
              return "deleted";
            });

    var updateResult = results.getFirst();
    var deleteResult = results.get(1);
    if (updateResult instanceof AgreementDetailResponse) {
      assertThat(deleteResult).isInstanceOf(ConcurrentUpdateException.class);
    } else {
      assertThat(updateResult).isInstanceOf(ResourceNotFoundException.class);
      assertThat(deleteResult).isEqualTo("deleted");
    }
    assertThat(
            count(
                "SELECT row_version FROM public.health_examination_agreements WHERE id=?",
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

  @Test
  void aCancelledParticipantIsReactivatedOnTheSameRowKeepingItsDataAndVersionChain() {
    var agreement = createAgreement(service);
    var participantId = insertParticipant(agreement.id());
    var agreementId = new AggregateId(agreement.id());
    var id = new AggregateId(participantId);

    var loaded = participants.findInAgreement(agreementId, id).orElseThrow();
    loaded.cancel();
    participants.save(loaded, loaded.getRowVersion());
    var cancelled = participants.findInAgreement(agreementId, id).orElseThrow();
    assertThat(cancelled.getRosterStatus()).isEqualTo(RosterStatus.CANCELLED);
    assertThat(cancelled.getRowVersion()).isEqualTo(1);

    cancelled.reactivate();
    participants.save(cancelled, cancelled.getRowVersion());

    var reactivated = participants.findInAgreement(agreementId, id).orElseThrow();
    assertThat(reactivated.getId()).isEqualTo(id);
    assertThat(reactivated.getRosterStatus()).isEqualTo(RosterStatus.ACTIVE);
    assertThat(reactivated.getRowVersion()).isEqualTo(2);
    assertThat(reactivated.getRoster()).isEqualTo(cancelled.getRoster());
    assertThat(
            count(
                "SELECT COUNT(*) FROM public.health_examination_participants WHERE agreement_id = ?",
                agreement.id()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT roster_status FROM public.health_examination_participants WHERE id = ?",
                String.class,
                participantId))
        .isEqualTo("ACTIVE");
  }

  @Test
  void reactivatingWithAStaleVersionIsRejectedAndLeavesTheRowCancelled() {
    var agreement = createAgreement(service);
    var participantId = insertParticipant(agreement.id());
    var agreementId = new AggregateId(agreement.id());
    var id = new AggregateId(participantId);
    var loaded = participants.findInAgreement(agreementId, id).orElseThrow();
    loaded.cancel();
    participants.save(loaded, loaded.getRowVersion());

    var stale = participants.findInAgreement(agreementId, id).orElseThrow();
    stale.reactivate();
    assertThatThrownBy(() -> participants.save(stale, 0))
        .isInstanceOf(ConcurrentUpdateException.class);

    assertThat(participants.findInAgreement(agreementId, id).orElseThrow().getRosterStatus())
        .isEqualTo(RosterStatus.CANCELLED);
  }

  private void insertAgreementRow(String code) {
    jdbc.update(
        "INSERT INTO public.health_examination_agreements(organization_id,agreement_code,name,status,created_by) VALUES (?,?,'Existing','DRAFT',?)",
        org,
        code,
        actor);
  }

  private UUID insertParticipant(UUID agreementId) {
    return jdbc.queryForObject(
        "INSERT INTO public.health_examination_participants(organization_id,agreement_id,full_name,date_of_birth,sex,identification_number,department_name,position_name) VALUES (?,?,'Test Participant',DATE '2000-01-01','MALE','000000000001','Test Department','Test Position') RETURNING id",
        UUID.class,
        org,
        agreementId);
  }

  @Test
  void theDatabaseRefusesTwoActiveLinesOfOneServiceButAllowsARetiredOne() {
    var created = createAgreement(service);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.health_examination_agreement_services(agreement_id,service_id,reference_price_snapshot,negotiated_price,display_order,active) VALUES (?,?,1,1,9,true)",
                    created.id(),
                    service))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update(
        "INSERT INTO public.health_examination_agreement_services(agreement_id,service_id,reference_price_snapshot,negotiated_price,display_order,active) VALUES (?,?,1,1,9,false)",
        created.id(),
        service);
    assertThat(
            count(
                "SELECT COUNT(*) FROM public.health_examination_agreement_services WHERE agreement_id=?",
                created.id()))
        .isEqualTo(2);
  }

  @Test
  void endMovesAnActiveAgreementToEndedAndFreezesServicesAndTheRoster() {
    var created = createAgreement(service);
    activateAgreement(created.id(), 0);

    var ended =
        end.execute(
            org,
            created.id(),
            com.ngockhanh.clinic.healthexamination.application.command
                .EndHealthExaminationAgreementCommand.builder()
                .rowVersion(1L)
                .build(),
            actor);

    assertThat(ended.status()).isEqualTo("ENDED");
    assertThat(ended.rowVersion()).isEqualTo(2);
    assertThat(audits("END_HEALTH_EXAMINATION_AGREEMENT", created.id())).isEqualTo(1);
    assertThatThrownBy(() -> updateAgreement(ended, config(List.of(D4), service, secondService), 2))
        .isInstanceOf(DomainRuleViolation.class);
    // a draft cannot be ended
    var draft = createAgreement(service);
    assertThatThrownBy(
            () ->
                end.execute(
                    org,
                    draft.id(),
                    com.ngockhanh.clinic.healthexamination.application.command
                        .EndHealthExaminationAgreementCommand.builder()
                        .rowVersion(0L)
                        .build(),
                    actor))
        .isInstanceOf(DomainRuleViolation.class);
  }

  @Test
  void anAgreementWithASessionCannotBeDeleted() {
    var created = createAgreement(service);
    jdbc.update(
        "INSERT INTO public.health_examination_sessions(organization_id,agreement_id,session_date,activity,site_type,site_address,created_by) VALUES (?,?,?,'ALL','CLINIC','',?)",
        org,
        created.id(),
        D4,
        actor);

    assertThatThrownBy(() -> deleteAgreement(created.id(), 0))
        .isInstanceOf(DomainRuleViolation.class);
    assertThat(audits("DELETE_HEALTH_EXAMINATION_AGREEMENT", created.id())).isZero();
  }
}
