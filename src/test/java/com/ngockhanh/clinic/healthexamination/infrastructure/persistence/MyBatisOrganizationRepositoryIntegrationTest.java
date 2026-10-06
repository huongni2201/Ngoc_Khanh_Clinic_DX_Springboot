package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.ListOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetOrganizationByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = MyBatisOrganizationRepositoryIntegrationTest.OrganizationTestConfiguration.class)
class MyBatisOrganizationRepositoryIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = {
        "com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.audit.infrastructure.persistence.mapper"
      })
  @org.springframework.context.annotation.Import({
    CreateOrganizationUseCase.class,
    UpdateOrganizationUseCase.class,
    GetOrganizationByIdUseCase.class,
    ListOrganizationUseCase.class,
    DeleteOrganizationUseCase.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisOrganizationRepository.class,
    com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter.class
  })
  static class OrganizationTestConfiguration {}

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @Autowired OrganizationRepository organizations;
  @Autowired CreateOrganizationUseCase createOrganization;
  @Autowired UpdateOrganizationUseCase updateOrganization;
  @Autowired GetOrganizationByIdUseCase getOrganization;
  @Autowired ListOrganizationUseCase listOrganizations;
  @Autowired DeleteOrganizationUseCase deleteOrganization;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void emptyOrganizations() {
    jdbc.execute("TRUNCATE public.organizations CASCADE");
  }

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AuditWriter audit;

  @Test
  void authenticationRecordingPreservesAccountColumnsWithSharedWriter() {
    UUID actor = createActorAccount();
    UUID correlation = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-10-05T03:00:00Z");

    audit.record(actor, "ACCOUNT_LOGIN", occurredAt, correlation);

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.audit_events WHERE actor_account_id = ? AND action = 'ACCOUNT_LOGIN' AND resource_type = 'ACCOUNT' AND resource_id = ? AND occurred_at = ? AND correlation_id = ? AND department_id IS NULL AND metadata = '{}'::jsonb",
                Integer.class,
                actor,
                actor,
                java.sql.Timestamp.from(occurredAt),
                correlation))
        .isEqualTo(1);
  }

  private Organization organization(String taxCode) {
    return Organization.create(
        new AggregateId(UUID.randomUUID()),
        "Synthetic School",
        taxCode,
        "0901",
        "o@example.test",
        "Address",
        "Contact",
        "0902",
        "c@example.test");
  }

  @Test
  void roundTripsCleanSlateChannelsAndEnforcesTaxCodeUniqueness() {
    var first = organization("S1");
    var second = organization("S2");
    organizations.save(first);
    organizations.save(second);
    var restored = organizations.findById(first.id()).orElseThrow();
    assertThat(restored.name()).isEqualTo(first.name());
    assertThat(restored.taxCode()).isEqualTo(first.taxCode());
    assertThat(restored.contactEmail()).isEqualTo(first.contactEmail());
    assertThat(organizations.existsByTaxCode("S1", null)).isTrue();
    assertThat(organizations.existsByTaxCode("S1", first.id())).isFalse();
    assertThatThrownBy(() -> organizations.save(organization("S1")))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  }

  @Test
  void staleWritesAreDetectedAndUpdatesPreserveChannels() {
    var first = organization("S3");
    organizations.save(first);
    first.updateDetails(
        "Renamed School",
        first.taxCode(),
        first.phone(),
        first.email(),
        first.address(),
        first.contactFullName(),
        first.contactPhone(),
        first.contactEmail());
    organizations.update(first, 0);
    var restored = organizations.findById(first.id()).orElseThrow();
    assertThat(restored.name()).isEqualTo("Renamed School");
    assertThat(restored.rowVersion()).isEqualTo(1);
    assertThat(restored.contactEmail()).isEqualTo("c@example.test");
    assertThatThrownBy(() -> organizations.update(first, 0))
        .isInstanceOf(ConcurrentUpdateException.class);
  }

  @Test
  void updateAuditsVersionChangeWithAuthenticatedActor() {
    UUID actor = createActorAccount();
    var first = organization("S4");
    organizations.save(first);

    var response =
        updateOrganization.execute(
            first.id().value(),
            UpdateOrganizationCommand.builder()
                .name("Renamed School")
                .taxCode(first.taxCode())
                .phone(first.phone())
                .email(first.email())
                .address(first.address())
                .contactFullName(first.contactFullName())
                .contactPhone(first.contactPhone())
                .contactEmail(first.contactEmail())
                .rowVersion(0L)
                .build(),
            actor);

    assertThat(response.name()).isEqualTo("Renamed School");
    assertThat(response.rowVersion()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.audit_events WHERE resource_id = ? AND action = 'UPDATE_ORGANIZATION' AND actor_account_id = ?",
                Integer.class,
                first.id().value(),
                actor))
        .isEqualTo(1);
  }

  @Test
  void auditFailureRollsBackOrganizationUpdateAndAuditRow() {
    UUID actor = createActorAccount();
    var first = organization("S5");
    organizations.save(first);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("audit failed after insert");
            })
        .when(audit)
        .record(
            eq(actor),
            eq("UPDATE_ORGANIZATION"),
            eq("ORGANIZATION"),
            eq(first.id().value()),
            any(),
            any());

    assertThatThrownBy(
            () ->
                updateOrganization.execute(
                    first.id().value(),
                    UpdateOrganizationCommand.builder()
                        .name("Renamed School")
                        .taxCode(first.taxCode())
                        .phone(first.phone())
                        .email(first.email())
                        .address(first.address())
                        .contactFullName(first.contactFullName())
                        .contactPhone(first.contactPhone())
                        .contactEmail(first.contactEmail())
                        .rowVersion(0L)
                        .build(),
                    actor))
        .isInstanceOf(IllegalStateException.class);

    var restored = organizations.findById(first.id()).orElseThrow();
    assertThat(restored.name()).isEqualTo(first.name());
    assertThat(restored.rowVersion()).isEqualTo(first.rowVersion());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.audit_events WHERE resource_id = ? AND action = 'UPDATE_ORGANIZATION'",
                Integer.class,
                first.id().value()))
        .isZero();
  }

  @Test
  void auditFailureRollsBackOrganizationInsertAndAuditRow() {
    UUID actor = createActorAccount();
    var command =
        CreateOrganizationCommand.builder()
            .name("Audit Partner")
            .taxCode(null)
            .phone("0901")
            .email("office@example.test")
            .address("Address")
            .contactFullName("Contact")
            .contactPhone("0902")
            .contactEmail("contact@example.test")
            .build();
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("audit failed after insert");
            })
        .when(audit)
        .record(
            eq(actor),
            eq("CREATE_ORGANIZATION"),
            eq("ORGANIZATION"),
            any(UUID.class),
            isNull(),
            any());

    assertThatThrownBy(() -> createOrganization.execute(command, actor))
        .isInstanceOf(IllegalStateException.class);

    ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
    verify(audit)
        .record(
            eq(actor),
            eq("CREATE_ORGANIZATION"),
            eq("ORGANIZATION"),
            id.capture(),
            isNull(),
            any());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.organizations WHERE id = ?",
                Integer.class,
                id.getValue()))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.audit_events WHERE resource_id = ?",
                Integer.class,
                id.getValue()))
        .isZero();
  }

  private Organization organization(String taxCode, String name, String status) {
    return Organization.restore(
        new AggregateId(UUID.randomUUID()),
        name,
        taxCode,
        "0901",
        "o@example.test",
        "Address",
        "Contact",
        "0902",
        "c@example.test",
        status,
        0);
  }

  private List<String> taxCodes(ListOrganizationCommand command) {
    return listOrganizations.execute(command).items().stream().map(o -> o.taxCode()).toList();
  }

  private UpdateOrganizationCommand renameTo(Organization organization, String name, long version) {
    return UpdateOrganizationCommand.builder()
        .name(name)
        .taxCode(organization.taxCode())
        .phone(organization.phone())
        .email(organization.email())
        .address(organization.address())
        .contactFullName(organization.contactFullName())
        .contactPhone(organization.contactPhone())
        .contactEmail(organization.contactEmail())
        .rowVersion(version)
        .build();
  }

  private int auditCount(UUID resourceId, String action) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM public.audit_events WHERE resource_id = ? AND action = ?",
        Integer.class,
        resourceId,
        action);
  }

  /** Runs both tasks at the same moment and returns each outcome (value or thrown exception). */
  private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<Object>> futures =
          List.of(
              pool.submit(
                  () -> {
                    go.await();
                    return first.call();
                  }),
              pool.submit(
                  () -> {
                    go.await();
                    return second.call();
                  }));
      go.countDown();
      List<Object> outcomes = new java.util.ArrayList<>();
      for (Future<Object> future : futures) {
        try {
          outcomes.add(future.get());
        } catch (java.util.concurrent.ExecutionException e) {
          outcomes.add(e.getCause());
        }
      }
      return outcomes;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void listReturnsOnlyActiveOrganizationsWithTotalsAfterFiltering() {
    organizations.save(organization("A1", "Alpha", "ACTIVE"));
    organizations.save(organization("A2", "Beta", "ACTIVE"));
    organizations.save(organization("A3", "Gamma", "INACTIVE"));

    var page = listOrganizations.execute(ListOrganizationCommand.builder().build());

    assertThat(page.items()).extracting("taxCode").containsExactly("A1", "A2");
    assertThat(page.items()).allSatisfy(o -> assertThat(o.status()).isEqualTo("ACTIVE"));
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.totalPages()).isEqualTo(1);
  }

  @Test
  void emptyDatasetHasNoItemsAndZeroTotals() {
    var page = listOrganizations.execute(ListOrganizationCommand.builder().build());

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  void pageBeyondTheEndIsEmptyButKeepsTotals() {
    for (int i = 1; i <= 3; i++) organizations.save(organization("P" + i, "Org " + i, "ACTIVE"));

    var page = listOrganizations.execute(ListOrganizationCommand.builder().page(5).size(2).build());

    assertThat(page.items()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(2);
  }

  @Test
  void sortsByTaxCodeAndNameInBothDirectionsWithIdTieBreaker() {
    organizations.save(organization("C1", "Same Name", "ACTIVE"));
    organizations.save(organization("C2", "Same Name", "ACTIVE"));
    organizations.save(organization("C3", "Another", "ACTIVE"));

    assertThat(taxCodes(ListOrganizationCommand.builder().sortKey("taxCode").sortBy("ASC").build()))
        .containsExactly("C1", "C2", "C3");
    assertThat(
            taxCodes(ListOrganizationCommand.builder().sortKey("taxCode").sortBy("DESC").build()))
        .containsExactly("C3", "C2", "C1");
    assertThat(taxCodes(ListOrganizationCommand.builder().sortKey("name").sortBy("ASC").build()))
        .first()
        .isEqualTo("C3");
    assertThat(taxCodes(ListOrganizationCommand.builder().sortKey("name").sortBy("DESC").build()))
        .last()
        .isEqualTo("C3");
  }

  @Test
  void duplicateNamesPaginateWithoutRepeatingOrSkippingRows() {
    for (int i = 1; i <= 5; i++) organizations.save(organization("D" + i, "Same Name", "ACTIVE"));

    var seen = new java.util.ArrayList<String>();
    for (int page = 1; page <= 3; page++) {
      seen.addAll(
          taxCodes(
              ListOrganizationCommand.builder()
                  .page(page)
                  .size(2)
                  .sortKey("name")
                  .sortBy("ASC")
                  .build()));
    }

    assertThat(seen)
        .containsExactlyInAnyOrder("D1", "D2", "D3", "D4", "D5")
        .doesNotHaveDuplicates();
  }

  @Test
  void searchMatchesTaxCodeOrNameIgnoringCaseAndSupportsVietnamese() {
    organizations.save(organization("SCH-01", "Trường Tiểu học Ánh Dương", "ACTIVE"));
    organizations.save(organization("CORP-02", "Clinic Corp", "ACTIVE"));

    assertThat(taxCodes(ListOrganizationCommand.builder().searchKey("sch-01").build()))
        .containsExactly("SCH-01");
    assertThat(taxCodes(ListOrganizationCommand.builder().searchKey("CLINIC").build()))
        .containsExactly("CORP-02");
    assertThat(taxCodes(ListOrganizationCommand.builder().searchKey("ánh dương").build()))
        .containsExactly("SCH-01");
    assertThat(taxCodes(ListOrganizationCommand.builder().searchKey("   ").build())).hasSize(2);
  }

  @Test
  void searchTreatsPercentUnderscoreAndBackslashAsLiterals() {
    organizations.save(organization("L1", "100% Care", "ACTIVE"));
    organizations.save(organization("L2", "1000 Care", "ACTIVE"));
    organizations.save(organization("L3", "Snake_Case", "ACTIVE"));
    organizations.save(organization("L4", "SnakeXCase", "ACTIVE"));
    organizations.save(organization("L5", "Back\\slash", "ACTIVE"));

    assertThat(taxCodes(ListOrganizationCommand.builder().searchKey("%").build()))
        .containsExactly("L1");
    assertThat(taxCodes(ListOrganizationCommand.builder().searchKey("_").build()))
        .containsExactly("L3");
    assertThat(taxCodes(ListOrganizationCommand.builder().searchKey("\\").build()))
        .containsExactly("L5");
  }

  @Test
  void searchCountMatchesTheFilteredSet() {
    for (int i = 1; i <= 12; i++) organizations.save(organization("M" + i, "Match " + i, "ACTIVE"));
    organizations.save(organization("X1", "Other", "ACTIVE"));

    var page =
        listOrganizations.execute(
            ListOrganizationCommand.builder().searchKey("match").size(5).build());

    assertThat(page.totalElements()).isEqualTo(12);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.items()).hasSize(5);
  }

  @Test
  void concurrentCreatesWithSameTaxCodeProduceOneOrganizationAndOneAudit() throws Exception {
    UUID actor = createActorAccount();
    var command =
        CreateOrganizationCommand.builder()
            .name("Race Partner")
            .taxCode("TAX-RACE")
            .phone("0901")
            .email("office@example.test")
            .address("Address")
            .contactFullName("Contact")
            .contactPhone("0902")
            .contactEmail("contact@example.test")
            .build();

    var outcomes =
        race(
            () -> createOrganization.execute(command, actor),
            () -> createOrganization.execute(command, actor));

    assertThat(outcomes.stream().filter(o -> o instanceof Throwable)).hasSize(1);
    assertThat(outcomes.stream().filter(o -> o instanceof Throwable).findFirst().orElseThrow())
        .isInstanceOfAny(
            org.springframework.dao.DuplicateKeyException.class,
            com.ngockhanh.clinic.healthexamination.domain.exception.DuplicateOrganizationIdentity
                .class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.organizations WHERE tax_code = 'TAX-RACE'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.audit_events WHERE action = 'CREATE_ORGANIZATION' AND actor_account_id = ?",
                Integer.class,
                actor))
        .isEqualTo(1);
  }

  @Test
  void concurrentUpdatesWithSameExpectedVersionLetOnlyOneWin() throws Exception {
    UUID actor = createActorAccount();
    var first = organization("RACE-UPD");
    organizations.save(first);
    UUID id = first.id().value();

    var outcomes =
        race(
            () -> updateOrganization.execute(id, renameTo(first, "Writer One", 0), actor),
            () -> updateOrganization.execute(id, renameTo(first, "Writer Two", 0), actor));

    assertThat(outcomes.stream().filter(o -> o instanceof ConcurrentUpdateException)).hasSize(1);
    var stored = organizations.findById(first.id()).orElseThrow();
    assertThat(stored.rowVersion()).isEqualTo(1);
    assertThat(stored.name()).isIn("Writer One", "Writer Two");
    assertThat(auditCount(id, "UPDATE_ORGANIZATION")).isEqualTo(1);
  }

  @Test
  void deactivationKeepsTheRowBumpsVersionOnceAndHidesItFromGetAndList() {
    UUID actor = createActorAccount();
    var first = organization("DEACT-1");
    organizations.save(first);
    UUID id = first.id().value();

    deleteOrganization.execute(
        id, DeleteOrganizationCommand.builder().rowVersion(0L).build(), actor);

    var stored = organizations.findById(first.id()).orElseThrow();
    assertThat(stored.status()).isEqualTo("INACTIVE");
    assertThat(stored.rowVersion()).isEqualTo(1);
    assertThat(stored.name()).isEqualTo(first.name());
    assertThatThrownBy(() -> getOrganization.execute(id))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(taxCodes(ListOrganizationCommand.builder().build())).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.audit_events WHERE resource_id = ? AND action = 'DEACTIVATE_ORGANIZATION' AND actor_account_id = ?",
                Integer.class,
                id,
                actor))
        .isEqualTo(1);
  }

  @Test
  void repeatedDeactivationIsANoOpAndStaleRetryIsAConflict() {
    UUID actor = createActorAccount();
    var first = organization("DEACT-2");
    organizations.save(first);
    UUID id = first.id().value();
    deleteOrganization.execute(
        id, DeleteOrganizationCommand.builder().rowVersion(0L).build(), actor);

    deleteOrganization.execute(
        id, DeleteOrganizationCommand.builder().rowVersion(1L).build(), actor);

    assertThat(organizations.findById(first.id()).orElseThrow().rowVersion()).isEqualTo(1);
    assertThat(auditCount(id, "DEACTIVATE_ORGANIZATION")).isEqualTo(1);
    assertThatThrownBy(
            () ->
                deleteOrganization.execute(
                    id, DeleteOrganizationCommand.builder().rowVersion(0L).build(), actor))
        .isInstanceOf(ConcurrentUpdateException.class);
  }

  @Test
  void auditFailureRollsBackOrganizationDeactivationAndAuditRow() {
    UUID actor = createActorAccount();
    var first = organization("DEACT-3");
    organizations.save(first);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("audit failed after insert");
            })
        .when(audit)
        .record(
            eq(actor),
            eq("DEACTIVATE_ORGANIZATION"),
            eq("ORGANIZATION"),
            eq(first.id().value()),
            any(),
            any());

    assertThatThrownBy(
            () ->
                deleteOrganization.execute(
                    first.id().value(),
                    DeleteOrganizationCommand.builder().rowVersion(0L).build(),
                    actor))
        .isInstanceOf(IllegalStateException.class);

    var restored = organizations.findById(first.id()).orElseThrow();
    assertThat(restored.status()).isEqualTo(first.status());
    assertThat(restored.rowVersion()).isEqualTo(first.rowVersion());
    assertThat(auditCount(first.id().value(), "DEACTIVATE_ORGANIZATION")).isZero();
  }

  @Test
  void concurrentUpdateAndDeactivateLetOnlyOneWin() throws Exception {
    UUID actor = createActorAccount();
    var first = organization("RACE-DEL");
    organizations.save(first);
    UUID id = first.id().value();

    var outcomes =
        race(
            () -> updateOrganization.execute(id, renameTo(first, "Renamed", 0), actor),
            () -> {
              deleteOrganization.execute(
                  id, DeleteOrganizationCommand.builder().rowVersion(0L).build(), actor);
              return "deleted";
            });

    assertThat(outcomes.stream().filter(o -> o instanceof ConcurrentUpdateException)).hasSize(1);
    assertThat(organizations.findById(first.id()).orElseThrow().rowVersion()).isEqualTo(1);
    assertThat(auditCount(id, "UPDATE_ORGANIZATION") + auditCount(id, "DEACTIVATE_ORGANIZATION"))
        .isEqualTo(1);
  }

  private UUID createActorAccount() {
    UUID staffId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.staff_members(id,staff_code,full_name,status) VALUES (?,?,?,'ACTIVE')",
        staffId,
        "ACTOR-" + staffId,
        "Synthetic Actor");
    jdbc.update(
        "INSERT INTO public.accounts(id,account_type,username,password_hash,staff_member_id,status) VALUES (?,'STAFF',?,'test-password-hash',?,'ACTIVE')",
        accountId,
        "org-actor-" + staffId,
        staffId);
    return accountId;
  }
}
