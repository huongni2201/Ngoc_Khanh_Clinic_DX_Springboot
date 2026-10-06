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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.response.BatchSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetHealthExaminationBatchByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateHealthExaminationBatchUseCase;
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
 * Exercises the real security filter chain (local/test policy) in front of the batch controller:
 * session authentication, STAFF-with-role access and CSRF on unsafe methods for all five routes.
 */
@WebMvcTest({OrganizationBatchController.class, OrganizationBatchSecurityTest.CsrfEndpoint.class})
@Import({
  AuthSecurityConfiguration.class,
  ApiResponseWriter.class,
  OrganizationBatchSecurityTest.Settings.class,
  OrganizationBatchSecurityTest.CsrfEndpoint.class
})
@ActiveProfiles("test")
class OrganizationBatchSecurityTest {
  private static final String SESSION = "A".repeat(43);
  private static final UUID ORGANIZATION = UUID.randomUUID();
  private static final UUID BATCH = UUID.randomUUID();
  private static final String COLLECTION =
      "/api/v1/organizations/" + ORGANIZATION + "/health-examination-batches";
  private static final String ITEM = COLLECTION + "/" + BATCH;
  private static final String BODY =
      """
      {"batchCode":"B1","batchName":"Campaign","examinationSiteType":"CLINIC","examinationSiteName":"Clinic","examinationSiteAddress":"Address","examinationDates":["2026-10-04"],"services":[{"serviceId":"%s","negotiatedPrice":12.34}],"rowVersion":0}
      """
          .formatted(UUID.randomUUID());

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;

  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean CreateHealthExaminationBatchUseCase createUseCase;
  @MockitoBean GetHealthExaminationBatchByIdUseCase getUseCase;
  @MockitoBean UpdateHealthExaminationBatchUseCase updateUseCase;
  @MockitoBean ListHealthExaminationBatchUseCase listUseCase;
  @MockitoBean DeleteHealthExaminationBatchUseCase deleteUseCase;

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

  private void verifyNoUseCaseWasCalled() {
    verifyNoInteractions(createUseCase, getUseCase, updateUseCase, listUseCase, deleteUseCase);
  }

  @Test
  void unauthenticatedRequestsAreRejectedWith401OrCsrf403BeforeAnyUseCase() throws Exception {
    mvc.perform(get(COLLECTION).servletPath(COLLECTION))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401));
    mvc.perform(get(ITEM).servletPath(ITEM)).andExpect(status().isUnauthorized());
    mvc.perform(
            post(COLLECTION).servletPath(COLLECTION).contentType("application/json").content(BODY))
        .andExpect(status().isForbidden());
    mvc.perform(put(ITEM).servletPath(ITEM).contentType("application/json").content(BODY))
        .andExpect(status().isForbidden());
    mvc.perform(delete(ITEM).servletPath(ITEM).param("rowVersion", "0"))
        .andExpect(status().isForbidden());

    verifyNoUseCaseWasCalled();
  }

  @Test
  void patientsAndRolelessStaffAreDeniedOnEveryBatchRoute() throws Exception {
    for (var denied : List.of(Map.entry("PATIENT", true), Map.entry("STAFF", false))) {
      signInAs(denied.getKey(), denied.getValue(), UUID.randomUUID());

      mvc.perform(withSession(get(COLLECTION), COLLECTION))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value(403));
      mvc.perform(withSession(get(ITEM), ITEM)).andExpect(status().isForbidden());
      mvc.perform(
              withCsrf(
                  withSession(
                      post(COLLECTION).contentType("application/json").content(BODY), COLLECTION)))
          .andExpect(status().isForbidden());
      mvc.perform(
              withCsrf(withSession(put(ITEM).contentType("application/json").content(BODY), ITEM)))
          .andExpect(status().isForbidden());
      mvc.perform(withCsrf(withSession(delete(ITEM).param("rowVersion", "0"), ITEM)))
          .andExpect(status().isForbidden());
    }

    verifyNoUseCaseWasCalled();
  }

  @Test
  void staffWithRoleCanReadWithoutCsrf() throws Exception {
    signInAs("STAFF", true, UUID.randomUUID());
    when(listUseCase.execute(eq(ORGANIZATION), any()))
        .thenReturn(
            PageResponse.<BatchSummaryResponse>builder()
                .items(List.of())
                .page(1)
                .size(10)
                .totalElements(0)
                .totalPages(0)
                .build());
    when(getUseCase.execute(ORGANIZATION, BATCH))
        .thenReturn(BatchDetailResponse.builder().id(BATCH).organizationId(ORGANIZATION).build());

    mvc.perform(withSession(get(COLLECTION), COLLECTION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    mvc.perform(withSession(get(ITEM), ITEM))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(BATCH.toString()));
  }

  @Test
  void unsafeMethodsWithoutCsrfAreBlockedEvenForStaffWithRole() throws Exception {
    signInAs("STAFF", true, UUID.randomUUID());

    mvc.perform(
            withSession(post(COLLECTION).contentType("application/json").content(BODY), COLLECTION))
        .andExpect(status().isForbidden());
    mvc.perform(withSession(put(ITEM).contentType("application/json").content(BODY), ITEM))
        .andExpect(status().isForbidden());
    mvc.perform(withSession(delete(ITEM).param("rowVersion", "0"), ITEM))
        .andExpect(status().isForbidden());

    verifyNoInteractions(createUseCase, updateUseCase, deleteUseCase);
  }

  @Test
  void createUpdateAndDeleteUseThePrincipalAccountAsActorWhenCsrfIsValid() throws Exception {
    UUID accountId = UUID.randomUUID();
    signInAs("STAFF", true, accountId);
    var response = BatchDetailResponse.builder().id(BATCH).organizationId(ORGANIZATION).build();
    when(createUseCase.execute(eq(ORGANIZATION), any(), eq(accountId))).thenReturn(response);
    when(updateUseCase.execute(eq(ORGANIZATION), eq(BATCH), any(), eq(accountId)))
        .thenReturn(response);

    mvc.perform(
            withCsrf(
                withSession(
                    post(COLLECTION).contentType("application/json").content(BODY), COLLECTION)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"));
    mvc.perform(
            withCsrf(withSession(put(ITEM).contentType("application/json").content(BODY), ITEM)))
        .andExpect(status().isOk());
    mvc.perform(withCsrf(withSession(delete(ITEM).param("rowVersion", "2"), ITEM)))
        .andExpect(status().isNoContent());

    verify(createUseCase).execute(eq(ORGANIZATION), any(), eq(accountId));
    verify(updateUseCase).execute(eq(ORGANIZATION), eq(BATCH), any(), eq(accountId));
    verify(deleteUseCase)
        .execute(
            eq(ORGANIZATION),
            eq(BATCH),
            eq(DeleteHealthExaminationBatchCommand.builder().rowVersion(2L).build()),
            eq(accountId));
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
