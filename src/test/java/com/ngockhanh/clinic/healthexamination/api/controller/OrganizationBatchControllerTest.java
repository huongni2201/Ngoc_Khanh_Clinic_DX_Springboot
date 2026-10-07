package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.response.BatchSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetHealthExaminationBatchByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrganizationBatchControllerTest {
  private final CreateHealthExaminationBatchUseCase create =
      mock(CreateHealthExaminationBatchUseCase.class);
  private final GetHealthExaminationBatchByIdUseCase get =
      mock(GetHealthExaminationBatchByIdUseCase.class);
  private final UpdateHealthExaminationBatchUseCase update =
      mock(UpdateHealthExaminationBatchUseCase.class);
  private final ListHealthExaminationBatchUseCase list =
      mock(ListHealthExaminationBatchUseCase.class);
  private final DeleteHealthExaminationBatchUseCase delete =
      mock(DeleteHealthExaminationBatchUseCase.class);
  private final UUID actor = UUID.randomUUID();
  private final UUID organizationId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();
  private final UUID service = UUID.randomUUID();
  private MockMvc mvc;

  @BeforeEach
  void authenticatedStaffAndStandaloneMvc() {
    var principal =
        UserPrincipal.builder()
            .userId(actor)
            .staffId(UUID.randomUUID())
            .patientId(null)
            .username("staff")
            .principalType("STAFF")
            .roleAssignments(List.of())
            .idleExpiresAt(Instant.now())
            .absoluteExpiresAt(Instant.now().plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "test", List.of()));
    mvc =
        MockMvcBuilders.standaloneSetup(
                new OrganizationBatchController(create, get, update, list, delete))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private String collection() {
    return "/api/v1/organizations/" + organizationId + "/health-examination-batches";
  }

  private String item() {
    return collection() + "/" + batchId;
  }

  private String body(String extra) {
    return """
        {"batchCode":"B1","batchName":"Campaign","examinationSiteType":"CLINIC",
         "examinationSiteName":"Clinic","examinationSiteAddress":"Address",
         "examinationDates":["2026-10-04","2026-10-05"],
         "services":[{"serviceId":"%s","negotiatedPrice":12.34}]%s}
        """
        .formatted(service, extra);
  }

  private BatchDetailResponse detail(long rowVersion) {
    return BatchDetailResponse.builder()
        .id(batchId)
        .organizationId(organizationId)
        .batchCode("B1")
        .batchName("Campaign")
        .days(List.of())
        .startDate(LocalDate.of(2026, 10, 4))
        .endDate(LocalDate.of(2026, 10, 5))
        .examinationSiteType("CLINIC")
        .status("DRAFT")
        .rowVersion(rowVersion)
        .services(List.of())
        .build();
  }

  @Test
  void createReturns201AndPassesPrincipalUserIdAsActor() throws Exception {
    when(create.execute(eq(organizationId), any(), eq(actor))).thenReturn(detail(0));

    mvc.perform(post(collection()).contentType(MediaType.APPLICATION_JSON).content(body("")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.id").value(batchId.toString()))
        .andExpect(jsonPath("$.data.rowVersion").value(0));

    var command = ArgumentCaptor.forClass(CreateHealthExaminationBatchCommand.class);
    verify(create).execute(eq(organizationId), command.capture(), eq(actor));
    var configuration = command.getValue().configuration();
    assertThat(configuration.batchCode()).isEqualTo("B1");
    assertThat(configuration.examinationDates())
        .containsExactly(LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 5));
    assertThat(configuration.examinationSiteType()).isEqualTo("CLINIC");
    assertThat(configuration.services().getFirst().serviceId()).isEqualTo(service);
    assertThat(configuration.services().getFirst().negotiatedPrice())
        .isEqualByComparingTo(new BigDecimal("12.34"));
  }

  @Test
  void createRejectsInvalidTransportBeforeCallingTheUseCase() throws Exception {
    for (String invalid :
        List.of(
            "{}",
            "{",
            body("").replace("12.34", "-1"),
            body("").replace("12.34", "1.234"),
            body("").replace("[\"2026-10-04\",\"2026-10-05\"]", "[]"),
            body("").replace("CLINIC", "COMPANY"),
            body("").replace("\"batchName\":\"Campaign\",", ""),
            body("").replace("\"serviceId\":\"" + service + "\",", ""))) {
      mvc.perform(post(collection()).contentType(MediaType.APPLICATION_JSON).content(invalid))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.result").value("NG"));
    }
    mvc.perform(
            post("/api/v1/organizations/not-a-uuid/health-examination-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(create);
  }

  @Test
  void getReturnsTheBatchEnvelope() throws Exception {
    when(get.execute(organizationId, batchId)).thenReturn(detail(3));

    mvc.perform(get(item()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.id").value(batchId.toString()))
        .andExpect(jsonPath("$.data.rowVersion").value(3));
  }

  @Test
  void getMapsMissingBatchToNotFoundAndMalformedIdToBadRequest() throws Exception {
    when(get.execute(organizationId, batchId))
        .thenThrow(new ResourceNotFoundException("Health examination batch"));

    mvc.perform(get(item()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.result").value("NG"));
    mvc.perform(get(collection() + "/not-a-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  void listBindsPagingSearchAndSortForTheOrganizationInThePath() throws Exception {
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

    mvc.perform(
            get(collection())
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
  void listRejectsInvalidPagingAndSortKeysBeforeCallingTheUseCase() throws Exception {
    mvc.perform(get(collection()).param("page", "0")).andExpect(status().isBadRequest());
    mvc.perform(get(collection()).param("sortKey", "id; drop table services"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(collection()).param("sortKey", "organizationId"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(list);
  }

  @Test
  void updateSendsTheFullConfigurationRowVersionAndActor() throws Exception {
    when(update.execute(eq(organizationId), eq(batchId), any(), eq(actor))).thenReturn(detail(4));

    mvc.perform(
            put(item()).contentType(MediaType.APPLICATION_JSON).content(body(",\"rowVersion\":3")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.rowVersion").value(4));

    var command = ArgumentCaptor.forClass(UpdateHealthExaminationBatchCommand.class);
    verify(update).execute(eq(organizationId), eq(batchId), command.capture(), eq(actor));
    assertThat(command.getValue().rowVersion()).isEqualTo(3L);
    assertThat(command.getValue().configuration().batchName()).isEqualTo("Campaign");
  }

  @Test
  void updateRequiresARowVersionAndAValidConfiguration() throws Exception {
    for (String invalid :
        List.of(body(""), body(",\"rowVersion\":-1"), "{\"rowVersion\":1}", "{")) {
      mvc.perform(put(item()).contentType(MediaType.APPLICATION_JSON).content(invalid))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.result").value("NG"));
    }
    verifyNoInteractions(update);
  }

  @Test
  void updateConflictIs409() throws Exception {
    when(update.execute(any(), any(), any(), any())).thenThrow(new ConcurrentUpdateException());

    mvc.perform(
            put(item()).contentType(MediaType.APPLICATION_JSON).content(body(",\"rowVersion\":1")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.result").value("NG"));
  }

  @Test
  void deleteReturns204WithoutBodyAndPassesVersionAndActor() throws Exception {
    mvc.perform(delete(item()).param("rowVersion", "3"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    var command = ArgumentCaptor.forClass(DeleteHealthExaminationBatchCommand.class);
    verify(delete).execute(eq(organizationId), eq(batchId), command.capture(), eq(actor));
    assertThat(command.getValue().rowVersion()).isEqualTo(3L);
  }

  @Test
  void deleteRequiresAValidRowVersion() throws Exception {
    mvc.perform(delete(item())).andExpect(status().isBadRequest());
    mvc.perform(delete(item()).param("rowVersion", "-1")).andExpect(status().isBadRequest());
    mvc.perform(delete(item()).param("rowVersion", "abc")).andExpect(status().isBadRequest());
    verifyNoInteractions(delete);
  }

  @Test
  void deleteMapsNotFoundAndConflict() throws Exception {
    doThrow(new ResourceNotFoundException("Health examination batch"))
        .doThrow(new ConcurrentUpdateException())
        .when(delete)
        .execute(any(), any(), any(), any());

    mvc.perform(delete(item()).param("rowVersion", "1")).andExpect(status().isNotFound());
    mvc.perform(delete(item()).param("rowVersion", "1")).andExpect(status().isConflict());
  }
}
