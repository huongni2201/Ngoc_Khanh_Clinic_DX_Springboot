package com.ngockhanh.clinic.catalog.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.catalog.application.response.ServiceCatalogItemResponse;
import com.ngockhanh.clinic.catalog.application.usecase.ListServiceCatalogUseCase;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Exercises the real security filter chain (local/test policy) in front of the catalog list route:
 * session authentication and STAFF-with-role access. The route is a GET, so CSRF does not apply.
 */
@WebMvcTest(ServiceCatalogController.class)
@Import({
  AuthSecurityConfiguration.class,
  ApiResponseWriter.class,
  ServiceCatalogSecurityTest.Settings.class
})
@ActiveProfiles("test")
class ServiceCatalogSecurityTest {
  private static final String SESSION = "A".repeat(43);
  private static final String PATH = "/api/v1/catalog/services";

  @Autowired MockMvc mvc;

  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean ListServiceCatalogUseCase listUseCase;

  private void signInAs(String type, boolean withRole) {
    Instant now = Instant.now();
    var assignment =
        UserPrincipal.Assignment.builder()
            .roleId(UUID.randomUUID())
            .roleCode("DOCTOR")
            .permissions(List.of())
            .grantedBy(UUID.randomUUID())
            .grantedAt(now.minusSeconds(60))
            .build();
    when(authenticate.execute(any()))
        .thenReturn(
            UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .staffId(type.equals("STAFF") ? UUID.randomUUID() : null)
                .patientId(type.equals("PATIENT") ? UUID.randomUUID() : null)
                .username("user")
                .principalType(type)
                .roleAssignments(withRole ? List.of(assignment) : List.of())
                .idleExpiresAt(now.plusSeconds(1800))
                .absoluteExpiresAt(now.plusSeconds(28800))
                .build());
  }

  private MockHttpServletRequestBuilder withSession() {
    return get(PATH).servletPath(PATH).cookie(new Cookie("NKC_SESSION", SESSION));
  }

  @Test
  void unauthenticatedRequestsAreRejectedWith401BeforeTheUseCase() throws Exception {
    mvc.perform(get(PATH).servletPath(PATH))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401));

    verifyNoInteractions(listUseCase);
  }

  @Test
  void patientsAndRolelessStaffAreDenied() throws Exception {
    for (var denied : List.of(Map.entry("PATIENT", true), Map.entry("STAFF", false))) {
      signInAs(denied.getKey(), denied.getValue());

      mvc.perform(withSession())
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value(403));
    }

    verifyNoInteractions(listUseCase);
  }

  @Test
  void staffWithRoleCanListWithoutCsrf() throws Exception {
    signInAs("STAFF", true);
    when(listUseCase.execute(any()))
        .thenReturn(
            PageResponse.<ServiceCatalogItemResponse>builder()
                .items(List.of())
                .page(1)
                .size(10)
                .totalElements(0)
                .totalPages(0)
                .build());

    mvc.perform(withSession())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @TestConfiguration
  static class Settings {
    @Bean
    AuthHttpSettings authHttpSettings() {
      return new AuthHttpSettings(false, true, List.of("http://localhost:3000"), List.of());
    }
  }
}
