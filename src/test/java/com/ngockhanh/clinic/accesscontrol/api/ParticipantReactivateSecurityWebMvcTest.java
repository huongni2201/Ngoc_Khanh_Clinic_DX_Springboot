package com.ngockhanh.clinic.accesscontrol.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.LoginUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.LogoutUseCase;
import com.ngockhanh.clinic.accesscontrol.api.controller.AuthController;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Security chain of {@code POST .../participants/{participantId}/reactivate}: it needs a staff
 * session holding the Participant manage permission and a known Origin. The route is served by a
 * stub controller so that only the filter chain is exercised.
 */
@WebMvcTest(
    controllers = {AuthController.class, ParticipantReactivateSecurityWebMvcTest.StubEndpoint.class},
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  ApiResponseWriter.class,
  ParticipantReactivateSecurityWebMvcTest.StubEndpoint.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class ParticipantReactivateSecurityWebMvcTest {
  private static final String COOKIE = "__Host-NKC_SESSION";
  private static final String ORIGIN = "https://app.test";
  private static final String MANAGE = "HEALTH_EXAMINATION_PARTICIPANT_MANAGE";
  private static final String READ = "HEALTH_EXAMINATION_PARTICIPANT_READ";
  private static final String IMPORT = "HEALTH_EXAMINATION_PARTICIPANT_IMPORT";
  private static final String PATH =
      "/api/v1/organizations/" + UUID.randomUUID() + "/health-examination-batches/"
          + UUID.randomUUID() + "/participants/" + UUID.randomUUID() + "/reactivate";

  @Autowired MockMvc mvc;
  @MockitoBean LoginUseCase login;
  @MockitoBean LogoutUseCase logout;
  @MockitoBean AuthenticateSessionUseCase authenticate;

  @RestController
  static class StubEndpoint {
    @PostMapping(
        "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants/{participantId}/reactivate")
    String reactivate(@PathVariable String participantId) {
      return "reactivated";
    }
  }

  private static UserPrincipal principal(String type, List<String> permissions) {
    return UserPrincipal.builder()
        .userId(UUID.randomUUID())
        .staffId("STAFF".equals(type) ? UUID.randomUUID() : null)
        .patientId("PATIENT".equals(type) ? UUID.randomUUID() : null)
        .username("user")
        .principalType(type)
        .roleAssignments(
            List.of(
                UserPrincipal.Assignment.builder()
                    .roleId(UUID.randomUUID())
                    .roleCode("ROLE")
                    .permissions(permissions)
                    .build()))
        .idleExpiresAt(Instant.now().plusSeconds(1800))
        .absoluteExpiresAt(Instant.now().plusSeconds(28800))
        .build();
  }

  @Test
  void anonymousCallerIsUnauthorized() throws Exception {
    mvc.perform(post(PATH).header("Origin", ORIGIN)).andExpect(status().isUnauthorized());
  }

  @Test
  void staffWithoutManageIsForbiddenEvenWithReadAndImport() throws Exception {
    when(authenticate.execute("reader")).thenReturn(principal("STAFF", List.of(READ, IMPORT)));
    mvc.perform(post(PATH).header("Origin", ORIGIN).cookie(new Cookie(COOKIE, "reader")))
        .andExpect(status().isForbidden());

    when(authenticate.execute("plain")).thenReturn(principal("STAFF", List.of()));
    mvc.perform(post(PATH).header("Origin", ORIGIN).cookie(new Cookie(COOKIE, "plain")))
        .andExpect(status().isForbidden());
  }

  @Test
  void aPatientAccountIsForbiddenEvenWithTheManagePermission() throws Exception {
    when(authenticate.execute("patient")).thenReturn(principal("PATIENT", List.of(MANAGE)));
    mvc.perform(post(PATH).header("Origin", ORIGIN).cookie(new Cookie(COOKIE, "patient")))
        .andExpect(status().isForbidden());
  }

  @Test
  void anUnknownOriginIsForbiddenEvenForAManager() throws Exception {
    when(authenticate.execute("manager")).thenReturn(principal("STAFF", List.of(MANAGE)));
    mvc.perform(post(PATH).header("Origin", "https://evil.test").cookie(new Cookie(COOKIE, "manager")))
        .andExpect(status().isForbidden());
    mvc.perform(post(PATH).cookie(new Cookie(COOKIE, "manager")))
        .andExpect(status().isForbidden());
  }

  @Test
  void aStaffManagerWithAKnownOriginPasses() throws Exception {
    when(authenticate.execute("manager")).thenReturn(principal("STAFF", List.of(MANAGE)));
    mvc.perform(post(PATH).header("Origin", ORIGIN).cookie(new Cookie(COOKIE, "manager")))
        .andExpect(status().isOk());
  }
}
