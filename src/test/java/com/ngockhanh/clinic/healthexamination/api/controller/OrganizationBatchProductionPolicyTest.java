package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The production policy still denies every business endpoint, batches included. */
@WebMvcTest(OrganizationBatchController.class)
@Import({
  AuthSecurityConfiguration.class,
  ApiResponseWriter.class,
  OrganizationBatchProductionPolicyTest.Settings.class
})
@ActiveProfiles("production")
class OrganizationBatchProductionPolicyTest {
  private static final String COLLECTION =
      "/api/v1/organizations/" + UUID.randomUUID() + "/health-examination-batches";
  private static final String ITEM = COLLECTION + "/" + UUID.randomUUID();

  @Autowired MockMvc mvc;

  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean CreateHealthExaminationBatchUseCase createUseCase;
  @MockitoBean GetHealthExaminationBatchByIdUseCase getUseCase;
  @MockitoBean UpdateHealthExaminationBatchUseCase updateUseCase;
  @MockitoBean ListHealthExaminationBatchUseCase listUseCase;
  @MockitoBean DeleteHealthExaminationBatchUseCase deleteUseCase;

  private MockHttpServletRequestBuilder withSession(
      MockHttpServletRequestBuilder request, String path) {
    return request.servletPath(path).cookie(new Cookie("NKC_SESSION", "A".repeat(43)));
  }

  @Test
  void authenticatedStaffWithARoleCannotReachAnyBatchRoute() throws Exception {
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
                .staffId(UUID.randomUUID())
                .patientId(null)
                .username("staff")
                .principalType("STAFF")
                .roleAssignments(List.of(assignment))
                .idleExpiresAt(now.plusSeconds(1800))
                .absoluteExpiresAt(now.plusSeconds(28800))
                .build());

    mvc.perform(withSession(get(COLLECTION), COLLECTION))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403));
    mvc.perform(withSession(get(ITEM), ITEM)).andExpect(status().isForbidden());
    mvc.perform(
            withSession(post(COLLECTION).contentType("application/json").content("{}"), COLLECTION))
        .andExpect(status().isForbidden());
    mvc.perform(withSession(put(ITEM).contentType("application/json").content("{}"), ITEM))
        .andExpect(status().isForbidden());
    mvc.perform(withSession(delete(ITEM).param("rowVersion", "0"), ITEM))
        .andExpect(status().isForbidden());

    verifyNoInteractions(createUseCase, getUseCase, updateUseCase, listUseCase, deleteUseCase);
  }

  @TestConfiguration
  static class Settings {
    @Bean
    AuthHttpSettings authHttpSettings() {
      return new AuthHttpSettings(true, false, List.of(), List.of());
    }
  }
}
