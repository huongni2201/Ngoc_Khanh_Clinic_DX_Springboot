package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.AgreementConfiguration;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.ImportExaminationDetailsCommand;
import com.ngockhanh.clinic.healthexamination.application.command.RecordExaminationDetailCommand;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ExportExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ExportPaymentSummaryReportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetExaminationDetailImportTemplateUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetExaminationSummaryUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetPaymentSummaryReportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ImportExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.RecordExaminationDetailUseCase;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant.Roster;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.infrastructure.configuration.ExaminationDetailImportProperties;
import com.ngockhanh.clinic.healthexamination.infrastructure.configuration.ParticipantImportProperties;
import com.ngockhanh.clinic.healthexamination.infrastructure.word.ReportClinicProperties;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the examination detail export, the Excel import, the screen queries and the payment report
 * against a real PostgreSQL database (Testcontainers; skipped when Docker is unavailable): the SQL
 * of the read model with attendance derived from the session assignments, the row locks, the
 * idempotency receipt, the import tables and their triggers, the price snapshots, atomic rollback
 * and the Word export of the same figures.
 */
// Close cached connections when this class finishes; its containers are class-scoped.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ExaminationDetailIntegrationTest.DetailTestConfiguration.class)
class ExaminationDetailIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @EnableConfigurationProperties({
    ParticipantImportProperties.class,
    ExaminationDetailImportProperties.class,
    ReportClinicProperties.class
  })
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
    ExaminationDetailAccessPolicy.class,
    com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailImportCommitter
        .class,
    com.ngockhanh.clinic.healthexamination.application.service.PaymentSummaryReportAssembler.class,
    ListExaminationDetailsUseCase.class,
    GetExaminationSummaryUseCase.class,
    ExportExaminationDetailsUseCase.class,
    ImportExaminationDetailsUseCase.class,
    GetExaminationDetailImportTemplateUseCase.class,
    RecordExaminationDetailUseCase.class,
    GetPaymentSummaryReportUseCase.class,
    ExportPaymentSummaryReportUseCase.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationAgreementRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisOrganizationRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisHealthExaminationParticipantRepository.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository
        .MyBatisExaminationDetailReader.class,
    com.ngockhanh.clinic.healthexamination.infrastructure.excel.PoiExaminationDetailExcelWriter
        .class,
    com.ngockhanh.clinic.healthexamination.infrastructure.excel.PoiExaminationDetailExcelReader
        .class,
    com.ngockhanh.clinic.healthexamination.infrastructure.word.PoiPaymentReportDocxWriter.class,
    com.ngockhanh.clinic.catalog.infrastructure.persistence.repository.MyBatisServiceCatalogQuery
        .class,
    com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter.class,
    com.ngockhanh.clinic.integration.infrastructure.persistence.repository
        .MyBatisServiceReconciliationImportStore.class
  })
  static class DetailTestConfiguration {
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
  private static final LocalDate D8 = LocalDate.of(2026, 10, 8);
  private static final String STATE_SQL =
      "SELECT service_reconciliation_status FROM public.health_examination_participants WHERE id=?";

  @Autowired JdbcTemplate jdbc;
  @Autowired CreateHealthExaminationAgreementUseCase create;
  @Autowired HealthExaminationParticipantRepository participants;
  @Autowired ListExaminationDetailsUseCase list;
  @Autowired GetExaminationSummaryUseCase summary;
  @Autowired ExportExaminationDetailsUseCase export;
  @Autowired ImportExaminationDetailsUseCase importer;
  @Autowired GetExaminationDetailImportTemplateUseCase template;
  @Autowired RecordExaminationDetailUseCase recorder;
  @Autowired GetPaymentSummaryReportUseCase report;
  @Autowired ExportPaymentSummaryReportUseCase reportDocx;

  UUID org, actor, serviceA, serviceB, agreementId, agreementServiceA, agreementServiceB;
  String agreementCode;
  UUID p1, p2, p3;
  UserPrincipal principal;

  @BeforeEach
  void fixture() {
    jdbc.execute(
        "TRUNCATE public.organizations,public.staff_members,public.services,public.departments CASCADE");
    org = UUID.randomUUID();
    actor = UUID.randomUUID();
    serviceA = UUID.randomUUID();
    serviceB = UUID.randomUUID();
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
        "INSERT INTO public.organizations(id,name,tax_code,phone,email,address,contact_full_name,contact_phone,contact_email,status) VALUES (?,'Synthetic Organization','0101234567','0901','o@example.test','Address','Contact','0902','c@example.test','ACTIVE')",
        org);
    jdbc.update(
        "INSERT INTO public.departments(id,code,name,department_type) VALUES (?,'D1','Exam','CLINICAL')",
        department);
    jdbc.update(
        "INSERT INTO public.services(id,code,name,service_type,performing_department_id,unit_price) VALUES (?,'S1','Khám nội','CONSULTATION',?,200)",
        serviceA,
        department);
    jdbc.update(
        "INSERT INTO public.services(id,code,name,service_type,performing_department_id,unit_price) VALUES (?,'S2','Xét nghiệm','CONSULTATION',?,300)",
        serviceB,
        department);

    principal =
        UserPrincipal.builder()
            .userId(actor)
            .staffId(staff)
            .username("actor")
            .principalType("STAFF")
            .roleAssignments(
                List.of(
                    new UserPrincipal.Assignment(
                        UUID.randomUUID(),
                        "CLINIC_MANAGER",
                        List.of(
                            ExaminationDetailAccessPolicy.SERVICE_READ_PERMISSION,
                            ExaminationDetailAccessPolicy.SERVICE_SUMMARY_READ_PERMISSION,
                            ExaminationDetailAccessPolicy.SERVICE_EXPORT_PERMISSION,
                            ExaminationDetailAccessPolicy.SERVICE_RECONCILE_PERMISSION,
                            ExaminationDetailAccessPolicy.SERVICE_TEMPLATE_DOWNLOAD_PERMISSION,
                            ExaminationDetailAccessPolicy.SERVICE_RECORD_PERMISSION,
                            ExaminationDetailAccessPolicy.REPORT_READ_PERMISSION,
                            ExaminationDetailAccessPolicy.REPORT_EXPORT_PERMISSION))))
            .build();

    AgreementDetailResponse agreement =
        create.execute(
            org,
            CreateHealthExaminationAgreementCommand.builder()
                .configuration(
                    AgreementConfiguration.builder()
                        .agreementName("Campaign")
                        .validFrom(D4)
                        .validTo(D8)
                        .services(
                            List.of(
                                AgreementConfiguration.ServicePrice.builder()
                                    .serviceId(serviceA)
                                    .negotiatedPrice(new BigDecimal("100"))
                                    .build(),
                                AgreementConfiguration.ServicePrice.builder()
                                    .serviceId(serviceB)
                                    .negotiatedPrice(new BigDecimal("250.50"))
                                    .build()))
                        .build())
                .build(),
            actor);
    agreementId = agreement.id();
    agreementCode = agreement.agreementCode();
    agreementServiceA = agreement.services().get(0).id();
    agreementServiceB = agreement.services().get(1).id();
    p1 = insertParticipant("000000000001", "Person One");
    p2 = insertParticipant("000000000002", "Person Two");
    p3 = insertParticipant("000000000003", "Person Three");
  }

  private UUID insertParticipant(String identification, String name) {
    UUID id = UUID.randomUUID();
    participants.insertMany(
        List.of(
            HealthExaminationParticipant.create(
                AggregateId.of(id),
                AggregateId.of(org),
                AggregateId.of(agreementId),
                new Roster(
                    "NV-" + identification,
                    name,
                    LocalDate.of(1990, 1, 31),
                    "MALE",
                    IdentificationNumber.of(identification),
                    null,
                    null,
                    "Accounting",
                    "Staff"),
                null,
                null,
                Instant.parse("2026-10-01T00:00:00Z"))));
    return id;
  }

  // --- helpers ---

  private byte[] exported() {
    return export.execute(org, agreementId, principal).content();
  }

  private byte[] edited(byte[] file, Consumer<Sheet> change) {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file));
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      change.accept(workbook.getSheet("ChiTietKham"));
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Data row of the participant in the exported sheet, found by the hidden identifier column. */
  private static int rowOf(Sheet sheet, UUID participant) {
    for (int r = 2; r <= sheet.getLastRowNum(); r++)
      if (sheet.getRow(r).getCell(0).getStringCellValue().equals(participant.toString())) return r;
    throw new IllegalStateException("participant not exported");
  }

  /** The first service column follows the twelve fixed columns. */
  private static void mark(Sheet sheet, UUID participant, int serviceIndex, String value) {
    sheet.getRow(rowOf(sheet, participant)).getCell(12 + serviceIndex).setCellValue(value);
  }

  /** An open session of the agreement on the date. */
  private UUID session(LocalDate date) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.health_examination_sessions(id,organization_id,agreement_id,session_date,activity,site_type,site_address,created_by) VALUES (?,?,?,?,'ALL','ORGANIZATION_SITE',?,?)",
        id,
        org,
        agreementId,
        date,
        "Address " + id,
        actor);
    return id;
  }

  private void assign(UUID session, UUID participant, String status) {
    jdbc.update(
        "INSERT INTO public.health_examination_session_assignments(organization_id,participant_id,session_id,status,attended_at,recorded_by) VALUES (?,?,?,?,CASE WHEN ? = 'ATTENDED' THEN CURRENT_TIMESTAMP END,?)",
        org,
        participant,
        session,
        status,
        status,
        actor);
  }

  private com.ngockhanh.clinic.healthexamination.application.response
          .ExaminationDetailImportResponse
      importFile(byte[] file, UUID key) {
    return importer.execute(
        org, agreementId, new ImportExaminationDetailsCommand(file, key), principal);
  }

  private String state(UUID participant) {
    return jdbc.queryForObject(STATE_SQL, String.class, participant);
  }

  private long version(UUID participant) {
    return jdbc.queryForObject(
        "SELECT row_version FROM public.health_examination_participants WHERE id=?",
        Long.class,
        participant);
  }

  private int serviceRows(UUID participant, boolean performed) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM public.health_examination_participant_services WHERE participant_id=? AND is_performed=?",
        Integer.class,
        participant,
        performed);
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  // --- tests ---

  @Test
  void exportHoldsEveryActiveParticipantWithMaskedIdentificationAndAnAuditEvent()
      throws IOException {
    jdbc.update(
        "UPDATE public.health_examination_participants SET roster_status='CANCELLED' WHERE id=?",
        p3);
    assign(session(D4), p1, "ASSIGNED");

    byte[] file = exported();

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
      Sheet sheet = workbook.getSheet("ChiTietKham");
      assertThat(sheet.getLastRowNum()).isEqualTo(3); // header, keys, two active participants
      assertThat(sheet.getRow(0).getCell(12).getStringCellValue()).isEqualTo("Khám nội");
      assertThat(sheet.getRow(1).getCell(12).getStringCellValue())
          .isEqualTo("svc:" + agreementServiceA);
      for (int r = 2; r <= 3; r++)
        assertThat(sheet.getRow(r).getCell(7).getStringCellValue()).doesNotContain("00000000000");
      // the next session date is informational, shown for the Participant that is still expected
      assertThat(sheet.getRow(rowOf(sheet, p1)).getCell(10).getStringCellValue())
          .isEqualTo(D4.toString());
      assertThat(sheet.getRow(rowOf(sheet, p2)).getCell(10).getStringCellValue()).isEmpty();
    }
    assertThat(
            count(
                "SELECT count(*) FROM public.audit_events WHERE action='EXPORT_EXAMINATION_DETAILS'"))
        .isEqualTo(1);
  }

  @Test
  void importReconcilesChangedRowsKeepsTheRestAndIsIdempotent() {
    byte[] file =
        edited(
            exported(),
            sheet -> {
              mark(sheet, p1, 0, "X");
              mark(sheet, p1, 1, "x");
              mark(sheet, p2, 0, "X");
            });
    long p3Version = version(p3);
    UUID key = UUID.randomUUID();

    var response = importFile(file, key);

    assertThat(response.totalRows()).isEqualTo(3);
    assertThat(response.updatedParticipants()).isEqualTo(2);
    assertThat(response.unchangedParticipants()).isEqualTo(1);
    assertThat(response.performedItems()).isEqualTo(3);
    assertThat(state(p1)).isEqualTo("RECONCILED");
    assertThat(state(p2)).isEqualTo("RECONCILED");
    assertThat(state(p3)).isEqualTo("PENDING");
    assertThat(version(p3)).isEqualTo(p3Version);
    assertThat(version(p1)).isEqualTo(1);
    assertThat(serviceRows(p1, true)).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "SELECT unit_price_snapshot FROM public.health_examination_participant_services WHERE participant_id=? ORDER BY unit_price_snapshot",
                BigDecimal.class,
                p1))
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactly(new BigDecimal("100"), new BigDecimal("250.50"));
    assertThat(
            count(
                "SELECT count(*) FROM public.import_jobs WHERE import_type='HEALTH_EXAMINATION_SERVICE_RECONCILIATION' AND status='CONFIRMED' AND agreement_id=?",
                agreementId))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM public.import_rows WHERE job_id=? AND committed_resource_id IS NOT NULL",
                response.importJobId()))
        .isEqualTo(2);
    assertThat(
            count(
                "SELECT count(*) FROM public.audit_events WHERE action='IMPORT_EXAMINATION_DETAILS'"))
        .isEqualTo(1);
    // reconciling services never writes attendance: nobody is scheduled or marked as attended
    assertThat(
            count(
                "SELECT count(*) FROM public.health_examination_session_assignments WHERE organization_id=?",
                org))
        .isZero();

    var replay = importFile(file, key);

    assertThat(replay.importJobId()).isEqualTo(response.importJobId());
    assertThat(count("SELECT count(*) FROM public.import_jobs WHERE agreement_id=?", agreementId))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM public.audit_events WHERE action='IMPORT_EXAMINATION_DETAILS'"))
        .isEqualTo(1);
    assertThat(version(p1)).isEqualTo(1);
  }

  @Test
  void theSameKeyForADifferentFileIsAConflict() {
    UUID key = UUID.randomUUID();
    importFile(edited(exported(), sheet -> mark(sheet, p1, 0, "X")), key);

    assertThatThrownBy(() -> importFile(edited(exported(), sheet -> mark(sheet, p2, 0, "X")), key))
        .isInstanceOf(ConflictException.class);
    assertThat(state(p2)).isEqualTo("PENDING");
  }

  @Test
  void aStaleFileRejectsEverythingAndRollsBack() {
    byte[] stale = edited(exported(), sheet -> mark(sheet, p1, 0, "X"));
    importFile(edited(exported(), sheet -> mark(sheet, p1, 1, "X")), UUID.randomUUID());
    long p2Version = version(p2);
    byte[] twoRows =
        edited(
            stale,
            sheet -> {
              mark(sheet, p2, 0, "X"); // valid row
            });

    assertThatThrownBy(() -> importFile(twoRows, UUID.randomUUID()))
        .isInstanceOf(ConcurrentUpdateException.class);

    assertThat(state(p2)).isEqualTo("PENDING");
    assertThat(version(p2)).isEqualTo(p2Version);
    assertThat(serviceRows(p2, true)).isZero();
    assertThat(count("SELECT count(*) FROM public.import_jobs WHERE agreement_id=?", agreementId))
        .isEqualTo(1);
  }

  @Test
  void aServiceUncheckedAfterwardsKeepsItsRowAsNotPerformed() {
    importFile(
        edited(
            exported(),
            sheet -> {
              mark(sheet, p1, 0, "X");
              mark(sheet, p1, 1, "X");
            }),
        UUID.randomUUID());

    var second = importFile(edited(exported(), sheet -> mark(sheet, p1, 1, "")), UUID.randomUUID());

    assertThat(second.updatedParticipants()).isEqualTo(1);
    assertThat(serviceRows(p1, true)).isEqualTo(1);
    assertThat(serviceRows(p1, false)).isEqualTo(1);
    assertThat(state(p1)).isEqualTo("RECONCILED");
  }

  @Test
  void aCancelledParticipantOrAnEndedAgreementIsAConflict() {
    byte[] file = edited(exported(), sheet -> mark(sheet, p3, 0, "X"));
    jdbc.update(
        "UPDATE public.health_examination_participants SET roster_status='CANCELLED',row_version=row_version+1 WHERE id=?",
        p3);
    assertThatThrownBy(() -> importFile(file, UUID.randomUUID()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("cancelled");

    byte[] again = edited(exported(), sheet -> mark(sheet, p1, 0, "X"));
    jdbc.update(
        "UPDATE public.health_examination_agreements SET status='ENDED' WHERE id=?", agreementId);
    assertThatThrownBy(() -> importFile(again, UUID.randomUUID()))
        .isInstanceOf(ConflictException.class);
    assertThat(state(p1)).isEqualTo("PENDING");
  }

  @Test
  void aFileFromAnotherAgreementIsRejected() {
    byte[] file = exported();
    UUID extra = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.services(id,code,name,service_type,performing_department_id,unit_price) SELECT ?,'S3','Extra','CONSULTATION',performing_department_id,10 FROM public.services WHERE id=?",
        extra,
        serviceA);
    jdbc.update(
        "INSERT INTO public.health_examination_agreement_services(agreement_id,service_id,reference_price_snapshot,negotiated_price,display_order,active) VALUES (?,?,1,1,3,true)",
        agreementId,
        extra);

    assertThatThrownBy(() -> importFile(file, UUID.randomUUID()))
        .hasMessageContaining("does not match this agreement");
  }

  @Test
  void listAndSummaryFollowTheDerivedAttendanceAndTheFilters() {
    importFile(edited(exported(), sheet -> mark(sheet, p1, 0, "X")), UUID.randomUUID());
    UUID s4 = session(D4);
    assign(s4, p1, "ATTENDED");
    assign(s4, p2, "NO_SHOW");
    assign(s4, p3, "ATTENDED");

    var all =
        list.execute(org, agreementId, ExaminationDetailListQuery.builder().build(), principal);
    var attended =
        list.execute(
            org,
            agreementId,
            ExaminationDetailListQuery.builder()
                .attendanceStatus("ATTENDED")
                .reconciliationStatus("RECONCILED")
                .build(),
            principal);
    var noShow =
        list.execute(
            org,
            agreementId,
            ExaminationDetailListQuery.builder().attendanceStatus("NO_SHOW").build(),
            principal);
    var searched =
        list.execute(
            org,
            agreementId,
            ExaminationDetailListQuery.builder().searchKey("TWO").build(),
            principal);
    var sorted =
        list.execute(
            org,
            agreementId,
            ExaminationDetailListQuery.builder().sortKey("fullName").sortBy("DESC").size(2).build(),
            principal);

    assertThat(all.totalElements()).isEqualTo(3);
    assertThat(attended.items()).extracting("id").containsExactly(p1);
    assertThat(attended.items().get(0).performedAgreementServiceIds())
        .containsExactly(agreementServiceA);
    assertThat(attended.items().get(0).identificationNumberMasked()).doesNotContain("000000000001");
    assertThat(attended.items().get(0).lastAttendedDate()).isNotNull();
    assertThat(noShow.items()).extracting("id").containsExactly(p2);
    assertThat(searched.items()).extracting("id").containsExactly(p2);
    assertThat(sorted.items()).extracting("fullName").containsExactly("Person Two", "Person Three");
    assertThat(sorted.totalPages()).isEqualTo(2);

    var counters = summary.execute(org, agreementId, principal);
    assertThat(counters.registered()).isEqualTo(3);
    assertThat(counters.attended()).isEqualTo(2);
    assertThat(counters.noShow()).isEqualTo(1);
    assertThat(counters.unconfirmed()).isZero();
    // a no-show has no session left, so it is also waiting to be scheduled again
    assertThat(counters.unscheduled()).isEqualTo(1);
    assertThat(counters.reconciled()).isEqualTo(1);
    assertThat(counters.pendingReconciliation()).isEqualTo(1);
  }

  @Test
  void aNoShowWithAnotherSessionStillExpectedIsUnconfirmedAndAnUnscheduledOneIsCounted() {
    assign(session(D4), p1, "NO_SHOW");
    assign(session(D8), p1, "ASSIGNED");
    assign(session(D4), p2, "ASSIGNED");

    var counters = summary.execute(org, agreementId, principal);

    assertThat(counters.registered()).isEqualTo(3);
    assertThat(counters.unconfirmed()).isEqualTo(3); // nobody has attended, p3 is not scheduled yet
    assertThat(counters.noShow()).isZero();
    assertThat(counters.attended()).isZero();
    assertThat(counters.unscheduled()).isEqualTo(1); // p3 is in no session
    var row =
        list.execute(
                org,
                agreementId,
                ExaminationDetailListQuery.builder().searchKey("ONE").build(),
                principal)
            .items()
            .get(0);
    assertThat(row.nextSessionDate()).isEqualTo(D8);
    assertThat(row.attendanceStatus()).isEqualTo("UNCONFIRMED");
  }

  @Test
  void recordingOneParticipantByHandReconcilesItAndStaleFilesAndRetriesBehave() {
    byte[] oldFile = edited(exported(), sheet -> mark(sheet, p1, 0, "X"));
    var request =
        new RecordExaminationDetailCommand(Set.of(agreementServiceA, agreementServiceB), 0);

    var row = recorder.execute(org, agreementId, p1, request, principal);

    assertThat(row.rowVersion()).isEqualTo(1);
    assertThat(row.reconciliationStatus()).isEqualTo("RECONCILED");
    assertThat(row.performedAgreementServiceIds())
        .containsExactlyInAnyOrder(agreementServiceA, agreementServiceB);
    assertThat(state(p1)).isEqualTo("RECONCILED");
    assertThat(serviceRows(p1, true)).isEqualTo(2);
    assertThat(summary.execute(org, agreementId, principal).reconciled()).isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM public.audit_events WHERE action='RECORD_EXAMINATION_DETAIL'"))
        .isEqualTo(1);

    // The same request again (a lost response) is answered from the stored state, writing nothing.
    var retry = recorder.execute(org, agreementId, p1, request, principal);
    assertThat(retry.rowVersion()).isEqualTo(1);
    assertThat(version(p1)).isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM public.audit_events WHERE action='RECORD_EXAMINATION_DETAIL'"))
        .isEqualTo(1);

    // A different request with the old version is a conflict.
    assertThatThrownBy(
            () ->
                recorder.execute(
                    org,
                    agreementId,
                    p1,
                    new RecordExaminationDetailCommand(Set.of(agreementServiceA), 0),
                    principal))
        .isInstanceOf(ConcurrentUpdateException.class);
    assertThat(serviceRows(p1, true)).isEqualTo(2);

    // A file exported before the manual entry no longer matches the Participant.
    assertThatThrownBy(() -> importFile(oldFile, UUID.randomUUID()))
        .isInstanceOf(ConcurrentUpdateException.class);
  }

  @Test
  void recordingNothingForAFreshParticipantChangesNothing() {
    var row =
        recorder.execute(
            org, agreementId, p2, new RecordExaminationDetailCommand(Set.of(), 0), principal);

    assertThat(row.rowVersion()).isZero();
    assertThat(state(p2)).isEqualTo("PENDING");
    assertThat(serviceRows(p2, true)).isZero();
  }

  @Test
  void recordingACancelledParticipantOrOneOfAnotherAgreementIsRefused() {
    jdbc.update(
        "UPDATE public.health_examination_participants SET roster_status='CANCELLED' WHERE id=?",
        p3);

    assertThatThrownBy(
            () ->
                recorder.execute(
                    org,
                    agreementId,
                    p3,
                    new RecordExaminationDetailCommand(Set.of(agreementServiceA), 0),
                    principal))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () ->
                recorder.execute(
                    org,
                    agreementId,
                    UUID.randomUUID(),
                    new RecordExaminationDetailCommand(Set.of(agreementServiceA), 0),
                    principal))
        .isInstanceOf(com.ngockhanh.clinic.shared.exception.ResourceNotFoundException.class);
  }

  @Test
  void theTemplateIsTheExportUnderItsOwnNameAndImportsBackWithoutChanges() {
    importFile(edited(exported(), sheet -> mark(sheet, p1, 0, "X")), UUID.randomUUID());

    var file = template.execute(org, agreementId, principal);
    var response = importFile(file.content(), UUID.randomUUID());

    assertThat(file.fileName()).isEqualTo("mau-nhap-chi-tiet-kham-" + agreementCode + ".xlsx");
    assertThat(response.totalRows()).isEqualTo(3);
    assertThat(response.updatedParticipants()).isZero();
    assertThat(response.unchangedParticipants()).isEqualTo(3);
    assertThat(
            count(
                "SELECT count(*) FROM public.audit_events WHERE action='DOWNLOAD_EXAMINATION_DETAIL_TEMPLATE'"))
        .isEqualTo(1);
  }

  @Test
  void paymentReportCountsActiveParticipantsAtTheirPriceSnapshotAndTheWordFileMatches()
      throws IOException {
    importFile(
        edited(
            exported(),
            sheet -> {
              mark(sheet, p1, 0, "X");
              mark(sheet, p1, 1, "X");
              mark(sheet, p2, 0, "X");
              mark(sheet, p3, 0, "X");
            }),
        UUID.randomUUID());
    UUID s4 = session(D4);
    UUID s8 = session(D8);
    assign(s4, p1, "ATTENDED");
    assign(s4, p2, "ATTENDED");
    assign(s8, p1, "ATTENDED");
    // The negotiated price changes later; performed rows keep their snapshot.
    jdbc.update(
        "UPDATE public.health_examination_agreement_services SET negotiated_price=120 WHERE id=?",
        agreementServiceA);
    jdbc.update(
        "UPDATE public.health_examination_participants SET roster_status='CANCELLED' WHERE id=?",
        p3);

    var result = report.execute(org, agreementId, null, null, principal);

    assertThat(result.provisional()).isTrue();
    assertThat(result.registeredCount()).isEqualTo(2);
    assertThat(result.attendedCount()).isEqualTo(2);
    assertThat(result.reconciledCount()).isEqualTo(2);
    assertThat(result.sessionCount()).isEqualTo(2);
    assertThat(result.visitCount()).isEqualTo(3); // p1 came twice
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).serviceName()).isEqualTo("Khám nội");
    assertThat(result.items().get(0).examinedCount()).isEqualTo(2);
    assertThat(result.items().get(0).unitPrice()).isEqualByComparingTo("100");
    assertThat(result.items().get(0).amount()).isEqualByComparingTo("200");
    assertThat(result.items().get(1).examinedCount()).isEqualTo(1);
    assertThat(result.items().get(1).amount()).isEqualByComparingTo("250.50");
    assertThat(result.totalAmount()).isEqualByComparingTo("450.50");

    var file = reportDocx.execute(org, agreementId, null, null, principal);

    assertThat(file.fileName()).isEqualTo("bao-cao-thanh-toan-" + agreementCode + ".docx");
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(file.content()))) {
      var text = new StringBuilder();
      doc.getParagraphs().forEach(p -> text.append(p.getText()).append('\n'));
      doc.getTables()
          .forEach(
              t ->
                  t.getRows()
                      .forEach(
                          r ->
                              r.getTableCells()
                                  .forEach(c -> text.append(c.getText()).append('\n'))));
      assertThat(text.toString())
          .contains("(TẠM TÍNH)", "Khám nội", "450,50", "Bằng chữ: ")
          .doesNotContain("Person One", "000000000001");
    }
    assertThat(
            count("SELECT count(*) FROM public.audit_events WHERE action='EXPORT_PAYMENT_REPORT'"))
        .isEqualTo(1);
  }

  @Test
  void paymentReportLimitedToASessionDateRangeCountsOnlyThePeopleInThoseSessions() {
    importFile(
        edited(
            exported(),
            sheet -> {
              mark(sheet, p1, 0, "X");
              mark(sheet, p1, 1, "X");
              mark(sheet, p2, 0, "X");
            }),
        UUID.randomUUID());
    assign(session(D4), p1, "ATTENDED");
    assign(session(D8), p2, "ASSIGNED");

    var first = report.execute(org, agreementId, D4, D4, principal);
    var second = report.execute(org, agreementId, D8, D8, principal);
    var both = report.execute(org, agreementId, D4, D8, principal);
    var none = report.execute(org, agreementId, LocalDate.of(2026, 11, 1), null, principal);

    assertThat(first.registeredCount()).isEqualTo(1);
    assertThat(first.attendedCount()).isEqualTo(1);
    assertThat(first.rangeFrom()).isEqualTo(D4);
    assertThat(first.rangeTo()).isEqualTo(D4);
    assertThat(first.totalAmount()).isEqualByComparingTo("350.50");
    assertThat(second.registeredCount()).isEqualTo(1);
    assertThat(second.attendedCount()).isZero();
    assertThat(second.totalAmount()).isEqualByComparingTo("100");
    assertThat(both.registeredCount()).isEqualTo(2);
    assertThat(both.totalAmount()).isEqualByComparingTo("450.50");
    assertThat(none.registeredCount()).isZero();
    assertThat(none.totalAmount()).isEqualByComparingTo("0");
    assertThatThrownBy(() -> report.execute(org, agreementId, D8, D4, principal))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
