package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.healthexamination.application.command.DeleteOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetOrganizationByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateOrganizationUseCase;
import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import com.ngockhanh.clinic.identity.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.identity.infrastructure.configuration.AuthHttpSettings;
import com.ngockhanh.clinic.identity.infrastructure.configuration.AuthSecurityConfiguration;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises the real security filter chain (local/test policy) in front of the organization
 * controller: session authentication, STAFF-with-role access, and CSRF on unsafe methods.
 */
@WebMvcTest({OrganizationController.class, OrganizationSecurityTest.CsrfEndpoint.class})
@Import({
  AuthSecurityConfiguration.class,
  ApiResponseWriter.class,
  OrganizationSecurityTest.Settings.class,
  OrganizationSecurityTest.CsrfEndpoint.class
})
@ActiveProfiles("test")
class OrganizationSecurityTest {
  private static final String SESSION = "A".repeat(43);
  private static final String ORGANIZATION_BODY =
      """
      {"name":"Clinic Corp","taxCode":"0101234567","phone":"0901","email":"org@example.test","address":"123 Street","contactFullName":"Nguyen Van A","contactPhone":"0901234567","contactEmail":"contact@example.test","rowVersion":0}
      """;

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;

  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean CreateOrganizationUseCase createUseCase;
  @MockitoBean GetOrganizationByIdUseCase getUseCase;
  @MockitoBean UpdateOrganizationUseCase updateUseCase;
  @MockitoBean ListOrganizationUseCase listUseCase;
  @MockitoBean DeleteOrganizationUseCase deleteUseCase;

  private UserPrincipal principal(String type, boolean withRole, UUID userId) {
    Instant now = Instant.now();
    var assignment =
        UserPrincipal.Assignment.builder()
            .roleId(UUID.randomUUID())
            .roleCode("DOCTOR")
            .permissions(List.of())
            .grantedBy(UUID.randomUUID())
            .grantedAt(now.minusSeconds(60))
            .build();
    return UserPrincipal.builder()
        .userId(userId)
        .staffId(type.equals("STAFF") ? UUID.randomUUID() : null)
        .patientId(type.equals("PATIENT") ? UUID.randomUUID() : null)
        .username("user")
        .principalType(type)
        .roleAssignments(withRole ? List.of(assignment) : List.of())
        .idleExpiresAt(now.plusSeconds(1800))
        .absoluteExpiresAt(now.plusSeconds(28800))
        .build();
  }

  private void signInAs(String type, boolean withRole, UUID userId) {
    when(authenticate.execute(any())).thenReturn(principal(type, withRole, userId));
  }

  private MockHttpServletRequestBuilder withSession(
      MockHttpServletRequestBuilder request, String path) {
    return request.servletPath(path).cookie(new Cookie("NKC_SESSION", SESSION));
  }

  /** Requests a CSRF token the same way a browser client does and attaches it to the request. */
  private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request)
      throws Exception {
    var response =
        mvc.perform(get("/api/v1/auth/csrf").servletPath("/api/v1/auth/csrf"))
            .andReturn()
            .getResponse();
    var body = json.readTree(response.getContentAsString());
    return request
        .cookie(response.getCookie("XSRF-TOKEN"))
        .header(body.get("headerName").asString(), body.get("token").asString());
  }

  @Test
  void unauthenticatedRequestsAreRejectedWith401OrCsrf403BeforeAnyUseCase() throws Exception {
    UUID id = UUID.randomUUID();
    String path = "/api/v1/organizations/" + id;

    mvc.perform(get("/api/v1/organizations").servletPath("/api/v1/organizations"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401));
    mvc.perform(get(path).servletPath(path)).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/organizations")
                .servletPath("/api/v1/organizations")
                .contentType("application/json")
                .content(ORGANIZATION_BODY))
        .andExpect(status().isForbidden());
    mvc.perform(
            put(path).servletPath(path).contentType("application/json").content(ORGANIZATION_BODY))
        .andExpect(status().isForbidden());
    mvc.perform(delete(path).servletPath(path).param("rowVersion", "0"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(createUseCase, getUseCase, updateUseCase, listUseCase, deleteUseCase);
  }

  @Test
  void patientsAndRolelessStaffAreDeniedOnEveryOrganizationRoute() throws Exception {
    UUID id = UUID.randomUUID();
    String path = "/api/v1/organizations/" + id;
    for (var denied : List.of(Map.entry("PATIENT", true), Map.entry("STAFF", false))) {
      signInAs(denied.getKey(), denied.getValue(), UUID.randomUUID());

      mvc.perform(withSession(get("/api/v1/organizations"), "/api/v1/organizations"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value(403));
      mvc.perform(withSession(get(path), path)).andExpect(status().isForbidden());
      mvc.perform(
              withCsrf(
                  withSession(
                      post("/api/v1/organizations")
                          .contentType("application/json")
                          .content(ORGANIZATION_BODY),
                      "/api/v1/organizations")))
          .andExpect(status().isForbidden());
      mvc.perform(
              withCsrf(
                  withSession(
                      put(path).contentType("application/json").content(ORGANIZATION_BODY), path)))
          .andExpect(status().isForbidden());
      mvc.perform(withCsrf(withSession(delete(path).param("rowVersion", "0"), path)))
          .andExpect(status().isForbidden());
    }

    verifyNoInteractions(createUseCase, getUseCase, updateUseCase, listUseCase, deleteUseCase);
  }

  @Test
  void staffWithRoleCanReadWithoutCsrf() throws Exception {
    signInAs("STAFF", true, UUID.randomUUID());
    when(listUseCase.execute(any()))
        .thenReturn(
            PageResponse.<OrganizationResponse>builder()
                .items(List.of())
                .page(1)
                .size(10)
                .totalElements(0)
                .totalPages(0)
                .build());

    mvc.perform(withSession(get("/api/v1/organizations"), "/api/v1/organizations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void unsafeMethodsWithoutCsrfAreBlockedEvenForStaffWithRole() throws Exception {
    UUID id = UUID.randomUUID();
    String path = "/api/v1/organizations/" + id;
    signInAs("STAFF", true, UUID.randomUUID());

    mvc.perform(
            withSession(
                post("/api/v1/organizations")
                    .contentType("application/json")
                    .content(ORGANIZATION_BODY),
                "/api/v1/organizations"))
        .andExpect(status().isForbidden());
    mvc.perform(
            withSession(put(path).contentType("application/json").content(ORGANIZATION_BODY), path))
        .andExpect(status().isForbidden());
    mvc.perform(withSession(delete(path).param("rowVersion", "0"), path))
        .andExpect(status().isForbidden());

    verifyNoInteractions(createUseCase, updateUseCase, deleteUseCase);
  }

  @Test
  void deleteWithValidCsrfUsesThePrincipalAccountAsActor() throws Exception {
    UUID id = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    String path = "/api/v1/organizations/" + id;
    signInAs("STAFF", true, accountId);

    mvc.perform(withCsrf(withSession(delete(path).param("rowVersion", "2"), path)))
        .andExpect(status().isNoContent());

    verify(deleteUseCase)
        .execute(
            eq(id), eq(DeleteOrganizationCommand.builder().rowVersion(2L).build()), eq(accountId));
  }

  @RestController
  static class CsrfEndpoint {
    @GetMapping("/api/v1/auth/csrf")
    Map<String, String> csrf(CsrfToken token) {
      return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
  }

  @TestConfiguration
  static class Settings {
    @Bean
    AuthHttpSettings authHttpSettings() {
      return new AuthHttpSettings(false, true, List.of("http://localhost:3000"), List.of());
    }
  }
}
