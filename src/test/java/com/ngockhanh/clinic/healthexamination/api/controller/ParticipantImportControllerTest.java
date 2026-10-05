package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.healthexamination.api.request.ParticipantImportCancelRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ParticipantImportConfirmRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ParticipantImportMappingRequest;
import com.ngockhanh.clinic.healthexamination.api.request.ParticipantImportUploadRequest;
import com.ngockhanh.clinic.healthexamination.application.command.UploadParticipantImportCommand;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportConfirmResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportRowResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportRowsPageResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportUploadResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CancelParticipantImportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ConfirmParticipantImportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DownloadParticipantImportTemplateUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetParticipantImportUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListParticipantImportRowsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateParticipantImportPreviewUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UploadParticipantImportUseCase;
import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class ParticipantImportControllerTest {
  @Test
  void multipartUploadPreviewConfirmAndCancelWorkOverHttp() throws Exception {
    UUID organizationId = UUID.randomUUID();
    UUID batchId = UUID.randomUUID();
    UUID confirmedImportId = UUID.randomUUID();
    UUID cancelledImportId = UUID.randomUUID();
    UUID dayId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UserPrincipal principal = principal(actorId);
    var upload = mock(UploadParticipantImportUseCase.class);
    var getImport = mock(GetParticipantImportUseCase.class);
    var listRows = mock(ListParticipantImportRowsUseCase.class);
    var preview = mock(UpdateParticipantImportPreviewUseCase.class);
    var confirm = mock(ConfirmParticipantImportUseCase.class);
    var cancel = mock(CancelParticipantImportUseCase.class);
    var controller =
        new ParticipantImportController(
            mock(DownloadParticipantImportTemplateUseCase.class),
            upload,
            getImport,
            listRows,
            preview,
            confirm,
            cancel);
    MockMvc mvc = mvc(controller);
    var authentication = new UsernamePasswordAuthenticationToken(principal, "test", List.of());
    var uploadedConfirmed =
        new ParticipantImportUploadResponse(
            confirmedImportId, "VALIDATED", 0, 1, List.of(dayId), List.of());
    var uploadedCancelled =
        new ParticipantImportUploadResponse(
            cancelledImportId, "VALIDATED", 0, 1, List.of(dayId), List.of());
    var summary =
        new ParticipantImportSummaryResponse(
            confirmedImportId, "VALIDATED", 0, 1, true, List.of(dayId));
    var revisedSummary =
        new ParticipantImportSummaryResponse(
            confirmedImportId, "VALIDATED", 1, 1, true, List.of(dayId));
    var cancelledSummary =
        new ParticipantImportSummaryResponse(
            cancelledImportId, "CANCELLED", 1, 1, false, List.of(dayId));
    var row =
        new ParticipantImportRowResponse(
            3,
            null,
            "Synthetic Person",
            java.time.LocalDate.of(1990, 1, 1),
            "MALE",
            "••••••0001",
            "Department",
            "Position",
            dayId,
            List.of());
    when(upload.execute(org.mockito.ArgumentMatchers.any(UploadParticipantImportCommand.class)))
        .thenReturn(uploadedConfirmed, uploadedCancelled);
    when(getImport.execute(organizationId, batchId, confirmedImportId)).thenReturn(summary);
    when(listRows.execute(organizationId, batchId, confirmedImportId, 1, 50, null))
        .thenReturn(
            new ParticipantImportRowsPageResponse(confirmedImportId, 1, 50, 1, List.of(row)));
    when(preview.execute(
            organizationId,
            batchId,
            confirmedImportId,
            actorId,
            0,
            List.of(dayId),
            Map.of(3, dayId)))
        .thenReturn(revisedSummary);
    when(confirm.execute(organizationId, batchId, confirmedImportId, actorId, 1))
        .thenReturn(new ParticipantImportConfirmResponse(confirmedImportId, "CONFIRMED", 1));
    when(cancel.execute(organizationId, batchId, cancelledImportId, actorId, 0))
        .thenReturn(cancelledSummary);

    String batchPath =
        "/api/v1/organizations/" + organizationId + "/health-examination-batches/" + batchId;
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      mvc.perform(uploadRequest(batchPath, dayId))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.data.importId").value(confirmedImportId.toString()));
      mvc.perform(get(batchPath + "/participant-imports/" + confirmedImportId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("VALIDATED"));
      mvc.perform(
              get(batchPath + "/participant-imports/" + confirmedImportId + "/rows")
                  .param("page", "1")
                  .param("size", "50"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.totalRows").value(1))
          .andExpect(jsonPath("$.data.rows[0].maskedIdentificationNumber").value("••••••0001"));
      mvc.perform(
              put(batchPath + "/participant-imports/" + confirmedImportId + "/preview")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"selectedBatchDayIds":["%s"],"expectedRowVersion":0,"rowAssignments":{"3":"%s"}}
                      """
                          .formatted(dayId, dayId)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.rowVersion").value(1));
      mvc.perform(
              post(batchPath + "/participant-imports/" + confirmedImportId + "/confirm")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"expectedRowVersion\":1}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
      mvc.perform(uploadRequest(batchPath, dayId))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.data.importId").value(cancelledImportId.toString()));
      mvc.perform(
              post(batchPath + "/participant-imports/" + cancelledImportId + "/cancel")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"expectedRowVersion\":0}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    } finally {
      SecurityContextHolder.clearContext();
    }

    var commands = ArgumentCaptor.forClass(UploadParticipantImportCommand.class);
    verify(upload, org.mockito.Mockito.times(2)).execute(commands.capture());
    assertThat(commands.getAllValues())
        .allSatisfy(command -> assertThat(command.actorUserId()).isEqualTo(actorId));
    verify(preview)
        .execute(
            organizationId,
            batchId,
            confirmedImportId,
            actorId,
            0,
            List.of(dayId),
            Map.of(3, dayId));
    verify(confirm).execute(organizationId, batchId, confirmedImportId, actorId, 1);
    verify(cancel).execute(organizationId, batchId, cancelledImportId, actorId, 0);
  }

  @Test
  void httpWorkflowRejectsMissingOrStaleVersionsAndKeepsScopeErrorsCentralized() throws Exception {
    UUID organizationId = UUID.randomUUID();
    UUID otherOrganizationId = UUID.randomUUID();
    UUID batchId = UUID.randomUUID();
    UUID importId = UUID.randomUUID();
    UUID dayId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    var getImport = mock(GetParticipantImportUseCase.class);
    var preview = mock(UpdateParticipantImportPreviewUseCase.class);
    var cancel = mock(CancelParticipantImportUseCase.class);
    var controller =
        new ParticipantImportController(
            mock(DownloadParticipantImportTemplateUseCase.class),
            mock(UploadParticipantImportUseCase.class),
            getImport,
            mock(ListParticipantImportRowsUseCase.class),
            preview,
            mock(ConfirmParticipantImportUseCase.class),
            cancel);
    MockMvc mvc = mvc(controller);
    var principal = principal(actorId);
    var authentication = new UsernamePasswordAuthenticationToken(principal, "test", List.of());
    String validPath =
        "/api/v1/organizations/"
            + organizationId
            + "/health-examination-batches/"
            + batchId
            + "/participant-imports/"
            + importId;
    when(getImport.execute(otherOrganizationId, batchId, importId))
        .thenThrow(new ResourceNotFoundException("Health examination batch"));
    when(preview.execute(organizationId, batchId, importId, actorId, 0, List.of(dayId), Map.of()))
        .thenThrow(new ConcurrentUpdateException());

    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      mvc.perform(get(validPath.replace(organizationId.toString(), otherOrganizationId.toString())))
          .andExpect(status().isNotFound());
      mvc.perform(
              put(validPath + "/preview")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"selectedBatchDayIds\":[\"" + dayId + "\"]}"))
          .andExpect(status().isBadRequest());
      mvc.perform(
              put(validPath + "/preview")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"selectedBatchDayIds\":[\""
                          + dayId
                          + "\"],\"expectedRowVersion\":0,\"rowAssignments\":{}}"))
          .andExpect(status().isConflict());
      mvc.perform(post(validPath + "/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isBadRequest());
    } finally {
      SecurityContextHolder.clearContext();
    }

    verifyNoInteractions(cancel);
    verify(preview)
        .execute(organizationId, batchId, importId, actorId, 0, List.of(dayId), Map.of());
  }

  @Test
  void uploadPreviewConfirmAndCancelUseTheAuthenticatedUserId() {
    UUID organizationId = UUID.randomUUID();
    UUID batchId = UUID.randomUUID();
    UUID importId = UUID.randomUUID();
    UUID dayId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UserPrincipal principal = principal(actorId);
    var upload = mock(UploadParticipantImportUseCase.class);
    var preview = mock(UpdateParticipantImportPreviewUseCase.class);
    var confirm = mock(ConfirmParticipantImportUseCase.class);
    var cancel = mock(CancelParticipantImportUseCase.class);
    var controller =
        new ParticipantImportController(
            mock(DownloadParticipantImportTemplateUseCase.class),
            upload,
            mock(GetParticipantImportUseCase.class),
            mock(ListParticipantImportRowsUseCase.class),
            preview,
            confirm,
            cancel);
    var validated =
        new ParticipantImportUploadResponse(importId, "VALIDATED", 0, 1, List.of(dayId), List.of());
    when(upload.execute(org.mockito.ArgumentMatchers.any(UploadParticipantImportCommand.class)))
        .thenReturn(validated);
    var summary =
        new ParticipantImportSummaryResponse(importId, "VALIDATED", 1, 1, true, List.of(dayId));
    when(preview.execute(
            organizationId, batchId, importId, actorId, 0, List.of(dayId), Map.of(1, dayId)))
        .thenReturn(summary);
    when(confirm.execute(organizationId, batchId, importId, actorId, 1))
        .thenReturn(new ParticipantImportConfirmResponse(importId, "CONFIRMED", 1));
    when(cancel.execute(organizationId, batchId, importId, actorId, 1)).thenReturn(summary);
    var file =
        new MockMultipartFile("file", "roster.xlsx", "application/vnd.ms-excel", new byte[] {1});

    var uploaded =
        controller.upload(
            organizationId,
            batchId,
            file,
            new ParticipantImportUploadRequest(List.of(dayId)),
            principal);
    controller.preview(
        organizationId,
        batchId,
        importId,
        new ParticipantImportMappingRequest(List.of(dayId), 0L, Map.of(1, dayId)),
        principal);
    controller.confirm(
        organizationId, batchId, importId, new ParticipantImportConfirmRequest(1L), principal);
    controller.cancel(
        organizationId, batchId, importId, new ParticipantImportCancelRequest(1L), principal);

    assertThat(uploaded.getStatusCode().value()).isEqualTo(201);
    var command = ArgumentCaptor.forClass(UploadParticipantImportCommand.class);
    verify(upload).execute(command.capture());
    assertThat(command.getValue().actorUserId()).isEqualTo(actorId);
    verify(preview)
        .execute(organizationId, batchId, importId, actorId, 0, List.of(dayId), Map.of(1, dayId));
    verify(confirm).execute(organizationId, batchId, importId, actorId, 1);
    verify(cancel).execute(organizationId, batchId, importId, actorId, 1);
  }

  private static UserPrincipal principal(UUID userId) {
    return new UserPrincipal(
        userId,
        UUID.randomUUID(),
        null,
        "staff",
        "STAFF",
        List.of(
            new UserPrincipal.Assignment(
                UUID.randomUUID(), "CLINIC_MANAGER", List.of(), UUID.randomUUID(), Instant.now())),
        Instant.now(),
        Instant.now().plusSeconds(3600));
  }

  private static MockMvc mvc(ParticipantImportController controller) {
    return MockMvcBuilders.standaloneSetup(controller)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(
            new GlobalExceptionHandler(new ApiResponseWriter(JsonMapper.builder().build())))
        .build();
  }

  private static MockMultipartHttpServletRequestBuilder uploadRequest(
      String batchPath, UUID dayId) {
    return multipart(batchPath + "/participant-imports")
        .file(
            new MockMultipartFile(
                "file", "roster.xlsx", "application/vnd.ms-excel", new byte[] {1}))
        .file(
            new MockMultipartFile(
                "configuration",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                ("{\"selectedBatchDayIds\":[\"" + dayId + "\"]}")
                    .getBytes(StandardCharsets.UTF_8)));
  }
}
