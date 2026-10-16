package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationFormDocumentResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.RenderParticipantExaminationFormUseCase;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.exception.ConflictException;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExaminationFormControllerTest {
  private final RenderParticipantExaminationFormUseCase useCase =
      mock(RenderParticipantExaminationFormUseCase.class);
  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
  private final UUID participantId = UUID.randomUUID();
  private UserPrincipal principal;
  private MockMvc mvc;

  @BeforeEach
  void authenticatedStaffAndStandaloneMvc() {
    principal = ParticipantFixtures.staff();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "test", List.of()));
    mvc =
        MockMvcBuilders.standaloneSetup(new ExaminationFormController(useCase))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private String url() {
    return "/api/v1/organizations/"
        + organizationId
        + "/agreements/"
        + agreementId
        + "/participants/"
        + participantId
        + "/examination-form";
  }

  @Test
  void returnsThePdfInlineAndNeverCachesIt() throws Exception {
    when(useCase.execute(
            eq(organizationId), eq(agreementId), eq(participantId), isNull(), eq(principal)))
        .thenReturn(
            new ExaminationFormDocumentResponse(
                new byte[] {'%', 'P', 'D', 'F'}, "phieu-kham-NV-001.pdf"));

    mvc.perform(get(url()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header().string("Content-Disposition", "inline; filename=\"phieu-kham-NV-001.pdf\""))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(content().bytes(new byte[] {'%', 'P', 'D', 'F'}));
  }

  @Test
  void mapsFailuresToTheirStatuses() throws Exception {
    when(useCase.execute(
            eq(organizationId), eq(agreementId), eq(participantId), isNull(), eq(principal)))
        .thenThrow(new ResourceNotFoundException("Participant"))
        .thenThrow(
            new ConflictException("The agreement status does not allow printing examination forms"))
        .thenThrow(new DependencyUnavailableException("converter down", null))
        .thenThrow(
            new ApplicationException(ApplicationException.Type.ACCESS_DENIED, "not authorized"));

    mvc.perform(get(url())).andExpect(status().isNotFound());
    mvc.perform(get(url())).andExpect(status().isConflict());
    mvc.perform(get(url())).andExpect(status().isServiceUnavailable());
    mvc.perform(get(url())).andExpect(status().isForbidden());
  }

  @Test
  void passesTheOptionalSessionToTheUseCase() throws Exception {
    UUID sessionId = UUID.randomUUID();
    when(useCase.execute(
            eq(organizationId), eq(agreementId), eq(participantId), eq(sessionId), eq(principal)))
        .thenReturn(new ExaminationFormDocumentResponse(new byte[] {1}, "phieu-kham-NV-001.pdf"));

    mvc.perform(get(url()).param("sessionId", sessionId.toString())).andExpect(status().isOk());
    mvc.perform(get(url()).param("sessionId", "not-a-uuid")).andExpect(status().isBadRequest());
  }
}
