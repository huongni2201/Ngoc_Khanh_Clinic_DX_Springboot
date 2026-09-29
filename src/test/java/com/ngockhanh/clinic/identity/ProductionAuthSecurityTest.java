package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.api.configuration.AuthApiConfiguration;
import com.ngockhanh.clinic.identity.api.controller.StaffAuthController;
import com.ngockhanh.clinic.identity.application.command.StaffLoginCommand;
import com.ngockhanh.clinic.identity.application.command.LogoutAllStaffSessionsCommand;
import com.ngockhanh.clinic.identity.application.command.LogoutStaffSessionCommand;
import com.ngockhanh.clinic.identity.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.identity.application.query.AuthenticateStaffSessionQuery;
import com.ngockhanh.clinic.identity.application.query.GetCsrfTokenQuery;
import com.ngockhanh.clinic.identity.application.query.GetStaffSessionQuery;
import com.ngockhanh.clinic.identity.application.query.access.StaffPrincipal;
import com.ngockhanh.clinic.identity.application.response.CsrfResponse;
import com.ngockhanh.clinic.identity.application.response.StaffLoginResult;
import com.ngockhanh.clinic.identity.application.response.StaffSessionResponse;
import com.ngockhanh.clinic.identity.application.usecase.AuthenticateStaffSessionUseCase;
import com.ngockhanh.clinic.identity.application.usecase.GetCsrfTokenUseCase;
import com.ngockhanh.clinic.identity.application.usecase.GetStaffSessionUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LogoutAllStaffSessionsUseCase;
import com.ngockhanh.clinic.identity.application.usecase.LogoutStaffSessionUseCase;
import com.ngockhanh.clinic.identity.application.usecase.StaffLoginUseCase;
import com.ngockhanh.clinic.identity.infrastructure.configuration.AuthSecurityConfiguration;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffAuthController.class)
@Import({AuthApiConfiguration.class, AuthSecurityConfiguration.class, ApiResponseWriter.class,
    ProductionAuthSecurityTest.ClockConfiguration.class})
@ActiveProfiles("production")
class ProductionAuthSecurityTest {
  @Autowired
  MockMvc mvc;

  @Autowired
  Clock clock;

  @Autowired
  tools.jackson.databind.json.JsonMapper json;

  @MockitoBean
  GetCsrfTokenUseCase getCsrfToken;

  @MockitoBean
  StaffLoginUseCase staffLogin;

  @MockitoBean
  GetStaffSessionUseCase getStaffSession;

  @MockitoBean
  LogoutStaffSessionUseCase logoutStaffSession;

  @MockitoBean
  LogoutAllStaffSessionsUseCase logoutAllStaffSessions;

  @MockitoBean
  AuthenticateStaffSessionUseCase authenticateStaffSession;

  @Test
  void authenticatedStaffCannotAccessBusinessEndpointsWithoutPolicy() throws Exception {
    var now = clock.instant();
    var principal = principal(now);
    when(authenticateStaffSession.execute(any())).thenReturn(principal);
    when(getStaffSession.execute(any())).thenReturn(StaffSessionResponse.from(principal));

    mvc.perform(get("/api/v1/organizations").servletPath("/api/v1/organizations")
            .cookie(new Cookie("NKC_SESSION", "A".repeat(43))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.result").value("NG"))
        .andExpect(jsonPath("$.code").value(403));

    mvc.perform(get("/api/v1/auth/me").servletPath("/api/v1/auth/me")
            .cookie(new Cookie("NKC_SESSION", "A".repeat(43))))
        .andExpect(status().isOk());
    verify(authenticateStaffSession, times(2))
        .execute(argThat(query -> query.sessionIds().equals(List.of("A".repeat(43)))));
    verify(getStaffSession).execute(argThat(query -> query.principal().equals(principal)));
  }

  @Test
  void csrfCookieIsSecureAndHttpOnlyInProduction() throws Exception {
    when(getCsrfToken.execute(any())).thenAnswer(invocation -> {
      GetCsrfTokenQuery query = invocation.getArgument(0);
      return new CsrfResponse(query.token(), query.headerName());
    });
    mvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(cookie().secure("XSRF-TOKEN", true))
        .andExpect(cookie().httpOnly("XSRF-TOKEN", true))
        .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"));
    verify(getCsrfToken).execute(argThat(query -> query.headerName().equals("X-XSRF-TOKEN")
        && query.token() != null && !query.token().isBlank()));
  }

  @Test
  void bearerTokensAreNotAcceptedAndRedisFailureIsJson503() throws Exception {
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer arbitrary-jwt"))
        .andExpect(status().isUnauthorized());

    when(authenticateStaffSession.execute(any()))
        .thenThrow(AuthenticationFailure.unavailable(new IllegalStateException("Simulated unavailable dependency")));
    mvc.perform(get("/api/v1/auth/me").servletPath("/api/v1/auth/me")
            .cookie(new Cookie("NKC_SESSION", "A".repeat(43))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.result").value("NG"))
        .andExpect(jsonPath("$.code").value(503))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void loginMapsHttpInputToOneCommandAndReturnsOnlyOpaqueSessionCookie() throws Exception {
    when(getCsrfToken.execute(any())).thenAnswer(invocation -> {
      GetCsrfTokenQuery query = invocation.getArgument(0);
      return new CsrfResponse(query.token(), query.headerName());
    });
    var principal = principal(clock.instant());
    when(staffLogin.execute(any())).thenReturn(new StaffLoginResult(
        "A".repeat(43), StaffSessionResponse.from(principal)));

    var csrfResponse = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
    var csrf = json.readTree(csrfResponse.getContentAsString()).get("data");
    var result = mvc.perform(post("/api/v1/auth/staff/login").servletPath("/api/v1/auth/staff/login")
            .cookie(csrfResponse.getCookie("XSRF-TOKEN"))
            .header(csrf.get("headerName").asText(), csrf.get("token").asText())
            .header("X-Forwarded-For", "203.0.113.8")
            .contentType("application/json").content("{\"username\":\"staff\",\"password\":\"entered-password\"}"))
        .andExpect(status().isOk())
        .andExpect(cookie().secure("NKC_SESSION", true))
        .andExpect(cookie().httpOnly("NKC_SESSION", true))
        .andExpect(cookie().path("NKC_SESSION", "/"))
        .andExpect(cookie().attribute("NKC_SESSION", "SameSite", "Lax"))
        .andExpect(cookie().doesNotExist("JSESSIONID"))
        .andExpect(jsonPath("$.data.jwt").doesNotExist())
        .andExpect(jsonPath("$.data.sessionId").doesNotExist())
        .andReturn();

    assertThat(result.getRequest().getSession(false)).isNull();
    var session = result.getResponse().getCookie("NKC_SESSION");
    assertThat(session.getDomain()).isNull();
    assertThat(session.getMaxAge()).isBetween(28790, 28800);
    verify(staffLogin).execute(argThat((StaffLoginCommand command) -> command.username().equals("staff")
        && command.password().equals("entered-password") && command.clientIp().equals("127.0.0.1")
        && command.sessionIds().isEmpty()));
  }

  @Test
  void logoutMapsCookieToOneCommandAndClearsCookies() throws Exception {
    when(getCsrfToken.execute(any())).thenAnswer(invocation -> {
      GetCsrfTokenQuery query = invocation.getArgument(0);
      return new CsrfResponse(query.token(), query.headerName());
    });
    var csrfResponse = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
    var csrf = json.readTree(csrfResponse.getContentAsString()).get("data");
    String sessionId = "B".repeat(43);

    mvc.perform(post("/api/v1/auth/logout").servletPath("/api/v1/auth/logout")
            .cookie(csrfResponse.getCookie("XSRF-TOKEN"), new Cookie("NKC_SESSION", sessionId))
            .header(csrf.get("headerName").asText(), csrf.get("token").asText()))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("NKC_SESSION=;")));

    verify(logoutStaffSession).execute(argThat((LogoutStaffSessionCommand command) ->
        command.sessionIds().equals(List.of(sessionId)) && command.correlationId() != null));
  }

  @Test
  void logoutAllMapsAuthenticatedUserToOneCommand() throws Exception {
    when(getCsrfToken.execute(any())).thenAnswer(invocation -> {
      GetCsrfTokenQuery query = invocation.getArgument(0);
      return new CsrfResponse(query.token(), query.headerName());
    });
    var principal = principal(clock.instant());
    when(authenticateStaffSession.execute(any())).thenReturn(principal);
    var csrfResponse = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
    var csrf = json.readTree(csrfResponse.getContentAsString()).get("data");

    mvc.perform(post("/api/v1/auth/logout-all").servletPath("/api/v1/auth/logout-all")
            .cookie(csrfResponse.getCookie("XSRF-TOKEN"), new Cookie("NKC_SESSION", "C".repeat(43)))
            .header(csrf.get("headerName").asText(), csrf.get("token").asText()))
        .andExpect(status().isNoContent());

    verify(logoutAllStaffSessions).execute(argThat((LogoutAllStaffSessionsCommand command) ->
        command.userId().equals(principal.userId()) && command.correlationId() != null));
  }

  private StaffPrincipal principal(Instant now) {
    return new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID(), "staff", "STAFF",
        List.of(new StaffPrincipal.Assignment(UUID.randomUUID(), "DOCTOR", List.of("READ"),
            null, null, now.minusSeconds(10), null)), now.plusSeconds(1800), now.plusSeconds(28800));
  }

  @TestConfiguration
  static class ClockConfiguration {
    @Bean
    Clock testClock() {
      return Clock.fixed(Instant.now(), ZoneOffset.UTC);
    }
  }
}
