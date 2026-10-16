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
import com.ngockhanh.clinic.healthexamination.application.command.ActivateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.EndHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationAgreementCommand;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationAgreementListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.response.AgreementSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ActivateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.EndHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetHealthExaminationAgreementByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListHealthExaminationAgreementUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateHealthExaminationAgreementUseCase;
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

class OrganizationAgreementControllerTest {
  private final CreateHealthExaminationAgreementUseCase create =
      mock(CreateHealthExaminationAgreementUseCase.class);
  private final GetHealthExaminationAgreementByIdUseCase get =
      mock(GetHealthExaminationAgreementByIdUseCase.class);
  private final UpdateHealthExaminationAgreementUseCase update =
      mock(UpdateHealthExaminationAgreementUseCase.class);
  private final ListHealthExaminationAgreementUseCase list =
      mock(ListHealthExaminationAgreementUseCase.class);
  private final DeleteHealthExaminationAgreementUseCase delete =
      mock(DeleteHealthExaminationAgreementUseCase.class);
  private final ActivateHealthExaminationAgreementUseCase start =
      mock(ActivateHealthExaminationAgreementUseCase.class);
  private final EndHealthExaminationAgreementUseCase end =
      mock(EndHealthExaminationAgreementUseCase.class);
  private final UUID actor = UUID.randomUUID();
  private final UUID organizationId = UUID.randomUUID();
  private final UUID agreementId = UUID.randomUUID();
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
                new OrganizationAgreementController(create, get, update, list, delete, start, end))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private String collection() {
    return "/api/v1/organizations/" + organizationId + "/agreements";
  }

  private String item() {
    return collection() + "/" + agreementId;
  }

  private String body(String extra) {
    return """
        {"agreementName":"Campaign","validFrom":"2026-10-04","validTo":"2026-10-05",
         "services":[{"serviceId":"%s","negotiatedPrice":12.34}]%s}
        """
        .formatted(service, extra);
  }

  private AgreementDetailResponse detail(long rowVersion) {
    return AgreementDetailResponse.builder()
        .id(agreementId)
        .organizationId(organizationId)
        .agreementCode("B1")
        .agreementName("Campaign")
        .validFrom(LocalDate.of(2026, 10, 4))
        .validTo(LocalDate.of(2026, 10, 5))
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
        .andExpect(jsonPath("$.data.id").value(agreementId.toString()))
        .andExpect(jsonPath("$.data.rowVersion").value(0));

    var command = ArgumentCaptor.forClass(CreateHealthExaminationAgreementCommand.class);
    verify(create).execute(eq(organizationId), command.capture(), eq(actor));
    var configuration = command.getValue().configuration();
    assertThat(configuration.agreementName()).isEqualTo("Campaign");
    assertThat(configuration.validFrom()).isEqualTo(LocalDate.of(2026, 10, 4));
    assertThat(configuration.validTo()).isEqualTo(LocalDate.of(2026, 10, 5));
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
            body("").replace("\"validFrom\":\"2026-10-04\"", "\"validFrom\":\"2026-12-31\""),
            body("").replace("\"validFrom\":\"2026-10-04\"", "\"validFrom\":\"04/10/2026\""),
            body("").replace("\"agreementName\":\"Campaign\",", ""),
            body("").replace("\"serviceId\":\"" + service + "\",", ""))) {
      mvc.perform(post(collection()).contentType(MediaType.APPLICATION_JSON).content(invalid))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.result").value("NG"));
    }
    mvc.perform(
            post("/api/v1/organizations/not-a-uuid/agreements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(create);
  }

  @Test
  void getReturnsTheAgreementEnvelope() throws Exception {
    when(get.execute(organizationId, agreementId)).thenReturn(detail(3));

    mvc.perform(get(item()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.id").value(agreementId.toString()))
        .andExpect(jsonPath("$.data.rowVersion").value(3));
  }

  @Test
  void getMapsMissingAgreementToNotFoundAndMalformedIdToBadRequest() throws Exception {
    when(get.execute(organizationId, agreementId))
        .thenThrow(new ResourceNotFoundException("Health examination agreement"));

    mvc.perform(get(item()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.result").value("NG"));
    mvc.perform(get(collection() + "/not-a-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  void listBindsPagingSearchAndSortForTheOrganizationInThePath() throws Exception {
    var page =
        PageResponse.<AgreementSummaryResponse>builder()
            .items(List.of())
            .page(2)
            .size(5)
            .totalElements(11)
            .totalPages(3)
            .build();
    when(list.execute(eq(organizationId), any(HealthExaminationAgreementListQuery.class)))
        .thenReturn(page);

    mvc.perform(
            get(collection())
                .param("page", "2")
                .param("size", "5")
                .param("searchKey", "Clinic")
                .param("sortKey", "validFrom")
                .param("sortBy", "DESC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(11));

    var query = ArgumentCaptor.forClass(HealthExaminationAgreementListQuery.class);
    verify(list).execute(eq(organizationId), query.capture());
    assertThat(query.getValue())
        .isEqualTo(
            HealthExaminationAgreementListQuery.builder()
                .page(2)
                .size(5)
                .searchKey("Clinic")
                .sortKey("validFrom")
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
    when(update.execute(eq(organizationId), eq(agreementId), any(), eq(actor)))
        .thenReturn(detail(4));

    mvc.perform(
            put(item()).contentType(MediaType.APPLICATION_JSON).content(body(",\"rowVersion\":3")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.rowVersion").value(4));

    var command = ArgumentCaptor.forClass(UpdateHealthExaminationAgreementCommand.class);
    verify(update).execute(eq(organizationId), eq(agreementId), command.capture(), eq(actor));
    assertThat(command.getValue().rowVersion()).isEqualTo(3L);
    assertThat(command.getValue().configuration().agreementName()).isEqualTo("Campaign");
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

    var command = ArgumentCaptor.forClass(DeleteHealthExaminationAgreementCommand.class);
    verify(delete).execute(eq(organizationId), eq(agreementId), command.capture(), eq(actor));
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
    doThrow(new ResourceNotFoundException("Health examination agreement"))
        .doThrow(new ConcurrentUpdateException())
        .when(delete)
        .execute(any(), any(), any(), any());

    mvc.perform(delete(item()).param("rowVersion", "1")).andExpect(status().isNotFound());
    mvc.perform(delete(item()).param("rowVersion", "1")).andExpect(status().isConflict());
  }

  @Test
  void activatePassesTheVersionAndReturnsTheActiveAgreement() throws Exception {
    when(start.execute(eq(organizationId), eq(agreementId), any(), eq(actor)))
        .thenReturn(
            AgreementDetailResponse.builder()
                .id(agreementId)
                .organizationId(organizationId)
                .agreementCode("B1")
                .agreementName("Campaign")
                .status("ACTIVE")
                .rowVersion(5)
                .services(List.of())
                .build());

    mvc.perform(
            post(item() + "/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":4}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.rowVersion").value(5));

    verify(start)
        .execute(
            organizationId,
            agreementId,
            ActivateHealthExaminationAgreementCommand.builder().rowVersion(4L).build(),
            actor);
  }

  @Test
  void activateWithoutAVersionIsRejectedAndAStaleOneIsAConflict() throws Exception {
    mvc.perform(post(item() + "/activate").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(start);

    when(start.execute(any(), any(), any(), any())).thenThrow(new ConcurrentUpdateException());
    mvc.perform(
            post(item() + "/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":1}"))
        .andExpect(status().isConflict());
  }

  @Test
  void endPassesTheVersionAndRejectsAMissingOne() throws Exception {
    when(end.execute(eq(organizationId), eq(agreementId), any(), eq(actor))).thenReturn(detail(6));

    mvc.perform(
            post(item() + "/end")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":5}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rowVersion").value(6));
    verify(end)
        .execute(
            organizationId,
            agreementId,
            EndHealthExaminationAgreementCommand.builder().rowVersion(5L).build(),
            actor);

    mvc.perform(post(item() + "/end").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }
}
