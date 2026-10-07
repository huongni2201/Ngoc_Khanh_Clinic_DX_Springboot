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
import com.ngockhanh.clinic.healthexamination.application.command.ImportParticipantsCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportTemplateResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetParticipantImportTemplateUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ImportParticipantsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListParticipantsUseCase;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import com.ngockhanh.clinic.shared.web.PageResponse;
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

class BatchParticipantControllerTest {
  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final ListParticipantsUseCase list = mock(ListParticipantsUseCase.class);
  private final GetParticipantImportTemplateUseCase template =
      mock(GetParticipantImportTemplateUseCase.class);
  private final ImportParticipantsUseCase importer = mock(ImportParticipantsUseCase.class);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final UUID key = UUID.randomUUID();
  private UserPrincipal principal;
  private MockMvc mvc;

  @BeforeEach
  void authenticatedStaffAndStandaloneMvc() {
    principal = ParticipantFixtures.staff(ParticipantFixtures.READ, ParticipantFixtures.IMPORT);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "test", List.of()));
    mvc =
        MockMvcBuilders.standaloneSetup(new BatchParticipantController(list, template, importer))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private String base() {
    return "/api/v1/organizations/" + organizationId + "/health-examination-batches/" + batchId
        + "/participants";
  }

  private static MockMultipartFile file(String name, String type, byte[] bytes) {
    return new MockMultipartFile("file", name, type, bytes);
  }

  @Test
  void importReturns201WithTheSafeResultAndPassesCommandAndPrincipal() throws Exception {
    UUID jobId = UUID.randomUUID();
    when(importer.execute(eq(organizationId), eq(batchId), any(), eq(principal)))
        .thenReturn(new ParticipantImportResponse(jobId, batchId, 3, 3, Instant.parse("2026-10-06T00:00:00Z")));

    mvc.perform(
            multipart(base() + "/imports")
                .file(file("roster.xlsx", XLSX, new byte[] {1, 2, 3}))
                .param("rowVersion", "4")
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.importJobId").value(jobId.toString()))
        .andExpect(jsonPath("$.data.createdCount").value(3));

    var command = ArgumentCaptor.forClass(ImportParticipantsCommand.class);
    verify(importer).execute(eq(organizationId), eq(batchId), command.capture(), eq(principal));
    assertThat(command.getValue().workbook()).containsExactly(1, 2, 3);
    assertThat(command.getValue().rowVersion()).isEqualTo(4L);
    assertThat(command.getValue().idempotencyKey()).isEqualTo(key);
  }

  @Test
  void importAcceptsOctetStreamContentType() throws Exception {
    when(importer.execute(any(), any(), any(), any()))
        .thenReturn(new ParticipantImportResponse(UUID.randomUUID(), batchId, 1, 1, Instant.now()));
    mvc.perform(
            multipart(base() + "/imports")
                .file(file("roster.XLSX", "application/octet-stream", new byte[] {1}))
                .param("rowVersion", "0")
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isCreated());
  }

  @Test
  void importRejectsWrongExtensionOrContentTypeWith415() throws Exception {
    mvc.perform(
            multipart(base() + "/imports")
                .file(file("roster.csv", "text/csv", new byte[] {1}))
                .param("rowVersion", "0")
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isUnsupportedMediaType());
    mvc.perform(
            multipart(base() + "/imports")
                .file(file("roster.xlsx", "text/plain", new byte[] {1}))
                .param("rowVersion", "0")
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isUnsupportedMediaType());
    verifyNoInteractions(importer);
  }

  @Test
  void importRejectsMissingOrEmptyFileMissingKeyAndBadKeyWith400() throws Exception {
    mvc.perform(multipart(base() + "/imports").param("rowVersion", "0").header("Idempotency-Key", key.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart(base() + "/imports")
                .file(file("roster.xlsx", XLSX, new byte[0]))
                .param("rowVersion", "0")
                .header("Idempotency-Key", key.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart(base() + "/imports")
                .file(file("roster.xlsx", XLSX, new byte[] {1}))
                .param("rowVersion", "0"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart(base() + "/imports")
                .file(file("roster.xlsx", XLSX, new byte[] {1}))
                .param("rowVersion", "0")
                .header("Idempotency-Key", "not-a-uuid"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(importer);
  }

  @Test
  void importMapsConflictsAndStaleVersions() throws Exception {
    when(importer.execute(any(), any(), any(), any()))
        .thenThrow(new ConflictException("Participant identity already exists in this batch at row 4"))
        .thenThrow(new ConcurrentUpdateException());

    var request =
        multipart(base() + "/imports")
            .file(file("roster.xlsx", XLSX, new byte[] {1}))
            .param("rowVersion", "0")
            .header("Idempotency-Key", key.toString());
    mvc.perform(request)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Participant identity already exists in this batch at row 4"));
    mvc.perform(request).andExpect(status().isConflict());
  }

  @Test
  void templateDownloadsAnXlsxAttachment() throws Exception {
    when(template.execute(organizationId, batchId, principal))
        .thenReturn(new ParticipantImportTemplateResponse(new byte[] {9, 8, 7}, "participant-import-template.xlsx"));

    mvc.perform(get(base() + "/import-template"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", XLSX))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header().string("Content-Disposition", "attachment; filename=\"participant-import-template.xlsx\""))
        .andExpect(content().bytes(new byte[] {9, 8, 7}));
  }

  @Test
  void listReturnsTheEnvelopeWithTheMaskedPageNoStore() throws Exception {
    var item = ParticipantSummaryResponse.from(
        ParticipantFixtures.summary("012345678901"));
    when(list.execute(eq(organizationId), eq(batchId), any(), eq(principal)))
        .thenReturn(PageResponse.<ParticipantSummaryResponse>builder()
            .items(List.of(item)).page(1).size(20).totalElements(1).totalPages(1).build());

    mvc.perform(get(base()).param("page", "1").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.items[0].identificationNumberMasked").value("********8901"))
        .andExpect(jsonPath("$.data.items[0].identificationNumber").doesNotExist())
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }
}
