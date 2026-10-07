package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.BatchSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.*;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class OrganizationBatchControllerTest {
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private static UsernamePasswordAuthenticationToken signedInAs(UUID actorId) {
    var principal =
        UserPrincipal.builder()
            .userId(actorId)
            .staffId(UUID.randomUUID())
            .username("staff")
            .principalType("STAFF")
            .roleAssignments(List.of())
            .idleExpiresAt(java.time.Instant.now())
            .absoluteExpiresAt(java.time.Instant.now().plusSeconds(3600))
            .build();
    var authentication = new UsernamePasswordAuthenticationToken(principal, "test", List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication;
  }

  @Test
  void listReturnsPageForTheOrganizationInThePath() throws Exception {
    var list = mock(ListHealthExaminationBatchUseCase.class);
    UUID organizationId = UUID.randomUUID();
    var page =
        PageResponse.<BatchSummaryResponse>builder()
            .items(List.of())
            .page(2)
            .size(5)
            .totalElements(11)
            .totalPages(3)
            .build();
    when(list.execute(eq(organizationId), any(HealthExaminationBatchListQuery.class)))
        .thenReturn(page);
    var controller =
        new OrganizationBatchController(mock(CreateHealthExaminationBatchUseCase.class), list);
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
        .isEqualTo(
            HealthExaminationBatchListQuery.builder()
                .page(2)
                .size(5)
                .searchKey("Clinic")
                .sortKey("startDate")
                .sortBy("DESC")
                .build());
  }

  @Test
  void createPassesSignedInActorAndOnlyEnteredPriceAndRejectsBadInput() throws Exception {
    var create = mock(CreateHealthExaminationBatchUseCase.class);
    UUID actor = UUID.randomUUID(), org = UUID.randomUUID(), service = UUID.randomUUID();
    signedInAs(actor);
    var controller =
        new OrganizationBatchController(create, mock(ListHealthExaminationBatchUseCase.class));
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
    var authentication = signedInAs(actorId);
    var controller =
        new OrganizationBatchController(create, mock(ListHealthExaminationBatchUseCase.class));
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
    mvc.perform(
            post("/api/v1/organizations/{organizationId}/health-examination-batches", organizationId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
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
    var controller =
        new OrganizationBatchController(create, mock(ListHealthExaminationBatchUseCase.class));
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

}
