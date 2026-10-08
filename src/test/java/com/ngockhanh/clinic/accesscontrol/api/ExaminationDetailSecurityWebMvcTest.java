package com.ngockhanh.clinic.accesscontrol.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.accesscontrol.api.controller.AuthController;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.LoginUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.LogoutUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Security chain of the six examination detail and report routes, with their permissions as
 * stored in {@code public.permissions}: each needs a staff session holding exactly its own
 * permission, and the permissions of the Participant roster, of another
 * detail route or of a patient account never open a route. The routes are served by a stub
 * controller so that only the filter chain is exercised.
 */
@WebMvcTest(
    controllers = {AuthController.class, ExaminationDetailSecurityWebMvcTest.StubEndpoints.class},
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  ExaminationDetailSecurityWebMvcTest.StubEndpoints.class,
  ExaminationDetailSecurityWebMvcTest.StoredEndpointPermissions.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class ExaminationDetailSecurityWebMvcTest {
  private static final String COOKIE = "__Host-NKC_SESSION";
  private static final String ORIGIN = "https://app.test";
  private static final String SERVICE_READ = "HEALTH_EXAMINATION_SERVICE_READ";
  private static final String SERVICE_SUMMARY_READ = "HEALTH_EXAMINATION_SERVICE_SUMMARY_READ";
  private static final String SERVICE_EXPORT = "HEALTH_EXAMINATION_SERVICE_EXPORT";
  private static final String SERVICE_RECONCILE = "HEALTH_EXAMINATION_SERVICE_RECONCILE";
  private static final String REPORT_READ = "HEALTH_EXAMINATION_REPORT_READ";
  private static final String REPORT_EXPORT = "HEALTH_EXAMINATION_REPORT_EXPORT";
  private static final String PARTICIPANT_MANAGE = "HEALTH_EXAMINATION_PARTICIPANT_MANAGE";
  private static final String BATCH =
      "/api/v1/organizations/"
          + UUID.randomUUID()
          + "/health-examination-batches/"
          + UUID.randomUUID();

  @Autowired MockMvc mvc;
  @MockitoBean LoginUseCase login;
  @MockitoBean LogoutUseCase logout;
  @MockitoBean AuthenticateSessionUseCase authenticate;

  @RestController
  static class StubEndpoints {
    private static final String BASE =
        "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}";

    @GetMapping({
      BASE + "/examination-details",
      BASE + "/examination-details/summary",
      BASE + "/examination-details/export",
      BASE + "/reports/payment-summary",
      BASE + "/reports/payment-summary/docx"
    })
    String read() {
      return "ok";
    }

    @PostMapping(BASE + "/examination-details/imports")
    String importDetails() {
      return "ok";
    }
  }

  private record Route(String name, boolean isPost, String path, String permission) {
    MockHttpServletRequestBuilder request() {
      return isPost ? post(BATCH + path) : get(BATCH + path);
    }
  }

  private static final List<Route> ROUTES =
      List.of(
          new Route("list", false, "/examination-details", SERVICE_READ),
          new Route("summary", false, "/examination-details/summary", SERVICE_SUMMARY_READ),
          new Route("export", false, "/examination-details/export", SERVICE_EXPORT),
          new Route("import", true, "/examination-details/imports", SERVICE_RECONCILE),
          new Route("report", false, "/reports/payment-summary", REPORT_READ),
          new Route("docx", false, "/reports/payment-summary/docx", REPORT_EXPORT));

  @TestConfiguration
  static class StoredEndpointPermissions {
    @Bean
    UserLoginMyBatisMapper endpointPermissions() {
      var mapper = mock(UserLoginMyBatisMapper.class);
      when(mapper.findEndpointPermissions())
          .thenReturn(
              ROUTES.stream()
                  .map(
                      route ->
                          PermissionRecord.builder()
                              .code(route.permission())
                              .httpMethod(route.isPost() ? "POST" : "GET")
                              .endpoint(StubEndpoints.BASE + route.path())
                              .build())
                  .toList());
      return mapper;
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

  private int statusOf(Route route, String session) throws Exception {
    var request = route.request().header("Origin", ORIGIN);
    if (session != null) request = request.cookie(new Cookie(COOKIE, session));
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }

  @Test
  void anonymousCallerIsUnauthorizedOnEveryRoute() throws Exception {
    for (Route route : ROUTES)
      org.assertj.core.api.Assertions.assertThat(statusOf(route, null))
          .as(route.name())
          .isEqualTo(401);
  }

  @Test
  void eachRouteOpensOnlyForItsOwnPermission() throws Exception {
    for (Route allowed : ROUTES) {
      when(authenticate.execute("holder"))
          .thenReturn(principal("STAFF", List.of(allowed.permission())));
      for (Route route : ROUTES) {
        int expected = route.permission().equals(allowed.permission()) ? 200 : 403;
        org.assertj.core.api.Assertions.assertThat(statusOf(route, "holder"))
            .as(route.name() + " with " + allowed.permission())
            .isEqualTo(expected);
      }
    }
  }

  @Test
  void staffWithOnlyParticipantPermissionsOrNoPermissionIsForbidden() throws Exception {
    when(authenticate.execute("manager"))
        .thenReturn(principal("STAFF", List.of(PARTICIPANT_MANAGE)));
    when(authenticate.execute("plain")).thenReturn(principal("STAFF", List.of()));
    for (Route route : ROUTES) {
      org.assertj.core.api.Assertions.assertThat(statusOf(route, "manager"))
          .as(route.name())
          .isEqualTo(403);
      org.assertj.core.api.Assertions.assertThat(statusOf(route, "plain"))
          .as(route.name())
          .isEqualTo(403);
    }
  }

  @Test
  void aPatientAccountIsForbiddenEvenWithEveryDetailPermission() throws Exception {
    when(authenticate.execute("patient"))
        .thenReturn(principal("PATIENT", ROUTES.stream().map(Route::permission).toList()));
    for (Route route : ROUTES)
      org.assertj.core.api.Assertions.assertThat(statusOf(route, "patient"))
          .as(route.name())
          .isEqualTo(403);
  }

  @Test
  void importWithAnUnknownOriginIsForbiddenEvenWithThePermission() throws Exception {
    when(authenticate.execute("reconciler"))
        .thenReturn(principal("STAFF", List.of(SERVICE_RECONCILE)));
    mvc.perform(
            post(BATCH + "/examination-details/imports")
                .header("Origin", "https://evil.test")
                .cookie(new Cookie(COOKIE, "reconciler")))
        .andExpect(status().isForbidden());
  }
}
