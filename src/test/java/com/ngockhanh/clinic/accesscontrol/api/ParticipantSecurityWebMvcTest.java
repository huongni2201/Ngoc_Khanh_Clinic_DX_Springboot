package com.ngockhanh.clinic.accesscontrol.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.healthexamination.api.controller.BatchParticipantController;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportTemplateResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.*;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Exercises the real roster controller through the production cookie, Origin and permission
 * filters.
 */
@WebMvcTest(
    controllers = BatchParticipantController.class,
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  ParticipantSecurityWebMvcTest.StoredEndpointPermissions.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class ParticipantSecurityWebMvcTest {
  private static final String BASE =
      "/api/v1/organizations/"
          + UUID.randomUUID()
          + "/health-examination-batches/"
          + UUID.randomUUID()
          + "/participants";
  private static final String ITEM = BASE + "/" + UUID.randomUUID();
  private static final String BODY =
      """
      {"fullName":"Synthetic Person","dateOfBirth":"1990-05-12","sex":"MALE",
       "identificationNumber":"012345678901","departmentName":"Accounting",
       "positionName":"Staff","batchDayId":"01990000-0000-7000-8000-000000000003","rowVersion":0}
      """;
  private static final String ORIGIN = "https://app.test";
  private static final String COOKIE = "__Host-NKC_SESSION";

  /** The roster endpoints and their permissions as stored in {@code public.permissions}. */
  @TestConfiguration
  static class StoredEndpointPermissions {
    private static final String ROSTER =
        "/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}/participants";

    @Bean
    UserLoginMyBatisMapper endpointPermissions() {
      var mapper = mock(UserLoginMyBatisMapper.class);
      when(mapper.findEndpointPermissions())
          .thenReturn(
              List.of(
                  endpoint("GET", "", "PARTICIPANT_VIEW"),
                  endpoint("GET", "/{participantId}", "PARTICIPANT_DETAIL_VIEW"),
                  endpoint("GET", "/import-template", "PARTICIPANT_TEMPLATE_DOWNLOAD"),
                  endpoint("POST", "/imports", "PARTICIPANT_IMPORT"),
                  endpoint("POST", "", "PARTICIPANT_CREATE"),
                  endpoint("PUT", "/{participantId}", "PARTICIPANT_UPDATE"),
                  endpoint("DELETE", "/{participantId}", "PARTICIPANT_REMOVE"),
                  endpoint("POST", "/{participantId}/reactivate", "PARTICIPANT_REACTIVATE")));
      return mapper;
    }

    private static PermissionRecord endpoint(String method, String path, String code) {
      return PermissionRecord.builder().code(code).httpMethod(method).endpoint(ROSTER + path).build();
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean ListParticipantsUseCase list;
  @MockitoBean GetParticipantImportTemplateUseCase template;
  @MockitoBean ImportParticipantsUseCase importer;
  @MockitoBean CreateParticipantUseCase create;
  @MockitoBean GetParticipantDetailUseCase detail;
  @MockitoBean UpdateParticipantUseCase update;
  @MockitoBean CancelParticipantUseCase cancel;
  @MockitoBean ReactivateParticipantUseCase reactivate;

  private record Route(
      String name,
      String permission,
      int successStatus,
      Supplier<AbstractMockHttpServletRequestBuilder<?>> request) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Route> routes() {
    return Stream.of(
        new Route("list", "PARTICIPANT_VIEW", 200, () -> get(BASE)),
        new Route("detail", "PARTICIPANT_DETAIL_VIEW", 200, () -> get(ITEM)),
        new Route(
            "template", "PARTICIPANT_TEMPLATE_DOWNLOAD", 200, () -> get(BASE + "/import-template")),
        new Route(
            "import",
            "PARTICIPANT_IMPORT",
            201,
            () ->
                multipart(BASE + "/imports")
                    .file(
                        new MockMultipartFile(
                            "file",
                            "participants.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            new byte[] {1}))
                    .param("rowVersion", "0")
                    .header("Idempotency-Key", UUID.randomUUID().toString())),
        new Route(
            "create",
            "PARTICIPANT_CREATE",
            201,
            () -> post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY)),
        new Route(
            "update",
            "PARTICIPANT_UPDATE",
            200,
            () -> put(ITEM).contentType(MediaType.APPLICATION_JSON).content(BODY)),
        new Route("cancel", "PARTICIPANT_REMOVE", 204, () -> delete(ITEM).param("rowVersion", "0")),
        new Route(
            "reactivate",
            "PARTICIPANT_REACTIVATE",
            200,
            () ->
                post(ITEM + "/reactivate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rowVersion\":0}")));
  }

  @BeforeEach
  void stubResponsesAtTheApplicationBoundary() {
    when(list.execute(any(), any(), any(), any()))
        .thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0));
    when(template.execute(any(), any(), any()))
        .thenReturn(new ParticipantImportTemplateResponse(new byte[] {1}, "template.xlsx"));
  }

  static Stream<Route> writes() {
    return routes()
        .filter(
            route ->
                route.successStatus() != 200
                    || route.name().equals("update")
                    || route.name().equals("reactivate"));
  }

  @ParameterizedTest
  @MethodSource("routes")
  void acceptsOnlyTheRoutesPermissionOnAStaffAccount(Route route) throws Exception {
    for (String permission : routes().map(Route::permission).distinct().toList()) {
      when(authenticate.execute("session")).thenReturn(ParticipantFixtures.staff(permission));
      assertThat(status(route, "session", ORIGIN))
          .as(permission)
          .isEqualTo(permission.equals(route.permission()) ? route.successStatus() : 403);
    }
  }

  @ParameterizedTest
  @MethodSource("routes")
  void rejectsAnonymousPatientsAndLegacyManageGrants(Route route) throws Exception {
    assertThat(status(route, null, ORIGIN)).isEqualTo(401);
    var granted = ParticipantFixtures.staff(route.permission());
    when(authenticate.execute("patient"))
        .thenReturn(
            UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .patientId(UUID.randomUUID())
                .principalType("PATIENT")
                .roleAssignments(granted.roleAssignments())
                .build());
    assertThat(status(route, "patient", ORIGIN)).isEqualTo(403);
    when(authenticate.execute("legacy"))
        .thenReturn(ParticipantFixtures.staff("HEALTH_EXAMINATION_PARTICIPANT_MANAGE"));
    assertThat(status(route, "legacy", ORIGIN)).isEqualTo(403);
    verifyNoInteractions(list, template, importer, create, detail, update, cancel, reactivate);
  }

  @ParameterizedTest
  @MethodSource("writes")
  void rejectsWritesWithAnUnknownOrMissingOrigin(Route route) throws Exception {
    when(authenticate.execute("session")).thenReturn(ParticipantFixtures.staff(route.permission()));
    assertThat(status(route, "session", "https://evil.test")).isEqualTo(403);
    assertThat(status(route, "session", null)).isEqualTo(403);
    verifyNoInteractions(list, template, importer, create, detail, update, cancel, reactivate);
  }

  private int status(Route route, String session, String origin) throws Exception {
    var request = route.request().get();
    if (origin != null) request.header("Origin", origin);
    if (session != null) request.cookie(new Cookie(COOKIE, session));
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }
}
