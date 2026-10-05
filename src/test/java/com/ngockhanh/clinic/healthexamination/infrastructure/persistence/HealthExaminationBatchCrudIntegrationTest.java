package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ngockhanh.clinic.healthexamination.application.command.*;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.usecase.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
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
        "com.ngockhanh.clinic.audit.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.integration.infrastructure.persistence.mapper"
      })
  @org.springframework.context.annotation.Import({
    CreateHealthExaminationBatchUseCase.class,
    com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase.class,
    com.ngockhanh.clinic.healthexamination.application.usecase.UpdateOrganizationUseCase.class,
    ListHealthExaminationBatchUseCase.class,
    BatchDraftEditor.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationBatchRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisOrganizationRepository.class,
    com.ngockhanh.clinic.catalog.infrastructure.persistence.repository.MyBatisServiceCatalogQuery
        .class,
    com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter.class
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
  @Autowired ListHealthExaminationBatchUseCase list;

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.ngockhanh.clinic.audit.application.port.AuditWriter audit;

  UUID org, actor, service;

  @BeforeEach
  void fixture() {
    jdbc.execute(
        "TRUNCATE public.organizations,public.staff_members,public.services,public.departments CASCADE");
    org = UUID.randomUUID();
    actor = UUID.randomUUID();
    service = UUID.randomUUID();
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
        "INSERT INTO public.organizations(id,code,name,organization_type,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES (?,'ORG','Synthetic Organization','COMPANY','0901','o@example.test','Address','Contact','0902','c@example.test','ACTIVE')",
        org);
    jdbc.update(
        "INSERT INTO public.departments(id,code,name,department_type) VALUES (?,'D1','Exam','CLINICAL')",
        department);
    jdbc.update(
        "INSERT INTO public.services(id,code,name,service_type,performing_department_id,unit_price) VALUES (?,'S1','Exam','CONSULTATION',?,200)",
        service,
        department);
  }

  private BatchConfigurationCommand config(String code, UUID... services) {
    return new BatchConfigurationCommand(
        code,
        "Campaign%_",
        List.of(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 8)),
        "ORGANIZATION_SITE",
        "Site",
        "Address",
        Arrays.stream(services)
            .map(s -> new BatchConfigurationCommand.ServicePrice(s, new BigDecimal("100")))
            .toList());
  }

  @Test
  void atomicallyPersistsInitialDaysAndCapturesPriceSnapshots() {
    var first =
        create.execute(org, new CreateHealthExaminationBatchCommand(actor, config("B1", service)));
    assertThat(first.days()).hasSize(2);
    assertThat(first.startDate()).isEqualTo(LocalDate.of(2026, 10, 4));
    assertThat(first.endDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    assertThat(first.rowVersion()).isZero();
    var initialService = first.services().getFirst();
    assertThat(initialService.referencePriceSnapshot()).isEqualByComparingTo("200");
    create.execute(org, new CreateHealthExaminationBatchCommand(actor, config("B2", service)));
    UUID otherOrganization = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.organizations(id,code,name,organization_type,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES (?,'ORG-2','Other Organization','COMPANY','0901','other@example.test','Address','Contact','0902','other-contact@example.test','ACTIVE')",
        otherOrganization);
    create.execute(
        otherOrganization, new CreateHealthExaminationBatchCommand(actor, config("B3", service)));
    var firstPage =
        list.execute(org, new HealthExaminationBatchListQuery(1, 1, null, "batchCode", "ASC"));
    var secondPage =
        list.execute(org, new HealthExaminationBatchListQuery(2, 1, null, "batchCode", "ASC"));
    var otherOrganizationPage =
        list.execute(
            otherOrganization, new HealthExaminationBatchListQuery(1, 10, null, null, null));
    assertThat(firstPage.totalElements()).isEqualTo(2);
    assertThat(firstPage.items()).extracting(item -> item.batchCode()).containsExactly("B1");
    assertThat(secondPage.items()).extracting(item -> item.batchCode()).containsExactly("B2");
    assertThat(otherOrganizationPage.totalElements()).isEqualTo(1);
    assertThat(otherOrganizationPage.items())
        .extracting(item -> item.batchCode())
        .containsExactly("B3");
    assertThat(
            list.execute(org, new HealthExaminationBatchListQuery(1, 10, "%_", "startDate", "ASC"))
                .totalElements())
        .isEqualTo(2);
  }

  @Test
  void duplicateBatchCodeIsRejectedWithoutPartialChildren() {
    create.execute(org, new CreateHealthExaminationBatchCommand(actor, config("B1", service)));

    assertThatThrownBy(
            () ->
                create.execute(
                    org, new CreateHealthExaminationBatchCommand(actor, config("B1", service))))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.health_examination_batches", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.health_examination_batch_days", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.health_examination_batch_services", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void failedAuditRollsBackHeaderDaysAndServices() {
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), any(), any(), any(), any(), any());
    assertThatThrownBy(
            () ->
                create.execute(
                    org, new CreateHealthExaminationBatchCommand(actor, config("BAD", service))))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.health_examination_batches", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.health_examination_batch_days", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.health_examination_batch_services", Integer.class))
        .isZero();
  }

  @Test
  void unknownActorForeignKeyRollsBackCreation() {
    assertThatThrownBy(
            () ->
                create.execute(
                    org,
                    new CreateHealthExaminationBatchCommand(
                        UUID.randomUUID(), config("BAD", service))))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.health_examination_batches", Integer.class))
        .isZero();
  }
}
