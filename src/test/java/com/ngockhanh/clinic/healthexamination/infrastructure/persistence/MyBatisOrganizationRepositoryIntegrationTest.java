package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.UUID;
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
@SpringBootTest(classes = HealthExaminationBatchCrudIntegrationTest.BatchTestConfiguration.class)
class MyBatisOrganizationRepositoryIntegrationTest {
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
  @Autowired JdbcTemplate jdbc;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AuditWriter audit;

  private Organization organization(String code) {
    return Organization.create(
        new AggregateId(UUID.randomUUID()),
        code,
        "Synthetic School",
        "SCHOOL",
        "SHARED-TAX",
        "0901",
        "o@example.test",
        "Address",
        "Contact",
        "Principal",
        "0902",
        "c@example.test");
  }

  @Test
  void roundTripsCleanSlateChannelsAndUsesCodeUniquenessInsteadOfTaxCode() {
    var first = organization("S1");
    var second = organization("S2");
    organizations.save(first);
    organizations.save(second);
    assertThat(organizations.findById(first.id())).contains(first);
    assertThat(organizations.existsByCode("S1", null)).isTrue();
    assertThat(organizations.existsByCode("S1", first.id())).isFalse();
    assertThatThrownBy(() -> organizations.save(organization("S1")))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  }

  @Test
  void staleWritesAreDetectedAndUpdatesPreserveChannels() {
    var first = organization("S3");
    organizations.save(first);
    organizations.update(
        first.updateDetails(
            first.code(),
            "Renamed School",
            first.organizationType(),
            first.taxCode(),
            first.phone(),
            first.email(),
            first.address(),
            first.contactFullName(),
            first.contactPosition(),
            first.contactPhone(),
            first.contactEmail()),
        0);
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
                .code(first.code())
                .name("Renamed School")
                .organizationType(first.organizationType())
                .taxCode(first.taxCode())
                .phone(first.phone())
                .email(first.email())
                .address(first.address())
                .contactFullName(first.contactFullName())
                .contactPosition(first.contactPosition())
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
                        .code(first.code())
                        .name("Renamed School")
                        .organizationType(first.organizationType())
                        .taxCode(first.taxCode())
                        .phone(first.phone())
                        .email(first.email())
                        .address(first.address())
                        .contactFullName(first.contactFullName())
                        .contactPosition(first.contactPosition())
                        .contactPhone(first.contactPhone())
                        .contactEmail(first.contactEmail())
                        .rowVersion(0L)
                        .build(),
                    actor))
        .isInstanceOf(IllegalStateException.class);

    var restored = organizations.findById(first.id()).orElseThrow();
    assertThat(restored).isEqualTo(first);
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
            .code("AUDIT-ORG")
            .name("Audit Partner")
            .organizationType("COMPANY")
            .taxCode(null)
            .phone("0901")
            .email("office@example.test")
            .address("Address")
            .contactFullName("Contact")
            .contactPosition(null)
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
