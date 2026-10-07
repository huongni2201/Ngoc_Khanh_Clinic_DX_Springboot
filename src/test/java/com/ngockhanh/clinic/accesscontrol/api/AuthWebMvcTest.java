package com.ngockhanh.clinic.accesscontrol.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.accesscontrol.api.controller.AuthController;
import com.ngockhanh.clinic.accesscontrol.application.exception.AuthenticationFailure;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.LoginResult;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.LoginUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.LogoutUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.shared.exception.DependencyUnavailableException;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
    controllers = {AuthController.class, AuthWebMvcTest.BusinessEndpoint.class},
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  ApiResponseWriter.class,
  AuthWebMvcTest.BusinessEndpoint.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class AuthWebMvcTest {
  private static final String COOKIE = "__Host-NKC_SESSION";
  private static final String ORIGIN = "https://app.test";

  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;
  @MockitoBean LoginUseCase login;
  @MockitoBean LogoutUseCase logout;
  @MockitoBean AuthenticateSessionUseCase authenticate;

  @RestController
  static class BusinessEndpoint {
    @GetMapping("/api/v1/test-business")
    String read() {
      return "allowed";
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
  void securityAndSharedConfigurationProvideOneUtcClock() {
    assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
    assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void defaultUserAndFormLoginAreUnavailable() throws Exception {
    assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    mvc.perform(get("/login")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Basic dXNlcjpwYXNzd29yZA=="))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginSetsOnlyAnOpaqueSessionCookie() throws Exception {
    when(login.execute(any()))
        .thenReturn(
            LoginResult.builder()
                .sessionId("S".repeat(43))
                .principal(principal("STAFF", List.of()))
                .build());

    mvc.perform(
            post("/api/v1/auth/login")
                .header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\" staff \",\"password\":\" secret \"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
        .andExpect(
            header()
                .string(
                    "Access-Control-Expose-Headers",
                    allOf(containsString("Content-Disposition"), containsString("Retry-After"))))
        .andExpect(jsonPath("$.data.principalType").value("STAFF"))
        .andExpect(jsonPath("$.data.sessionId").doesNotExist())
        .andExpect(
            header()
                .string(
                    "Set-Cookie",
                    allOf(
                        containsString(COOKIE + "=" + "S".repeat(43)),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Lax"),
                        containsString("Path=/"),
                        not(containsString("Max-Age")),
                        not(containsString("Domain")))));
    verify(login)
        .execute(
            argThat(
                command ->
                    command.username().equals(" staff ") && command.password().equals(" secret ")));
  }

  @Test
  void loginPreflightAllowsConfiguredOriginAndCredentials() throws Exception {
    mvc.perform(
            options("/api/v1/auth/login")
                .header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
        .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
        .andExpect(header().string("Access-Control-Allow-Headers", containsString("content-type")));
  }

  @Test
  void rejectedLoginIsUnauthorizedWithoutCookie() throws Exception {
    when(login.execute(any())).thenThrow(AuthenticationFailure.invalidCredentials());
    mvc.perform(
            post("/api/v1/auth/login")
                .header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"staff\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.result").value("NG"))
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void stateChangingRequestFromUnknownOriginIsForbidden() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .header("Origin", "https://evil.test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"staff\",\"password\":\"secret\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"staff\",\"password\":\"secret\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void meReturnsThePrincipalForStaffAndPatients() throws Exception {
    when(authenticate.execute("sid")).thenReturn(principal("PATIENT", List.of()));
    mvc.perform(get("/api/v1/auth/me").cookie(new Cookie(COOKIE, "sid")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.principalType").value("PATIENT"));
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void businessRoutesRequireAStaffAccount() throws Exception {
    mvc.perform(get("/api/v1/test-business"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401));

    when(authenticate.execute("staff")).thenReturn(principal("STAFF", List.of()));
    mvc.perform(get("/api/v1/test-business").cookie(new Cookie(COOKIE, "staff")))
        .andExpect(status().isOk());

    when(authenticate.execute("patient")).thenReturn(principal("PATIENT", List.of()));
    mvc.perform(get("/api/v1/test-business").cookie(new Cookie(COOKIE, "patient")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403));
  }

  @Test
  void permissionNamedLikeAnAccountTypeDoesNotGrantStaffAccess() throws Exception {
    when(authenticate.execute("patient"))
        .thenReturn(principal("PATIENT", List.of("ACCOUNT_STAFF")));
    mvc.perform(get("/api/v1/test-business").cookie(new Cookie(COOKIE, "patient")))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidSessionIsUnauthorizedAndUnavailableStoreIs503() throws Exception {
    when(authenticate.execute("expired")).thenThrow(AuthenticationFailure.unauthenticated());
    mvc.perform(get("/api/v1/test-business").cookie(new Cookie(COOKIE, "expired")))
        .andExpect(status().isUnauthorized());

    when(authenticate.execute("down"))
        .thenThrow(new DependencyUnavailableException("down", new IllegalStateException()));
    mvc.perform(get("/api/v1/test-business").cookie(new Cookie(COOKIE, "down")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value(503));
  }

  @Test
  void logoutAlwaysClearsTheCookie() throws Exception {
    mvc.perform(
            post("/api/v1/auth/logout").header("Origin", ORIGIN).cookie(new Cookie(COOKIE, "sid")))
        .andExpect(status().isNoContent())
        .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    verify(logout).execute(argThat(command -> "sid".equals(command.sessionId())));
  }

  @Test
  void bearerTokensAreNotAccepted() throws Exception {
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer anything"))
        .andExpect(status().isUnauthorized());
  }
}
