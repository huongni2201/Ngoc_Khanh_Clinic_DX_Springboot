package com.ngockhanh.clinic.healthexamination.application.usecase;

import static com.ngockhanh.clinic.healthexamination.BatchFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.audit.application.port.AuditWriter;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.healthexamination.application.command.ImportExaminationDetailsCommand;
import com.ngockhanh.clinic.healthexamination.application.port.ExaminationDetailExcelReader;
import com.ngockhanh.clinic.healthexamination.application.port.ExaminationDetailExcelWriter;
import com.ngockhanh.clinic.healthexamination.application.port.ExaminationDetailReader;
import com.ngockhanh.clinic.healthexamination.application.port.PaymentReportDocumentWriter;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailCriteria;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailExportData;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailListQuery;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailPage;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailWorkbook;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationSummary;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentAggregates;
import com.ngockhanh.clinic.healthexamination.application.query.PaymentReportDocument;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailAccessPolicy;
import com.ngockhanh.clinic.healthexamination.application.service.ExaminationDetailImportCommitter;
import com.ngockhanh.clinic.healthexamination.application.service.PaymentSummaryReportAssembler;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatch;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationReceipt;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The six use cases of the examination detail screen and the payment report. */
class ExaminationDetailUseCasesTest {
  private static final String READ = ExaminationDetailAccessPolicy.SERVICE_READ_PERMISSION;
  private static final String RECONCILE = ExaminationDetailAccessPolicy.SERVICE_RECONCILE_PERMISSION;
  private static final String REPORT = ExaminationDetailAccessPolicy.REPORT_READ_PERMISSION;
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-07T01:02:03.456789Z"), ZoneOffset.UTC);

  private final ExaminationDetailAccessPolicy access = new ExaminationDetailAccessPolicy();
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
  private final ExaminationDetailReader reader = mock(ExaminationDetailReader.class);
  private final ServiceCatalogQuery catalog = mock(ServiceCatalogQuery.class);
  private final AuditWriter audit = mock(AuditWriter.class);
  private final ExaminationDetailExcelWriter excelWriter = mock(ExaminationDetailExcelWriter.class);
  private final ExaminationDetailExcelReader excelReader = mock(ExaminationDetailExcelReader.class);
  private final PaymentReportDocumentWriter wordWriter = mock(PaymentReportDocumentWriter.class);
  private final ExaminationDetailImportCommitter committer = mock(ExaminationDetailImportCommitter.class);

  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final UUID catalogId = UUID.randomUUID();
  private HealthExaminationBatch batch;

  @BeforeEach
  void aDraftBatchWithOneCatalogService() {
    batch = draftBatch(organizationId, batchId, 3, catalogId);
    when(batches.findDetails(organizationId, batchId, false)).thenReturn(Optional.of(details(batch)));
    when(organizations.findById(new AggregateId(organizationId)))
        .thenReturn(Optional.of(organization(organizationId)));
    when(catalog.findByIds(any()))
        .thenReturn(
            List.of(new ServiceCatalogQuery.Service(catalogId, "KN", "Khám nội", true, new BigDecimal("200"))));
  }

  private static UserPrincipal staff(String... permissions) {
    return ParticipantFixtures.staff(permissions);
  }

  private static ExaminationDetailRow detailRow(UUID id, String identification) {
    return new ExaminationDetailRow(
        id,
        "NV-1",
        "Synthetic Person",
        LocalDate.of(1990, 1, 31),
        "MALE",
        identification,
        "Accounting",
        "Staff",
        FIRST_DAY,
        "UNCONFIRMED",
        null,
        "PENDING",
        List.of(),
        5);
  }

  // --- access policy ---

  @Test
  void onlyAStaffAccountWithTheExactPermissionPasses() {
    access.requireServiceRead(staff(READ));
    access.requireServiceReconcile(staff(RECONCILE));
    access.requireReportRead(staff(REPORT));

    assertThatThrownBy(() -> access.requireServiceRead(staff(RECONCILE, REPORT)))
        .isInstanceOfSatisfying(
            ApplicationException.class,
            e -> assertThat(e.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
    assertThatThrownBy(() -> access.requireServiceReconcile(staff(READ, REPORT)))
        .isInstanceOf(ApplicationException.class);
    assertThatThrownBy(() -> access.requireReportRead(staff(READ, RECONCILE)))
        .isInstanceOf(ApplicationException.class);
    assertThatThrownBy(() -> access.requireServiceRead(null)).isInstanceOf(ApplicationException.class);
    assertThatThrownBy(() -> access.requireServiceRead(staff()))
        .isInstanceOf(ApplicationException.class);
  }

  @Test
  void aPatientAccountNeverPassesEvenWithThePermission() {
    var patient =
        UserPrincipal.builder()
            .userId(UUID.randomUUID())
            .patientId(UUID.randomUUID())
            .username("patient")
            .principalType("PATIENT")
            .roleAssignments(
                List.of(new UserPrincipal.Assignment(UUID.randomUUID(), "USER", List.of(READ, RECONCILE, REPORT))))
            .build();

    assertThatThrownBy(() -> access.requireServiceRead(patient)).isInstanceOf(ApplicationException.class);
    assertThatThrownBy(() -> access.requireServiceReconcile(patient)).isInstanceOf(ApplicationException.class);
    assertThatThrownBy(() -> access.requireReportRead(patient)).isInstanceOf(ApplicationException.class);
  }

  // --- list ---

  private ListExaminationDetailsUseCase list() {
    return new ListExaminationDetailsUseCase(access, reader);
  }

  @Test
  void listDefaultsToTheActiveRosterAndMasksTheIdentificationNumber() {
    UUID participantId = UUID.randomUUID();
    when(reader.readPage(eq(organizationId), eq(batchId), any()))
        .thenReturn(Optional.of(new ExaminationDetailPage(List.of(detailRow(participantId, "012345678901")), 41)));

    var page =
        list()
            .execute(
                organizationId,
                batchId,
                ExaminationDetailListQuery.builder().page(2).size(20).searchKey(" 50%_off ").build(),
                staff(READ));

    var criteria = ArgumentCaptor.forClass(ExaminationDetailCriteria.class);
    verify(reader).readPage(eq(organizationId), eq(batchId), criteria.capture());
    assertThat(criteria.getValue().offset()).isEqualTo(20);
    assertThat(criteria.getValue().limit()).isEqualTo(20);
    assertThat(criteria.getValue().rosterStatus()).isEqualTo("ACTIVE");
    assertThat(criteria.getValue().searchPattern()).isEqualTo("%50\\%\\_off%");
    assertThat(page.totalElements()).isEqualTo(41);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.items().get(0).id()).isEqualTo(participantId);
    assertThat(page.items().get(0).identificationNumberMasked()).doesNotContain("012345678901");
  }

  @Test
  void listPassesTheStatusFiltersAndSortOfTheCaller() {
    when(reader.readPage(any(), any(), any()))
        .thenReturn(Optional.of(new ExaminationDetailPage(List.of(), 0)));

    list()
        .execute(
            organizationId,
            batchId,
            ExaminationDetailListQuery.builder()
                .attendanceStatus("ATTENDED")
                .reconciliationStatus("PENDING")
                .rosterStatus("CANCELLED")
                .sortKey("fullName")
                .sortBy("desc")
                .build(),
            staff(READ));

    var criteria = ArgumentCaptor.forClass(ExaminationDetailCriteria.class);
    verify(reader).readPage(any(), any(), criteria.capture());
    assertThat(criteria.getValue().attendanceStatus()).isEqualTo("ATTENDED");
    assertThat(criteria.getValue().reconciliationStatus()).isEqualTo("PENDING");
    assertThat(criteria.getValue().rosterStatus()).isEqualTo("CANCELLED");
    assertThat(criteria.getValue().sortKey()).isEqualTo("fullName");
    assertThat(criteria.getValue().sortBy()).isEqualTo("DESC");
  }

  @Test
  void listRejectsValuesOutsideTheAllowlistsBeforeReading() {
    var use = list();
    for (var bad :
        List.of(
            ExaminationDetailListQuery.builder().sortKey("phone").build(),
            ExaminationDetailListQuery.builder().sortBy("sideways").build(),
            ExaminationDetailListQuery.builder().attendanceStatus("PRESENT").build(),
            ExaminationDetailListQuery.builder().reconciliationStatus("DONE").build(),
            ExaminationDetailListQuery.builder().rosterStatus("GONE").build(),
            ExaminationDetailListQuery.builder().page(0).build(),
            ExaminationDetailListQuery.builder().size(0).build(),
            ExaminationDetailListQuery.builder().size(10_000).build(),
            ExaminationDetailListQuery.builder().searchKey("x".repeat(500)).build()))
      assertThatThrownBy(() -> use.execute(organizationId, batchId, bad, staff(READ)))
          .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(reader);
  }

  @Test
  void listChecksThePermissionFirstAndReportsAnUnknownBatch() {
    var query = ExaminationDetailListQuery.builder().build();
    assertThatThrownBy(() -> list().execute(organizationId, batchId, query, staff(REPORT)))
        .isInstanceOf(ApplicationException.class);
    verifyNoInteractions(reader);

    when(reader.readPage(any(), any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> list().execute(organizationId, batchId, query, staff(READ)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // --- summary ---

  @Test
  void summaryReturnsTheCountersAndRequiresThePermission() {
    when(reader.summarize(organizationId, batchId))
        .thenReturn(Optional.of(new ExaminationSummary(10, 4, 5, 1, 2, 3)));
    var use = new GetExaminationSummaryUseCase(access, reader);

    var summary = use.execute(organizationId, batchId, staff(READ));

    assertThat(summary.registered()).isEqualTo(10);
    assertThat(summary.pendingReconciliation()).isEqualTo(3);
    assertThatThrownBy(() -> use.execute(organizationId, batchId, staff(RECONCILE)))
        .isInstanceOf(ApplicationException.class);
    when(reader.summarize(organizationId, batchId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> use.execute(organizationId, batchId, staff(READ)))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // --- export ---

  private ExportExaminationDetailsUseCase exporter() {
    return new ExportExaminationDetailsUseCase(
        access, organizations, batches, reader, catalog, excelWriter, audit, CLOCK);
  }

  @Test
  void exportMasksTheIdentificationNumberNamesTheColumnsAndAuditsTheExport() {
    UUID participantId = UUID.randomUUID();
    when(reader.readAllActive(organizationId, batchId))
        .thenReturn(Optional.of(List.of(detailRow(participantId, "012345678901"))));
    when(excelWriter.write(any())).thenReturn(new byte[] {1, 2, 3});
    var principal = staff(READ);

    var file = exporter().execute(organizationId, batchId, principal);

    assertThat(file.content()).containsExactly(1, 2, 3);
    assertThat(file.fileName()).isEqualTo("chi-tiet-kham-B1.xlsx");
    var data = ArgumentCaptor.forClass(ExaminationDetailExportData.class);
    verify(excelWriter).write(data.capture());
    assertThat(data.getValue().batchId()).isEqualTo(batchId);
    assertThat(data.getValue().exportedAt()).isEqualTo(Instant.parse("2026-10-07T01:02:03.456Z"));
    assertThat(data.getValue().services()).hasSize(1);
    assertThat(data.getValue().services().get(0).batchServiceId()).isEqualTo(batch.services().get(0).id().value());
    assertThat(data.getValue().services().get(0).label()).isEqualTo("Khám nội");
    assertThat(data.getValue().rows().get(0).identificationNumber()).doesNotContain("012345678901");
    verify(audit)
        .record(
            eq(principal.userId()),
            eq("EXPORT_EXAMINATION_DETAILS"),
            eq("HEALTH_EXAMINATION_BATCH"),
            eq(batchId),
            eq(null),
            any());
  }

  @Test
  void exportWithoutThePermissionOrForAnUnknownBatchWritesNothing() {
    assertThatThrownBy(() -> exporter().execute(organizationId, batchId, staff(REPORT)))
        .isInstanceOf(ApplicationException.class);
    when(reader.readAllActive(organizationId, batchId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> exporter().execute(organizationId, batchId, staff(READ)))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(excelWriter, audit);
  }

  // --- import ---

  private ImportExaminationDetailsUseCase importer() {
    return new ImportExaminationDetailsUseCase(access, excelReader, committer);
  }

  @Test
  void importParsesTheFileAndHandsTheRowsAndAFingerprintToTheCommitter() {
    UUID participantId = UUID.randomUUID();
    UUID serviceId = batch.services().get(0).id().value();
    var parsed =
        new ExaminationDetailWorkbook(
            1,
            Set.of(serviceId),
            List.of(
                new com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailImportRow(
                    3, participantId, 5, null, Set.of(serviceId))));
    when(excelReader.read(any())).thenReturn(parsed);
    UUID jobId = UUID.randomUUID();
    var receipt = new ServiceReconciliationReceipt(jobId, batchId, 1, 1, 0, 1, Instant.parse("2026-10-07T00:00:00Z"));
    when(committer.commit(any())).thenReturn(new ExaminationDetailImportCommitter.Outcome(receipt, false));
    UUID key = UUID.randomUUID();
    var principal = staff(RECONCILE);

    var response =
        importer()
            .execute(organizationId, batchId, new ImportExaminationDetailsCommand(new byte[] {1, 2}, key), principal);

    assertThat(response.importJobId()).isEqualTo(jobId);
    assertThat(response.updatedParticipants()).isEqualTo(1);
    var request = ArgumentCaptor.forClass(ExaminationDetailImportCommitter.Request.class);
    verify(committer).commit(request.capture());
    assertThat(request.getValue().organizationId()).isEqualTo(organizationId);
    assertThat(request.getValue().batchId()).isEqualTo(batchId);
    assertThat(request.getValue().idempotencyKey()).isEqualTo(key);
    assertThat(request.getValue().actorId()).isEqualTo(principal.userId());
    assertThat(request.getValue().declaredServiceIds()).containsExactly(serviceId);
    assertThat(request.getValue().rows()).hasSize(1);
    assertThat(request.getValue().fingerprint()).hasSize(32);
  }

  @Test
  void importFingerprintDependsOnTheFileAndIgnoresNothingElse() {
    when(excelReader.read(any()))
        .thenReturn(new ExaminationDetailWorkbook(1, Set.of(), List.of()));
    when(committer.commit(any()))
        .thenReturn(
            new ExaminationDetailImportCommitter.Outcome(
                new ServiceReconciliationReceipt(UUID.randomUUID(), batchId, 0, 0, 0, 0, Instant.EPOCH), false));
    UUID key = UUID.randomUUID();
    var principal = staff(RECONCILE);

    importer().execute(organizationId, batchId, new ImportExaminationDetailsCommand(new byte[] {1}, key), principal);
    importer().execute(organizationId, batchId, new ImportExaminationDetailsCommand(new byte[] {1}, key), principal);
    importer().execute(organizationId, batchId, new ImportExaminationDetailsCommand(new byte[] {2}, key), principal);

    var requests = ArgumentCaptor.forClass(ExaminationDetailImportCommitter.Request.class);
    verify(committer, org.mockito.Mockito.times(3)).commit(requests.capture());
    var all = requests.getAllValues();
    assertThat(all.get(0).fingerprint()).isEqualTo(all.get(1).fingerprint());
    assertThat(all.get(0).fingerprint()).isNotEqualTo(all.get(2).fingerprint());
  }

  @Test
  void importChecksThePermissionAndTheInputBeforeParsing() {
    UUID key = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                importer()
                    .execute(organizationId, batchId, new ImportExaminationDetailsCommand(new byte[] {1}, key), staff(READ)))
        .isInstanceOf(ApplicationException.class);
    var reconciler = staff(RECONCILE);
    assertThatThrownBy(
            () -> importer().execute(organizationId, batchId, new ImportExaminationDetailsCommand(new byte[0], key), reconciler))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> importer().execute(organizationId, batchId, new ImportExaminationDetailsCommand(new byte[] {1}, null), reconciler))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> importer().execute(null, batchId, new ImportExaminationDetailsCommand(new byte[] {1}, key), reconciler))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(excelReader, committer);
  }

  // --- payment report ---

  private PaymentAggregates aggregates() {
    return new PaymentAggregates(
        new PaymentAggregates.ParticipantCounts(5, 3, 2),
        List.of(new PaymentAggregates.PerformedService(batch.services().get(0).id().value(), new BigDecimal("100"), 3)));
  }

  @Test
  void theReportAndTheWordDocumentShareOneSetOfFigures() {
    when(reader.readPaymentAggregates(organizationId, batchId)).thenReturn(Optional.of(aggregates()));
    when(wordWriter.write(any())).thenReturn(new byte[] {7});
    var assembler = new PaymentSummaryReportAssembler();
    var principal = staff(REPORT);

    var report =
        new GetPaymentSummaryReportUseCase(access, batches, reader, catalog, assembler, CLOCK)
            .execute(organizationId, batchId, principal);
    var file =
        new ExportPaymentSummaryReportUseCase(
                access, organizations, batches, reader, catalog, assembler, wordWriter, audit, CLOCK)
            .execute(organizationId, batchId, principal);

    assertThat(report.totalAmount()).isEqualByComparingTo("300");
    assertThat(report.provisional()).isTrue();
    assertThat(report.items().get(0).serviceName()).isEqualTo("Khám nội");
    assertThat(file.fileName()).isEqualTo("bao-cao-thanh-toan-B1.docx");
    var document = ArgumentCaptor.forClass(PaymentReportDocument.class);
    verify(wordWriter).write(document.capture());
    assertThat(document.getValue().report()).isEqualTo(report);
    assertThat(document.getValue().organizationName()).isEqualTo("Clinic Partner");
    assertThat(document.getValue().startDate()).isEqualTo(FIRST_DAY);
    assertThat(document.getValue().siteName()).isEqualTo("Clinic");
    verify(audit)
        .record(
            eq(principal.userId()),
            eq("EXPORT_PAYMENT_REPORT"),
            eq("HEALTH_EXAMINATION_BATCH"),
            eq(batchId),
            eq(null),
            any());
  }

  @Test
  void aFinalizedBatchReportIsNotProvisional() {
    batch = batch(organizationId, batchId, BatchStatus.FINALIZED, 3, null, catalogId);
    when(batches.findDetails(organizationId, batchId, false)).thenReturn(Optional.of(details(batch)));
    when(reader.readPaymentAggregates(organizationId, batchId)).thenReturn(Optional.of(aggregates()));

    var report =
        new GetPaymentSummaryReportUseCase(access, batches, reader, catalog, new PaymentSummaryReportAssembler(), CLOCK)
            .execute(organizationId, batchId, staff(REPORT));

    assertThat(report.provisional()).isFalse();
    assertThat(report.batchStatus()).isEqualTo("FINALIZED");
  }

  @Test
  void reportUseCasesNeedThePermissionAndAKnownBatch() {
    var assembler = new PaymentSummaryReportAssembler();
    var get = new GetPaymentSummaryReportUseCase(access, batches, reader, catalog, assembler, CLOCK);
    var export =
        new ExportPaymentSummaryReportUseCase(
            access, organizations, batches, reader, catalog, assembler, wordWriter, audit, CLOCK);
    assertThatThrownBy(() -> get.execute(organizationId, batchId, staff(READ)))
        .isInstanceOf(ApplicationException.class);
    assertThatThrownBy(() -> export.execute(organizationId, batchId, staff(READ)))
        .isInstanceOf(ApplicationException.class);

    when(reader.readPaymentAggregates(organizationId, batchId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> get.execute(organizationId, batchId, staff(REPORT)))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> export.execute(organizationId, batchId, staff(REPORT)))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(wordWriter, never()).write(any());
    verifyNoInteractions(audit);
    assertThat(Map.of()).isEmpty();
  }
}
