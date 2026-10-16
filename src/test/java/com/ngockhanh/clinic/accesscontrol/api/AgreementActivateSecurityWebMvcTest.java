package com.ngockhanh.clinic.accesscontrol.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.healthexamination.api.controller.OrganizationAgreementController;
import com.ngockhanh.clinic.healthexamination.application.command.ActivateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.usecase.ActivateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetHealthExaminationAgreementByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
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
 * Exercises the real agreement controller's activate endpoint through the production cookie, Origin
 * and permission filters, with the permission bound exactly as {@code V002} stores it.
 */
@WebMvcTest(
    controllers = OrganizationAgreementController.class,
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  AgreementActivateSecurityWebMvcTest.StoredEndpointPermissions.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class AgreementActivateSecurityWebMvcTest {
  private static final String START = "HEALTH_EXAMINATION_AGREEMENT_ACTIVATE";
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID AGREEMENT = UUID.randomUUID();
  private static final String URL =
      "/api/v1/organizations/" + ORGANIZATION + "/agreements/" + AGREEMENT + "/activate";
  private static final String ORIGIN = "https://app.test";
  private static final String COOKIE = "__Host-NKC_SESSION";

  /** The start endpoint and its permission as stored in {@code public.permissions}. */
  @TestConfiguration
  static class StoredEndpointPermissions {
    @Bean
    UserLoginMyBatisMapper endpointPermissionMapper() {
      var mapper = mock(UserLoginMyBatisMapper.class);
      when(mapper.findEndpointPermissions())
          .thenReturn(
              List.of(
                  PermissionRecord.builder()
                      .code(START)
                      .httpMethod("POST")
                      .endpoint(
                          "/api/v1/organizations/{organizationId}/agreements/{agreementId}/activate")
                      .build()));
      return mapper;
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean CreateHealthExaminationAgreementUseCase create;
  @MockitoBean GetHealthExaminationAgreementByIdUseCase get;
  @MockitoBean UpdateHealthExaminationAgreementUseCase update;
  @MockitoBean ListHealthExaminationAgreementUseCase list;
  @MockitoBean DeleteHealthExaminationAgreementUseCase delete;
  @MockitoBean ActivateHealthExaminationAgreementUseCase start;

  @MockitoBean
  com.ngockhanh.clinic.healthexamination.application.usecase.EndHealthExaminationAgreementUseCase
      end;

  @Test
  void acceptsOnlyTheStartPermissionOnAStaffAccountAndPassesTheVersion() throws Exception {
    var granted = ParticipantFixtures.staff(START);
    when(authenticate.execute("session")).thenReturn(granted);
    assertThat(status("session", "{\"rowVersion\":4}")).isEqualTo(200);
    verify(start)
        .execute(
            eq(ORGANIZATION),
            eq(AGREEMENT),
            eq(ActivateHealthExaminationAgreementCommand.builder().rowVersion(4L).build()),
            eq(granted.userId()));

    for (String other :
        List.of(
            "HEALTH_EXAMINATION_AGREEMENT_UPDATE",
            "HEALTH_EXAMINATION_AGREEMENT_END",
            "PARTICIPANT_EXAMINATION_FORM_PRINT")) {
      when(authenticate.execute("other")).thenReturn(ParticipantFixtures.staff(other));
      assertThat(status("other", "{\"rowVersion\":4}")).as(other).isEqualTo(403);
    }
  }

  @Test
  void rejectsAnonymousCallersPatientsAndForeignOrigins() throws Exception {
    assertThat(status(null, "{\"rowVersion\":0}")).isEqualTo(401);

    var granted = ParticipantFixtures.staff(START);
    when(authenticate.execute("patient"))
        .thenReturn(
            UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .patientId(UUID.randomUUID())
                .principalType("PATIENT")
                .roleAssignments(granted.roleAssignments())
                .build());
    assertThat(status("patient", "{\"rowVersion\":0}")).isEqualTo(403);

    when(authenticate.execute("session")).thenReturn(granted);
    assertThat(
            mvc.perform(
                    post(URL)
                        .header("Origin", "https://evil.test")
                        .cookie(new Cookie(COOKIE, "session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":0}"))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    verifyNoInteractions(start);
  }

  @Test
  void aMissingOrNegativeVersionIsABadRequest() throws Exception {
    when(authenticate.execute("session")).thenReturn(ParticipantFixtures.staff(START));

    assertThat(status("session", "{}")).isEqualTo(400);
    assertThat(status("session", "{\"rowVersion\":-1}")).isEqualTo(400);
    verify(start, org.mockito.Mockito.never()).execute(any(), any(), any(), any());
  }

  private int status(String session, String body) throws Exception {
    var request =
        post(URL).header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON).content(body);
    if (session != null) request.cookie(new Cookie(COOKIE, session));
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }
}
