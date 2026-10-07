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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.healthexamination.application.command.ImportExaminationDetailsCommand;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailExportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailImportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationDetailRowResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentReportDocumentResponse;
import com.ngockhanh.clinic.healthexamination.application.response.PaymentSummaryReportResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ExportExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ExportPaymentSummaryReportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetExaminationSummaryUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetPaymentSummaryReportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ImportExaminationDetailsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListExaminationDetailsUseCase;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
  private final GetPaymentSummaryReportUseCase report = mock(GetPaymentSummaryReportUseCase.class);
  private final ExportPaymentSummaryReportUseCase docx =
      mock(ExportPaymentSummaryReportUseCase.class);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
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
                new ExaminationDetailController(list, summary, export, importer),
                new BatchReportController(report, docx))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private String batchBase() {
    return "/api/v1/organizations/" + organizationId + "/health-examination-batches/" + batchId;
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
            .performedBatchServiceIds(List.of())
            .rowVersion(2)
            .build();
    when(list.execute(eq(organizationId), eq(batchId), any(), eq(principal)))
        .thenReturn(new PageResponse<>(List.of(row), 1, 20, 1, 1));

    mvc.perform(
            get(batchBase() + "/examination-details")
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
    verify(list).execute(eq(organizationId), eq(batchId), query.capture(), eq(principal));
    assertThat(query.getValue().searchKey()).isEqualTo("an");
    assertThat(query.getValue().attendanceStatus()).isEqualTo("ATTENDED");
    assertThat(query.getValue().reconciliationStatus()).isEqualTo("PENDING");
    assertThat(query.getValue().sortKey()).isEqualTo("fullName");
  }

  @Test
  void listRejectsAnUnknownSortKeyBeforeTheUseCase() throws Exception {
    mvc.perform(get(batchBase() + "/examination-details").param("sortKey", "phone"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(list);
  }

  @Test
  void summaryReturnsTheCounters() throws Exception {
    when(summary.execute(organizationId, batchId, principal))
        .thenReturn(new ExaminationSummaryResponse(10, 4, 5, 1, 2, 3));

    mvc.perform(get(batchBase() + "/examination-details/summary"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.registered").value(10))
        .andExpect(jsonPath("$.data.pendingReconciliation").value(3));
  }

  @Test
  void exportReturnsAnXlsxAttachment() throws Exception {
    when(export.execute(organizationId, batchId, principal))
        .thenReturn(
            new ExaminationDetailExportResponse(new byte[] {9, 8}, "chi-tiet-kham-B1.xlsx"));

    mvc.perform(get(batchBase() + "/examination-details/export"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", XLSX))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"chi-tiet-kham-B1.xlsx\""))
        .andExpect(content().bytes(new byte[] {9, 8}));
  }

  @Test
  void importReturns201AndPassesTheWorkbookAndIdempotencyKey() throws Exception {
    UUID jobId = UUID.randomUUID();
    when(importer.execute(eq(organizationId), eq(batchId), any(), eq(principal)))
        .thenReturn(
            new ExaminationDetailImportResponse(
                jobId, batchId, 5, 3, 2, 7, Instant.parse("2026-10-07T00:00:00Z")));

    mvc.perform(
            multipart(batchBase() + "/examination-details/imports")
                .file(new MockMultipartFile("file", "detail.xlsx", XLSX, new byte[] {1, 2, 3}))
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.importJobId").value(jobId.toString()))
        .andExpect(jsonPath("$.data.updatedParticipants").value(3))
        .andExpect(jsonPath("$.data.performedItems").value(7));

    var command = ArgumentCaptor.forClass(ImportExaminationDetailsCommand.class);
    verify(importer).execute(eq(organizationId), eq(batchId), command.capture(), eq(principal));
    assertThat(command.getValue().workbook()).containsExactly(1, 2, 3);
    assertThat(command.getValue().idempotencyKey()).isEqualTo(key);
  }

  @Test
  void importRejectsANonXlsxFileBeforeTheUseCase() throws Exception {
    mvc.perform(
            multipart(batchBase() + "/examination-details/imports")
                .file(new MockMultipartFile("file", "detail.csv", "text/csv", new byte[] {1}))
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isUnsupportedMediaType());
    verifyNoInteractions(importer);
  }

  @Test
  void importRejectsAnEmptyFileAndAMissingIdempotencyKey() throws Exception {
    mvc.perform(
            multipart(batchBase() + "/examination-details/imports")
                .file(new MockMultipartFile("file", "detail.xlsx", XLSX, new byte[0]))
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart(batchBase() + "/examination-details/imports")
                .file(new MockMultipartFile("file", "detail.xlsx", XLSX, new byte[] {1})))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(importer);
  }

  @Test
  void importMapsAConflictTo409() throws Exception {
    when(importer.execute(any(), any(), any(), any()))
        .thenThrow(new ConflictException("The file does not match this batch"));

    mvc.perform(
            multipart(batchBase() + "/examination-details/imports")
                .file(new MockMultipartFile("file", "detail.xlsx", XLSX, new byte[] {1}))
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isConflict());
  }

  @Test
  void paymentSummaryReturnsTheReport() throws Exception {
    var body =
        PaymentSummaryReportResponse.builder()
            .batchId(batchId)
            .batchCode("B1")
            .batchName("Campaign")
            .batchStatus("READY")
            .provisional(true)
            .registeredCount(3)
            .attendedCount(2)
            .reconciledCount(1)
            .items(List.of())
            .totalAmount(new BigDecimal("0.00"))
            .generatedAt(Instant.parse("2026-10-07T00:00:00Z"))
            .build();
    when(report.execute(organizationId, batchId, principal)).thenReturn(body);

    mvc.perform(get(batchBase() + "/reports/payment-summary"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.provisional").value(true))
        .andExpect(jsonPath("$.data.registeredCount").value(3))
        .andExpect(jsonPath("$.data.batchCode").value("B1"));
  }

  @Test
  void paymentSummaryDocxReturnsADocxAttachment() throws Exception {
    when(docx.execute(organizationId, batchId, principal))
        .thenReturn(
            new PaymentReportDocumentResponse(new byte[] {7}, "bao-cao-thanh-toan-B1.docx"));

    mvc.perform(get(batchBase() + "/reports/payment-summary/docx"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", DOCX))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition", "attachment; filename=\"bao-cao-thanh-toan-B1.docx\""))
        .andExpect(content().bytes(new byte[] {7}));
  }
}
