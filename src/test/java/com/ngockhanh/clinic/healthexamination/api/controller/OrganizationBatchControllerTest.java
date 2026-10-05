package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.BatchSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.*;
import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class OrganizationBatchControllerTest {
  @Test
  void listReturnsPageForTheOrganizationInThePath() throws Exception {
    var list = mock(ListHealthExaminationBatchUseCase.class);
    UUID organizationId = UUID.randomUUID();
    var page = new PageResponse<BatchSummaryResponse>(List.of(), 2, 5, 11, 3);
    when(list.execute(eq(organizationId), any(HealthExaminationBatchListQuery.class)))
        .thenReturn(page);
    var controller =
        new OrganizationBatchController(
            mock(CreateHealthExaminationBatchUseCase.class), list, new MockEnvironment());
    var mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(
                new GlobalExceptionHandler(new ApiResponseWriter(JsonMapper.builder().build())))
            .build();

    mvc.perform(
            get("/api/v1/organizations/{organizationId}/health-examination-batches", organizationId)
                .param("page", "2")
                .param("size", "5")
                .param("searchKey", "Clinic")
                .param("sortKey", "startDate")
                .param("sortBy", "DESC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(11));

    var query = ArgumentCaptor.forClass(HealthExaminationBatchListQuery.class);
    verify(list).execute(eq(organizationId), query.capture());
    assertThat(query.getValue())
        .isEqualTo(new HealthExaminationBatchListQuery(2, 5, "Clinic", "startDate", "DESC"));
  }

  @Test
  void createPassesLocalActorAndOnlyEnteredPriceAndRejectsBadInput() throws Exception {
    var create = mock(CreateHealthExaminationBatchUseCase.class);
    UUID actor = UUID.randomUUID(), org = UUID.randomUUID(), service = UUID.randomUUID();
    var env = new MockEnvironment();
    env.setActiveProfiles("local");
    env.setProperty("clinic.health-examination.batch.mock-created-by", actor.toString());
    var controller =
        new OrganizationBatchController(create, mock(ListHealthExaminationBatchUseCase.class), env);
    var mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(
                new GlobalExceptionHandler(new ApiResponseWriter(JsonMapper.builder().build())))
            .build();
    String path = "/api/v1/organizations/" + org + "/health-examination-batches";
    String body =
        """
        {"batchCode":"B1","batchName":"Campaign","examinationSiteType":"CLINIC","examinationSiteName":"Clinic","examinationSiteAddress":"Address","examinationDates":["2026-10-04"],
         "services":[{"serviceId":"%s","negotiatedPrice":12.34}]}
        """
            .formatted(service);
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
    var command =
        ArgumentCaptor.forClass(
            com.ngockhanh.clinic.healthexamination.application.command
                .CreateHealthExaminationBatchCommand.class);
    verify(create).execute(eq(org), command.capture());
    assertThat(command.getValue().createdBy()).isEqualTo(actor);
    assertThat(command.getValue().configuration().services().getFirst().negotiatedPrice())
        .isEqualByComparingTo("12.34");
    mvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content(body.replace("12.34", "-1")))
        .andExpect(status().isBadRequest());
    mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path).param("page", "0")).andExpect(status().isBadRequest());
    mvc.perform(get(path).param("sortKey", "id; drop table services"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("[\"2026-10-04\"]", "[]")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("CLINIC", "COMPANY")))
        .andExpect(status().isBadRequest());
    verify(create, times(1)).execute(any(), any());
  }

  @Test
  void createUsesUserPrincipalIdInsteadOfAuthenticationName() throws Exception {
    var create = mock(CreateHealthExaminationBatchUseCase.class);
    UUID actorId = UUID.randomUUID(),
        organizationId = UUID.randomUUID(),
        serviceId = UUID.randomUUID();
    var principal =
        new UserPrincipal(
            actorId,
            UUID.randomUUID(),
            null,
            "staff",
            "STAFF",
            java.util.List.of(),
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(3600));
    var authentication =
        new UsernamePasswordAuthenticationToken(principal, "test", java.util.List.of());
    var environment = new MockEnvironment();
    environment.setActiveProfiles("production");
    var controller =
        new OrganizationBatchController(
            create, mock(ListHealthExaminationBatchUseCase.class), environment);
    var mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(
                new GlobalExceptionHandler(new ApiResponseWriter(JsonMapper.builder().build())))
            .build();
    String body =
        """
        {"batchCode":"B1","batchName":"Campaign","examinationSiteType":"CLINIC","examinationSiteName":"Clinic","examinationSiteAddress":"Address","examinationDates":["2026-10-04"],"services":[{"serviceId":"%s","negotiatedPrice":12.34}]}
        """
            .formatted(serviceId);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      mvc.perform(
              post(
                      "/api/v1/organizations/{organizationId}/health-examination-batches",
                      organizationId)
                  .principal(authentication)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isCreated());
    } finally {
      SecurityContextHolder.clearContext();
    }
    var command =
        ArgumentCaptor.forClass(
            com.ngockhanh.clinic.healthexamination.application.command
                .CreateHealthExaminationBatchCommand.class);
    verify(create).execute(eq(organizationId), command.capture());
    assertThat(command.getValue().createdBy()).isEqualTo(actorId);
  }

  @Test
  void rejectsInvalidTransportBeforeCallingUseCases() throws Exception {
    var create = mock(CreateHealthExaminationBatchUseCase.class);
    var env = new MockEnvironment();
    env.setActiveProfiles("local");
    env.setProperty(
        "clinic.health-examination.batch.mock-created-by", UUID.randomUUID().toString());
    var controller =
        new OrganizationBatchController(create, mock(ListHealthExaminationBatchUseCase.class), env);
    var mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(
                new GlobalExceptionHandler(new ApiResponseWriter(JsonMapper.builder().build())))
            .build();
    mvc.perform(
            post("/api/v1/organizations/" + UUID.randomUUID() + "/health-examination-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.result").value("NG"));
    verifyNoInteractions(create);
  }

  @Test
  void mockActorIsDeniedOutsideLocalAndTest() {
    var create = mock(CreateHealthExaminationBatchUseCase.class);
    var env =
        new MockEnvironment()
            .withProperty(
                "clinic.health-examination.batch.mock-created-by", UUID.randomUUID().toString());
    env.setActiveProfiles("production");
    var controller =
        new OrganizationBatchController(create, mock(ListHealthExaminationBatchUseCase.class), env);
    assertThatThrownBy(
            () ->
                controller.create(
                    UUID.randomUUID(),
                    new com.ngockhanh.clinic.healthexamination.api.request
                        .HealthExaminationBatchRequest(
                        "B1",
                        "Batch",
                        java.util.List.of(java.time.LocalDate.of(2026, 10, 4)),
                        "CLINIC",
                        "Clinic",
                        "Address",
                        java.util.List.of(
                            new com.ngockhanh.clinic.healthexamination.api.request
                                .HealthExaminationBatchRequest.ServicePriceRequest(
                                UUID.randomUUID(), java.math.BigDecimal.ONE))),
                    null))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(create);
  }
}
