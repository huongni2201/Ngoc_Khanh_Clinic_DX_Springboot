package com.ngockhanh.clinic.accesscontrol.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.healthexamination.api.controller.ExaminationFormPrintJobController;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationFormPrintJobResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateExaminationFormPrintJobUseCase;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real bulk print controller through the production cookie, Origin and permission
 * filters, with the permission bound exactly as {@code V002} stores it.
 */
@WebMvcTest(
    controllers = ExaminationFormPrintJobController.class,
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  ExaminationFormPrintJobSecurityWebMvcTest.StoredEndpointPermissions.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class ExaminationFormPrintJobSecurityWebMvcTest {
  private static final String BULK_PRINT = "PARTICIPANT_EXAMINATION_FORM_BULK_PRINT";
  private static final String URL =
      "/api/v1/organizations/"
          + UUID.randomUUID()
          + "/health-examination-batches/"
          + UUID.randomUUID()
          + "/participants/examination-form-print-jobs";
  private static final String BODY = "{\"participantIds\":[\"" + UUID.randomUUID() + "\"]}";
  private static final String ORIGIN = "https://app.test";
  private static final String COOKIE = "__Host-NKC_SESSION";

  /** The bulk print endpoint and its permission as stored in {@code public.permissions}. */
  @TestConfiguration
  static class StoredEndpointPermissions {
    @Bean
    UserLoginMyBatisMapper endpointPermissionMapper() {
      var mapper = mock(UserLoginMyBatisMapper.class);
      when(mapper.findEndpointPermissions())
          .thenReturn(
              List.of(
                  PermissionRecord.builder()
                      .code(BULK_PRINT)
                      .httpMethod("POST")
                      .endpoint(
                          "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/examination-form-print-jobs")
                      .build()));
      return mapper;
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean CreateExaminationFormPrintJobUseCase create;

  @BeforeEach
  void stubTheApplicationBoundary() {
    when(create.execute(any(), any(), any(), any()))
        .thenReturn(new ExaminationFormPrintJobResponse(UUID.randomUUID()));
  }

  @Test
  void acceptsOnlyTheBulkPrintPermissionOnAStaffAccount() throws Exception {
    when(authenticate.execute("session")).thenReturn(ParticipantFixtures.staff(BULK_PRINT));
    assertThat(status("session")).isEqualTo(202);

    for (String other :
        List.of(
            "PARTICIPANT_EXAMINATION_FORM_PRINT",
            "PARTICIPANT_VIEW",
            "DOCUMENT_RENDER_JOB_READ",
            "DOCUMENT_RENDER_JOB_DOWNLOAD")) {
      when(authenticate.execute("other")).thenReturn(ParticipantFixtures.staff(other));
      assertThat(status("other")).as(other).isEqualTo(403);
    }
    when(authenticate.execute("none")).thenReturn(ParticipantFixtures.staff());
    assertThat(status("none")).isEqualTo(403);
  }

  @Test
  void rejectsAnonymousCallersPatientsAndForeignOrigins() throws Exception {
    assertThat(status(null)).isEqualTo(401);

    var granted = ParticipantFixtures.staff(BULK_PRINT);
    when(authenticate.execute("patient"))
        .thenReturn(
            UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .patientId(UUID.randomUUID())
                .principalType("PATIENT")
                .roleAssignments(granted.roleAssignments())
                .build());
    assertThat(status("patient")).isEqualTo(403);

    when(authenticate.execute("session")).thenReturn(granted);
    assertThat(
            mvc.perform(
                    post(URL)
                        .header("Origin", "https://evil.test")
                        .cookie(new Cookie(COOKIE, "session"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    verifyNoInteractions(create);
  }

  @Test
  void aMissingKeyOrAnEmptyListIsABadRequest() throws Exception {
    when(authenticate.execute("session")).thenReturn(ParticipantFixtures.staff(BULK_PRINT));

    var noKey =
        post(URL)
            .header("Origin", ORIGIN)
            .cookie(new Cookie(COOKIE, "session"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY);
    var empty =
        post(URL)
            .header("Origin", ORIGIN)
            .cookie(new Cookie(COOKIE, "session"))
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"participantIds\":[]}");

    assertThat(mvc.perform(noKey).andReturn().getResponse().getStatus()).isEqualTo(400);
    assertThat(mvc.perform(empty).andReturn().getResponse().getStatus()).isEqualTo(400);
    verifyNoInteractions(create);
  }

  private int status(String session) throws Exception {
    var request =
        post(URL)
            .header("Origin", ORIGIN)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(BODY);
    if (session != null) request.cookie(new Cookie(COOKIE, session));
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }
}
