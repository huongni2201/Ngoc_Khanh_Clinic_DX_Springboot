package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.healthexamination.application.command.ImportExaminationDetailsCommand;
import com.ngockhanh.clinic.healthexamination.application.command.RecordExaminationDetailCommand;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailExportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailImportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailRowResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentReportDocumentResponse;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentSummaryReportResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ExportExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ExportPaymentSummaryReportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetExaminationDetailImportTemplateUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetExaminationSummaryUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetPaymentSummaryReportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ImportExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.RecordExaminationDetailUseCase;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExaminationDetailAndReportControllerTest {
  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final String DOCX =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  private final ListExaminationDetailsUseCase list = mock(ListExaminationDetailsUseCase.class);
  private final GetExaminationSummaryUseCase summary = mock(GetExaminationSummaryUseCase.class);
  private final ExportExaminationDetailsUseCase export =
      mock(ExportExaminationDetailsUseCase.class);
  private final ImportExaminationDetailsUseCase importer =
      mock(ImportExaminationDetailsUseCase.class);
  private final GetExaminationDetailImportTemplateUseCase template =
      mock(GetExaminationDetailImportTemplateUseCase.class);
  private final RecordExaminationDetailUseCase recorder =
      mock(RecordExaminationDetailUseCase.class);
  private final GetPaymentSummaryReportUseCase report = mock(GetPaymentSummaryReportUseCase.class);
  private final ExportPaymentSummaryReportUseCase docx =
      mock(ExportPaymentSummaryReportUseCase.class);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
  private final UUID key = UUID.randomUUID();
  private UserPrincipal principal;
  private MockMvc mvc;

  @BeforeEach
  void authenticatedStaffAndStandaloneMvc() {
    principal = ParticipantFixtures.staff();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "test", List.of()));
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ExaminationDetailController(
                    list, summary, export, importer, template, recorder),
                new AgreementReportController(report, docx))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private String agreementBase() {
    return "/api/v1/organizations/" + organizationId + "/agreements/" + agreementId;
  }

  @Test
  void listPassesTheQueryAndReturnsANoStorePage() throws Exception {
    UUID participantId = UUID.randomUUID();
    var row =
        ExaminationDetailRowResponse.builder()
            .id(participantId)
            .participantCode("P1")
            .fullName("Synthetic Person")
            .identificationNumberMasked("*****6789")
            .attendanceStatus("ATTENDED")
            .reconciliationStatus("PENDING")
            .performedAgreementServiceIds(List.of())
            .rowVersion(2)
            .build();
    when(list.execute(eq(organizationId), eq(agreementId), any(), eq(principal)))
        .thenReturn(new PageResponse<>(List.of(row), 1, 20, 1, 1));

    mvc.perform(
            get(agreementBase() + "/examination-details")
                .param("page", "1")
                .param("size", "20")
                .param("searchKey", "an")
                .param("attendanceStatus", "ATTENDED")
                .param("reconciliationStatus", "PENDING")
                .param("sortKey", "fullName")
                .param("sortBy", "DESC"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.items[0].id").value(participantId.toString()))
        .andExpect(jsonPath("$.data.items[0].identificationNumberMasked").value("*****6789"))
        .andExpect(jsonPath("$.data.items[0].rowVersion").value(2));

    var query = ArgumentCaptor.forClass(ExaminationDetailListQuery.class);
    verify(list).execute(eq(organizationId), eq(agreementId), query.capture(), eq(principal));
    assertThat(query.getValue().searchKey()).isEqualTo("an");
    assertThat(query.getValue().attendanceStatus()).isEqualTo("ATTENDED");
    assertThat(query.getValue().reconciliationStatus()).isEqualTo("PENDING");
    assertThat(query.getValue().sortKey()).isEqualTo("fullName");
  }

  @Test
  void listRejectsAnUnknownSortKeyBeforeTheUseCase() throws Exception {
    mvc.perform(get(agreementBase() + "/examination-details").param("sortKey", "phone"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(list);
  }

  @Test
  void summaryReturnsTheCounters() throws Exception {
    when(summary.execute(organizationId, agreementId, principal))
        .thenReturn(new ExaminationSummaryResponse(10, 4, 5, 1, 2, 3, 3));

    mvc.perform(get(agreementBase() + "/examination-details/summary"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.registered").value(10))
        .andExpect(jsonPath("$.data.pendingReconciliation").value(3));
  }

  @Test
  void exportReturnsAnXlsxAttachment() throws Exception {
    when(export.execute(organizationId, agreementId, principal))
        .thenReturn(
            new ExaminationDetailExportResponse(new byte[] {9, 8}, "chi-tiet-kham-B1.xlsx"));

    mvc.perform(get(agreementBase() + "/examination-details/export"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", XLSX))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"chi-tiet-kham-B1.xlsx\""))
        .andExpect(content().bytes(new byte[] {9, 8}));
  }

  @Test
  void importTemplateReturnsAnXlsxAttachmentNamedAsATemplate() throws Exception {
    when(template.execute(organizationId, agreementId, principal))
        .thenReturn(
            new ExaminationDetailExportResponse(
                new byte[] {7, 8, 9}, "mau-nhap-chi-tiet-kham-B1.xlsx"));

    mvc.perform(get(agreementBase() + "/examination-details/import-template"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(XLSX))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    org.hamcrest.Matchers.containsString("mau-nhap-chi-tiet-kham-B1.xlsx")))
        .andExpect(content().bytes(new byte[] {7, 8, 9}));
  }

  @Test
  void importTemplateOfAnUnknownAgreementIs404() throws Exception {
    when(template.execute(organizationId, agreementId, principal))
        .thenThrow(new ResourceNotFoundException("Health examination agreement"));

    mvc.perform(get(agreementBase() + "/examination-details/import-template"))
        .andExpect(status().isNotFound());
  }

  private String recordUrl(UUID participantId) {
    return agreementBase() + "/examination-details/" + participantId;
  }

  private static ExaminationDetailRowResponse savedRow(UUID participantId) {
    return ExaminationDetailRowResponse.builder()
        .id(participantId)
        .fullName("Synthetic Person")
        .identificationNumberMasked("*****6789")
        .attendanceStatus("ATTENDED")
        .lastAttendedDate(LocalDate.of(2026, 10, 5))
        .reconciliationStatus("RECONCILED")
        .performedAgreementServiceIds(List.of())
        .rowVersion(4)
        .build();
  }

  @Test
  void recordMapsTheBodyToTheCommandAndReturnsTheSavedRow() throws Exception {
    UUID participantId = UUID.randomUUID();
    UUID service = UUID.randomUUID();
    when(recorder.execute(
            eq(organizationId), eq(agreementId), eq(participantId), any(), eq(principal)))
        .thenReturn(savedRow(participantId));

    mvc.perform(
            put(recordUrl(participantId))
                .contentType("application/json")
                .content(
                    """
                    {"performedAgreementServiceIds":["%s"],"rowVersion":3}
                    """
                        .formatted(service)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.id").value(participantId.toString()))
        .andExpect(jsonPath("$.data.rowVersion").value(4))
        .andExpect(jsonPath("$.data.reconciliationStatus").value("RECONCILED"));

    var command = ArgumentCaptor.forClass(RecordExaminationDetailCommand.class);
    verify(recorder)
        .execute(
            eq(organizationId),
            eq(agreementId),
            eq(participantId),
            command.capture(),
            eq(principal));
    assertThat(command.getValue().performedAgreementServiceIds()).isEqualTo(Set.of(service));
    assertThat(command.getValue().rowVersion()).isEqualTo(3);
  }

  @Test
  void recordAcceptsAnEmptyServiceList() throws Exception {
    UUID participantId = UUID.randomUUID();
    when(recorder.execute(any(), any(), any(), any(), any())).thenReturn(savedRow(participantId));

    mvc.perform(
            put(recordUrl(participantId))
                .contentType("application/json")
                .content("{\"performedAgreementServiceIds\":[],\"rowVersion\":0}"))
        .andExpect(status().isOk());

    var command = ArgumentCaptor.forClass(RecordExaminationDetailCommand.class);
    verify(recorder).execute(any(), any(), any(), command.capture(), any());
    assertThat(command.getValue().performedAgreementServiceIds()).isEmpty();
  }

  @Test
  void recordRejectsAnInvalidBodyBeforeTheUseCase() throws Exception {
    UUID participantId = UUID.randomUUID();
    UUID service = UUID.randomUUID();
    for (String body :
        List.of(
            "{}",
            // duplicated service
            "{\"performedAgreementServiceIds\":[\""
                + service
                + "\",\""
                + service
                + "\"],\"rowVersion\":0}",
            // missing and negative row version
            "{\"performedAgreementServiceIds\":[]}",
            "{\"performedAgreementServiceIds\":[],\"rowVersion\":-1}"))
      mvc.perform(put(recordUrl(participantId)).contentType("application/json").content(body))
          .andExpect(status().isBadRequest());
    verifyNoInteractions(recorder);
  }

  @Test
  void recordMapsAStaleVersionAndAnUnknownParticipantTo409And404() throws Exception {
    UUID participantId = UUID.randomUUID();
    String body = "{\"performedAgreementServiceIds\":[],\"rowVersion\":1}";
    when(recorder.execute(any(), any(), any(), any(), any()))
        .thenThrow(new ConcurrentUpdateException("PARTICIPANT_CHANGED", "Participant was changed"))
        .thenThrow(new ResourceNotFoundException("Participant"));

    mvc.perform(put(recordUrl(participantId)).contentType("application/json").content(body))
        .andExpect(status().isConflict());
    mvc.perform(put(recordUrl(participantId)).contentType("application/json").content(body))
        .andExpect(status().isNotFound());
  }

  @Test
  void importReturns201AndPassesTheWorkbookAndIdempotencyKey() throws Exception {
    UUID jobId = UUID.randomUUID();
    when(importer.execute(eq(organizationId), eq(agreementId), any(), eq(principal)))
        .thenReturn(
            new ExaminationDetailImportResponse(
                jobId, agreementId, 5, 3, 2, 7, Instant.parse("2026-10-07T00:00:00Z")));

    mvc.perform(
            multipart(agreementBase() + "/examination-details/imports")
                .file(new MockMultipartFile("file", "detail.xlsx", XLSX, new byte[] {1, 2, 3}))
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.importJobId").value(jobId.toString()))
        .andExpect(jsonPath("$.data.updatedParticipants").value(3))
        .andExpect(jsonPath("$.data.performedItems").value(7));

    var command = ArgumentCaptor.forClass(ImportExaminationDetailsCommand.class);
    verify(importer).execute(eq(organizationId), eq(agreementId), command.capture(), eq(principal));
    assertThat(command.getValue().workbook()).containsExactly(1, 2, 3);
    assertThat(command.getValue().idempotencyKey()).isEqualTo(key);
  }

  @Test
  void importRejectsANonXlsxFileBeforeTheUseCase() throws Exception {
    mvc.perform(
            multipart(agreementBase() + "/examination-details/imports")
                .file(new MockMultipartFile("file", "detail.csv", "text/csv", new byte[] {1}))
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isUnsupportedMediaType());
    verifyNoInteractions(importer);
  }

  @Test
  void importRejectsAnEmptyFileAndAMissingIdempotencyKey() throws Exception {
    mvc.perform(
            multipart(agreementBase() + "/examination-details/imports")
                .file(new MockMultipartFile("file", "detail.xlsx", XLSX, new byte[0]))
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart(agreementBase() + "/examination-details/imports")
                .file(new MockMultipartFile("file", "detail.xlsx", XLSX, new byte[] {1})))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(importer);
  }

  @Test
  void importMapsAConflictTo409() throws Exception {
    when(importer.execute(any(), any(), any(), any()))
        .thenThrow(new ConflictException("The file does not match this agreement"));

    mvc.perform(
            multipart(agreementBase() + "/examination-details/imports")
                .file(new MockMultipartFile("file", "detail.xlsx", XLSX, new byte[] {1}))
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isConflict());
  }

  @Test
  void paymentSummaryReturnsTheReport() throws Exception {
    var body =
        PaymentSummaryReportResponse.builder()
            .agreementId(agreementId)
            .agreementCode("B1")
            .agreementName("Campaign")
            .agreementStatus("ACTIVE")
            .provisional(true)
            .registeredCount(3)
            .attendedCount(2)
            .reconciledCount(1)
            .items(List.of())
            .totalAmount(new BigDecimal("0.00"))
            .generatedAt(Instant.parse("2026-10-07T00:00:00Z"))
            .build();
    when(report.execute(organizationId, agreementId, null, null, principal)).thenReturn(body);

    mvc.perform(get(agreementBase() + "/reports/payment-summary"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.provisional").value(true))
        .andExpect(jsonPath("$.data.registeredCount").value(3))
        .andExpect(jsonPath("$.data.agreementCode").value("B1"));
  }

  @Test
  void paymentSummaryDocxReturnsADocxAttachment() throws Exception {
    when(docx.execute(organizationId, agreementId, null, null, principal))
        .thenReturn(
            new PaymentReportDocumentResponse(new byte[] {7}, "bao-cao-thanh-toan-B1.docx"));

    mvc.perform(get(agreementBase() + "/reports/payment-summary/docx"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", DOCX))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition", "attachment; filename=\"bao-cao-thanh-toan-B1.docx\""))
        .andExpect(content().bytes(new byte[] {7}));
  }

  @Test
  void paymentSummaryPassesTheOptionalSessionDateRange() throws Exception {
    var body =
        PaymentSummaryReportResponse.builder()
            .agreementId(agreementId)
            .provisional(true)
            .items(List.of())
            .totalAmount(new BigDecimal("0.00"))
            .generatedAt(Instant.parse("2026-10-07T00:00:00Z"))
            .build();
    when(report.execute(any(), any(), any(), any(), any())).thenReturn(body);

    mvc.perform(
            get(agreementBase() + "/reports/payment-summary")
                .param("from", "2026-10-04")
                .param("to", "2026-10-05"))
        .andExpect(status().isOk());
    mvc.perform(get(agreementBase() + "/reports/payment-summary").param("from", "04/10/2026"))
        .andExpect(status().isBadRequest());

    verify(report)
        .execute(
            organizationId,
            agreementId,
            LocalDate.of(2026, 10, 4),
            LocalDate.of(2026, 10, 5),
            principal);
  }
}
