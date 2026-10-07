package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.healthexamination.application.command.CancelParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.command.CreateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.command.ImportParticipantsCommand;
import com.ngockhanh.clinic.healthexamination.application.command.ReactivateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateParticipantCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportTemplateResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CancelParticipantUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateParticipantUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetParticipantDetailUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetParticipantImportTemplateUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ImportParticipantsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListParticipantsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ReactivateParticipantUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateParticipantUseCase;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
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
  private final CreateParticipantUseCase create = mock(CreateParticipantUseCase.class);
  private final GetParticipantDetailUseCase detail = mock(GetParticipantDetailUseCase.class);
  private final UpdateParticipantUseCase update = mock(UpdateParticipantUseCase.class);
  private final CancelParticipantUseCase cancel = mock(CancelParticipantUseCase.class);
  private final ReactivateParticipantUseCase reactivate = mock(ReactivateParticipantUseCase.class);
  private final UUID participantId = UUID.randomUUID();
  private final UUID dayId = UUID.randomUUID();
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final UUID key = UUID.randomUUID();
  private UserPrincipal principal;
  private MockMvc mvc;

  @BeforeEach
  void authenticatedStaffAndStandaloneMvc() {
    principal =
        ParticipantFixtures.staff(
            ParticipantFixtures.READ,
            ParticipantFixtures.IMPORT,
            ParticipantFixtures.CREATE,
            ParticipantFixtures.UPDATE,
            ParticipantFixtures.REMOVE,
            ParticipantFixtures.REACTIVATE,
            ParticipantFixtures.TEMPLATE_DOWNLOAD);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "test", List.of()));
    mvc =
        MockMvcBuilders.standaloneSetup(
                new BatchParticipantController(
                    list, template, importer, create, detail, update, cancel, reactivate))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private String base() {
    return "/api/v1/organizations/"
        + organizationId
        + "/health-examination-batches/"
        + batchId
        + "/participants";
  }

  private static MockMultipartFile file(String name, String type, byte[] bytes) {
    return new MockMultipartFile("file", name, type, bytes);
  }

  @Test
  void importReturns201WithTheSafeResultAndPassesCommandAndPrincipal() throws Exception {
    UUID jobId = UUID.randomUUID();
    when(importer.execute(eq(organizationId), eq(batchId), any(), eq(principal)))
        .thenReturn(
            new ParticipantImportResponse(
                jobId, batchId, 3, 3, Instant.parse("2026-10-06T00:00:00Z")));

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
    mvc.perform(
            multipart(base() + "/imports")
                .param("rowVersion", "0")
                .header("Idempotency-Key", key.toString()))
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
        .thenThrow(
            new ConflictException("Participant identity already exists in this batch at row 4"))
        .thenThrow(new ConcurrentUpdateException());

    var request =
        multipart(base() + "/imports")
            .file(file("roster.xlsx", XLSX, new byte[] {1}))
            .param("rowVersion", "0")
            .header("Idempotency-Key", key.toString());
    mvc.perform(request)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value("Participant identity already exists in this batch at row 4"));
    mvc.perform(request).andExpect(status().isConflict());
  }

  @Test
  void templateDownloadsAnXlsxAttachment() throws Exception {
    when(template.execute(organizationId, batchId, principal))
        .thenReturn(
            new ParticipantImportTemplateResponse(
                new byte[] {9, 8, 7}, "participant-import-template.xlsx"));

    mvc.perform(get(base() + "/import-template"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", XLSX))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"participant-import-template.xlsx\""))
        .andExpect(content().bytes(new byte[] {9, 8, 7}));
  }

  @Test
  void listReturnsTheEnvelopeWithTheMaskedPageNoStore() throws Exception {
    var item = ParticipantSummaryResponse.from(ParticipantFixtures.summary("012345678901"));
    when(list.execute(eq(organizationId), eq(batchId), any(), eq(principal)))
        .thenReturn(
            PageResponse.<ParticipantSummaryResponse>builder()
                .items(List.of(item))
                .page(1)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build());

    mvc.perform(get(base()).param("page", "1").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.items[0].identificationNumberMasked").value("********8901"))
        .andExpect(jsonPath("$.data.items[0].identificationNumber").doesNotExist())
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  private static final String IDENTIFICATION = "012345678901";

  private String body(String identification, String extra) {
    return """
        {"participantCode":"NV-001","fullName":"Synthetic Person","dateOfBirth":"1990-05-12",
         "sex":"MALE","identificationNumber":"%s","phone":"0901234567","email":null,
         "departmentName":"Accounting","positionName":"Staff","batchDayId":"%s"%s}
        """
        .formatted(identification, dayId, extra);
  }

  private ParticipantDetailResponse detailResponse(long rowVersion) {
    return ParticipantDetailResponse.builder()
        .id(participantId)
        .batchId(batchId)
        .batchDayId(dayId)
        .examinationDate(LocalDate.of(2026, 10, 4))
        .fullName("Synthetic Person")
        .dateOfBirth(LocalDate.of(1990, 5, 12))
        .sex("MALE")
        .identificationNumberMasked("********8901")
        .identificationNumber(IDENTIFICATION)
        .phone("0901234567")
        .departmentName("Accounting")
        .positionName("Staff")
        .rosterStatus("ACTIVE")
        .attendanceStatus("UNCONFIRMED")
        .reconciliationStatus("PENDING")
        .rowVersion(rowVersion)
        .patientLinked(false)
        .source("MANUAL")
        .build();
  }

  @Test
  void createReturns201WithTheFullDetailNoStoreAndPassesCommandAndPrincipal() throws Exception {
    when(create.execute(eq(organizationId), eq(batchId), any(), eq(principal)))
        .thenReturn(detailResponse(0));

    mvc.perform(
            post(base()).contentType(MediaType.APPLICATION_JSON).content(body(IDENTIFICATION, "")))
        .andExpect(status().isCreated())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.id").value(participantId.toString()))
        .andExpect(jsonPath("$.data.identificationNumber").value(IDENTIFICATION))
        .andExpect(jsonPath("$.data.source").value("MANUAL"));

    var command = ArgumentCaptor.forClass(CreateParticipantCommand.class);
    verify(create).execute(eq(organizationId), eq(batchId), command.capture(), eq(principal));
    assertThat(command.getValue().identificationNumber()).isEqualTo(IDENTIFICATION);
    assertThat(command.getValue().batchDayId()).isEqualTo(dayId);
  }

  @Test
  void createRejectsInvalidBodiesWith400BeforeTheUseCase() throws Exception {
    for (String invalid :
        new String[] {
          body("12A45", ""),
          body("", ""),
          body("123456789012345678901", ""),
          body(IDENTIFICATION, "").replace("\"MALE\"", "\"ROBOT\""),
          body(IDENTIFICATION, "").replace("Synthetic Person", " "),
          body(IDENTIFICATION, "").replace("\"batchDayId\":\"" + dayId + "\"", "\"x\":1"),
          "{}",
          "not-json"
        })
      mvc.perform(post(base()).contentType(MediaType.APPLICATION_JSON).content(invalid))
          .andExpect(status().isBadRequest());
    verifyNoInteractions(create);
  }

  @Test
  void createMapsDuplicateIdentityAndClosedBatchToConflict() throws Exception {
    when(create.execute(any(), any(), any(), any()))
        .thenThrow(new ConflictException("Participant identity already exists in this batch"))
        .thenThrow(new ConflictException("Batch does not accept Participant changes"));
    mvc.perform(
            post(base()).contentType(MediaType.APPLICATION_JSON).content(body(IDENTIFICATION, "")))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message").value("Participant identity already exists in this batch"));
    mvc.perform(
            post(base()).contentType(MediaType.APPLICATION_JSON).content(body(IDENTIFICATION, "")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Batch does not accept Participant changes"));
  }

  @Test
  void detailReturnsTheCompleteIdentificationNumberNoStore() throws Exception {
    when(detail.execute(organizationId, batchId, participantId, principal))
        .thenReturn(detailResponse(3));

    mvc.perform(get(base() + "/" + participantId))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.identificationNumber").value(IDENTIFICATION))
        .andExpect(jsonPath("$.data.phone").value("0901234567"))
        .andExpect(jsonPath("$.data.patientLinked").value(false))
        .andExpect(jsonPath("$.data.rowVersion").value(3))
        .andExpect(jsonPath("$.data.patientId").doesNotExist());
  }

  @Test
  void detailOfAnUnknownParticipantIs404() throws Exception {
    when(detail.execute(any(), any(), any(), any()))
        .thenThrow(
            new com.ngockhanh.clinic.shared.exception.ResourceNotFoundException("Participant"));
    mvc.perform(get(base() + "/" + participantId)).andExpect(status().isNotFound());
  }

  @Test
  void updateReturns200WithTheNewVersionAndPassesTheExpectedVersion() throws Exception {
    when(update.execute(eq(organizationId), eq(batchId), eq(participantId), any(), eq(principal)))
        .thenReturn(detailResponse(4));

    mvc.perform(
            put(base() + "/" + participantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(IDENTIFICATION, ",\"rowVersion\":3")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.data.rowVersion").value(4));

    var command = ArgumentCaptor.forClass(UpdateParticipantCommand.class);
    verify(update)
        .execute(
            eq(organizationId), eq(batchId), eq(participantId), command.capture(), eq(principal));
    assertThat(command.getValue().expectedRowVersion()).isEqualTo(3L);
  }

  @Test
  void updateRequiresANonNegativeRowVersionAndMapsStaleVersionTo409() throws Exception {
    for (String extra : new String[] {"", ",\"rowVersion\":-1"})
      mvc.perform(
              put(base() + "/" + participantId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(IDENTIFICATION, extra)))
          .andExpect(status().isBadRequest());
    verifyNoInteractions(update);

    when(update.execute(any(), any(), any(), any(), any()))
        .thenThrow(new ConcurrentUpdateException());
    mvc.perform(
            put(base() + "/" + participantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(IDENTIFICATION, ",\"rowVersion\":0")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Record was changed by another request"));
  }

  @Test
  void cancelReturns204WithAnEmptyBodyAndPassesTheExpectedVersion() throws Exception {
    mvc.perform(delete(base() + "/" + participantId).param("rowVersion", "5"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    var command = ArgumentCaptor.forClass(CancelParticipantCommand.class);
    verify(cancel)
        .execute(
            eq(organizationId), eq(batchId), eq(participantId), command.capture(), eq(principal));
    assertThat(command.getValue().expectedRowVersion()).isEqualTo(5L);
  }

  @Test
  void cancelRequiresANonNegativeRowVersion() throws Exception {
    mvc.perform(delete(base() + "/" + participantId)).andExpect(status().isBadRequest());
    mvc.perform(delete(base() + "/" + participantId).param("rowVersion", "-1"))
        .andExpect(status().isBadRequest());
    mvc.perform(delete(base() + "/" + participantId).param("rowVersion", "abc"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(cancel);
  }

  @Test
  void cancelMapsRuleViolationsToConflictWithTheSafeMessage() throws Exception {
    org.mockito.Mockito.doThrow(
            new ConflictException(
                "Participant cannot be cancelled after preparation or attendance"))
        .when(cancel)
        .execute(any(), any(), any(), any(), any());
    mvc.perform(delete(base() + "/" + participantId).param("rowVersion", "0"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value("Participant cannot be cancelled after preparation or attendance"));
  }

  @Test
  void reactivateReturns200WithTheFullDetailNoStoreAndPassesCommandAndPrincipal() throws Exception {
    when(reactivate.execute(
            eq(organizationId), eq(batchId), eq(participantId), any(), eq(principal)))
        .thenReturn(detailResponse(5));

    mvc.perform(
            post(base() + "/" + participantId + "/reactivate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":4,\"batchDayId\":\"" + dayId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.id").value(participantId.toString()))
        .andExpect(jsonPath("$.data.rosterStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.data.rowVersion").value(5));

    var command = ArgumentCaptor.forClass(ReactivateParticipantCommand.class);
    verify(reactivate)
        .execute(
            eq(organizationId), eq(batchId), eq(participantId), command.capture(), eq(principal));
    assertThat(command.getValue().expectedRowVersion()).isEqualTo(4L);
    assertThat(command.getValue().batchDayId()).isEqualTo(dayId);
  }

  @Test
  void reactivateAcceptsABodyWithoutADayAndKeepsTheDayNull() throws Exception {
    when(reactivate.execute(any(), any(), any(), any(), any())).thenReturn(detailResponse(1));

    mvc.perform(
            post(base() + "/" + participantId + "/reactivate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":0}"))
        .andExpect(status().isOk());

    var command = ArgumentCaptor.forClass(ReactivateParticipantCommand.class);
    verify(reactivate).execute(any(), any(), any(), command.capture(), any());
    assertThat(command.getValue().batchDayId()).isNull();
  }

  @Test
  void reactivateRejectsInvalidBodiesWith400BeforeTheUseCase() throws Exception {
    for (String invalid :
        new String[] {
          "{}",
          "{\"batchDayId\":\"" + dayId + "\"}",
          "{\"rowVersion\":-1}",
          "{\"rowVersion\":\"abc\"}",
          "{\"rowVersion\":1,\"batchDayId\":\"not-a-uuid\"}",
          "not-json"
        })
      mvc.perform(
              post(base() + "/" + participantId + "/reactivate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalid))
          .andExpect(status().isBadRequest());
    verifyNoInteractions(reactivate);
  }

  @Test
  void reactivateMapsNotFoundAndConflictsToTheirSafeMessages() throws Exception {
    when(reactivate.execute(any(), any(), any(), any(), any()))
        .thenThrow(
            new com.ngockhanh.clinic.shared.exception.ResourceNotFoundException("Participant"))
        .thenThrow(new ConflictException("Participant is not cancelled"))
        .thenThrow(new ConflictException("Examination day is not a day of this batch"))
        .thenThrow(new ConflictException("Batch does not accept Participant changes"))
        .thenThrow(
            new ConflictException(
                "Participant cannot be reactivated after preparation or attendance"))
        .thenThrow(new ConcurrentUpdateException());
    String[] messages = {
      "Requested resource not found",
      "Participant is not cancelled",
      "Examination day is not a day of this batch",
      "Batch does not accept Participant changes",
      "Participant cannot be reactivated after preparation or attendance",
      "Record was changed by another request"
    };
    int[] statuses = {404, 409, 409, 409, 409, 409};
    for (int i = 0; i < messages.length; i++)
      mvc.perform(
              post(base() + "/" + participantId + "/reactivate")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rowVersion\":0}"))
          .andExpect(status().is(statuses[i]))
          .andExpect(jsonPath("$.message").value(messages[i]));
  }
}
