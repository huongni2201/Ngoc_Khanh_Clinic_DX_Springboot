package com.ngockhanh.clinic.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import com.ngockhanh.clinic.identity.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.identity.infrastructure.configuration.AuthHttpSettings;
import com.ngockhanh.clinic.identity.infrastructure.configuration.AuthSecurityConfiguration;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(LocalAuthSecurityTest.BusinessEndpoint.class)
@Import({
  AuthSecurityConfiguration.class,
  ApiResponseWriter.class,
  LocalAuthSecurityTest.Settings.class,
  LocalAuthSecurityTest.BusinessEndpoint.class
})
@ActiveProfiles("test")
class LocalAuthSecurityTest {
  @Autowired MockMvc mvc;
  @MockitoBean AuthenticateSessionUseCase authenticate;

  @Test
  void onlyStaffWithEffectiveAssignmentsCanAccessDevelopmentBusinessEndpoints() throws Exception {
    Instant now = Instant.now();
    var assignment =
        new UserPrincipal.Assignment(
            UUID.randomUUID(), "DOCTOR", List.of(), UUID.randomUUID(), now.minusSeconds(60));
    for (String type : List.of("STAFF", "PATIENT")) {
      for (boolean hasRoles : List.of(true, false)) {
        var principal =
            new UserPrincipal(
                UUID.randomUUID(),
                type.equals("STAFF") ? UUID.randomUUID() : null,
                type.equals("PATIENT") ? UUID.randomUUID() : null,
                "user",
                type,
                hasRoles ? List.of(assignment) : List.of(),
                now.plusSeconds(1800),
                now.plusSeconds(28800));
        when(authenticate.execute(any())).thenReturn(principal);
        var result =
            mvc.perform(
                get("/api/v1/test-business")
                    .servletPath("/api/v1/test-business")
                    .cookie(new Cookie("NKC_SESSION", "A".repeat(43))));
        if (type.equals("STAFF") && hasRoles) {
          result.andExpect(status().isOk());
        } else {
          result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        }
      }
    }
  }

  @Test
  void rosterImportRoutesRequireTheClinicManagerRole() throws Exception {
    Instant now = Instant.now();
    String path =
        "/api/v1/organizations/org/health-examination-batches/batch/participant-imports/job";
    for (String roleCode : List.of("DOCTOR", "CLINIC_MANAGER")) {
      var assignment =
          new UserPrincipal.Assignment(
              UUID.randomUUID(), roleCode, List.of(), UUID.randomUUID(), now.minusSeconds(60));
      var principal =
          new UserPrincipal(
              UUID.randomUUID(),
              UUID.randomUUID(),
              null,
              "staff",
              "STAFF",
              List.of(assignment),
              now.plusSeconds(1800),
              now.plusSeconds(28800));
      when(authenticate.execute(any())).thenReturn(principal);

      var response =
          mvc.perform(
              get(path).servletPath(path).cookie(new Cookie("NKC_SESSION", "B".repeat(43))));
      if (roleCode.equals("CLINIC_MANAGER")) {
        response.andExpect(status().isOk());
      } else {
        response.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
      }
    }
  }

  @RestController
  static class BusinessEndpoint {
    @GetMapping("/api/v1/test-business")
    String read() {
      return "allowed";
    }

    @GetMapping(
        "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participant-imports/{importId}")
    String readParticipantImport() {
      return "allowed";
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
