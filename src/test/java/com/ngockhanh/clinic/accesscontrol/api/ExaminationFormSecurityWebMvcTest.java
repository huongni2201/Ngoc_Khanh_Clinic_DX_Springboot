package com.ngockhanh.clinic.accesscontrol.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.healthexamination.api.controller.ExaminationFormController;
import com.ngockhanh.clinic.healthexamination.application.response.ExaminationFormDocumentResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.RenderParticipantExaminationFormUseCase;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real examination form controller through the production cookie, Origin and
 * permission filters, with the permission bound exactly as {@code V005} stores it.
 */
@WebMvcTest(
    controllers = ExaminationFormController.class,
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  ExaminationFormSecurityWebMvcTest.StoredEndpointPermissions.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class ExaminationFormSecurityWebMvcTest {
  private static final String PRINT = "PARTICIPANT_EXAMINATION_FORM_PRINT";
  private static final String URL =
      "/api/v1/organizations/"
          + UUID.randomUUID()
          + "/health-examination-batches/"
          + UUID.randomUUID()
          + "/participants/"
          + UUID.randomUUID()
          + "/examination-form";
  private static final String ORIGIN = "https://app.test";
  private static final String COOKIE = "__Host-NKC_SESSION";

  /** The print endpoint and its permission as stored in {@code public.permissions}. */
  @TestConfiguration
  static class StoredEndpointPermissions {
    @Bean
    UserLoginMyBatisMapper endpointPermissionMapper() {
      var mapper = mock(UserLoginMyBatisMapper.class);
      when(mapper.findEndpointPermissions())
          .thenReturn(
              List.of(
                  PermissionRecord.builder()
                      .code(PRINT)
                      .httpMethod("GET")
                      .endpoint(
                          "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/{participantId}/examination-form")
                      .build()));
      return mapper;
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean RenderParticipantExaminationFormUseCase render;

  @BeforeEach
  void stubTheApplicationBoundary() {
    when(render.execute(any(), any(), any(), any()))
        .thenReturn(new ExaminationFormDocumentResponse(new byte[] {1}, "phieu-kham-X.pdf"));
  }

  @Test
  void acceptsOnlyThePrintPermissionOnAStaffAccount() throws Exception {
    when(authenticate.execute("session")).thenReturn(ParticipantFixtures.staff(PRINT));
    assertThat(status("session")).isEqualTo(200);

    for (String other :
        List.of(
            "PARTICIPANT_VIEW",
            "PARTICIPANT_DETAIL_VIEW",
            "PARTICIPANT_IMPORT",
            "HEALTH_EXAMINATION_PARTICIPANT_MANAGE")) {
      when(authenticate.execute("other")).thenReturn(ParticipantFixtures.staff(other));
      assertThat(status("other")).as(other).isEqualTo(403);
    }
    when(authenticate.execute("none")).thenReturn(ParticipantFixtures.staff());
    assertThat(status("none")).isEqualTo(403);
  }

  @Test
  void rejectsAnonymousCallersAndPatientsEvenWithThePermission() throws Exception {
    assertThat(status(null)).isEqualTo(401);

    var granted = ParticipantFixtures.staff(PRINT);
    when(authenticate.execute("patient"))
        .thenReturn(
            UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .patientId(UUID.randomUUID())
                .principalType("PATIENT")
                .roleAssignments(granted.roleAssignments())
                .build());
    assertThat(status("patient")).isEqualTo(403);
    verifyNoInteractions(render);
  }

  private int status(String session) throws Exception {
    var request = get(URL).header("Origin", ORIGIN);
    if (session != null) request.cookie(new Cookie(COOKIE, session));
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }
}
