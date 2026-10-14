package com.ngockhanh.clinic.accesscontrol.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.document.api.controller.RenderJobController;
import com.ngockhanh.clinic.document.application.response.RenderJobPartResponse;
import com.ngockhanh.clinic.document.application.response.RenderJobResponse;
import com.ngockhanh.clinic.document.application.usecase.DownloadRenderJobPartUseCase;
import com.ngockhanh.clinic.document.application.usecase.GetRenderJobUseCase;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real print job controller through the production cookie, Origin and permission
 * filters, with the two permissions bound exactly as {@code V002} stores them. The two endpoints
 * share a prefix, so each permission must open only its own.
 */
@WebMvcTest(
    controllers = RenderJobController.class,
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  RenderJobSecurityWebMvcTest.StoredEndpointPermissions.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class RenderJobSecurityWebMvcTest {
  private static final String READ = "DOCUMENT_RENDER_JOB_READ";
  private static final String DOWNLOAD = "DOCUMENT_RENDER_JOB_DOWNLOAD";
  private static final String JOB = "/api/v1/document-render-jobs/" + UUID.randomUUID();
  private static final String PART = JOB + "/parts/1";
  private static final String ORIGIN = "https://app.test";
  private static final String COOKIE = "__Host-NKC_SESSION";

  /** The endpoints and permissions as stored in {@code public.permissions}. */
  @TestConfiguration
  static class StoredEndpointPermissions {
    @Bean
    UserLoginMyBatisMapper endpointPermissionMapper() {
      var mapper = mock(UserLoginMyBatisMapper.class);
      when(mapper.findEndpointPermissions())
          .thenReturn(
              List.of(
                  PermissionRecord.builder()
                      .code(READ)
                      .httpMethod("GET")
                      .endpoint("/api/v1/document-render-jobs/{jobId}")
                      .build(),
                  PermissionRecord.builder()
                      .code(DOWNLOAD)
                      .httpMethod("GET")
                      .endpoint("/api/v1/document-render-jobs/{jobId}/parts/{partNo}")
                      .build()));
      return mapper;
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean GetRenderJobUseCase getJob;
  @MockitoBean DownloadRenderJobPartUseCase downloadPart;

  @BeforeEach
  void stubTheApplicationBoundary() {
    when(getJob.execute(any(), any()))
        .thenReturn(
            new RenderJobResponse(
                UUID.randomUUID(),
                "T",
                "QUEUED",
                1,
                0,
                null,
                List.of(),
                List.of(),
                Instant.parse("2026-10-13T08:00:00Z"),
                null,
                null));
    when(downloadPart.execute(any(), anyInt(), any()))
        .thenReturn(new RenderJobPartResponse(new byte[] {1}, "documents-phan-1.pdf"));
  }

  @Test
  void eachPermissionOpensOnlyItsOwnEndpoint() throws Exception {
    when(authenticate.execute("reader")).thenReturn(ParticipantFixtures.staff(READ));
    when(authenticate.execute("downloader")).thenReturn(ParticipantFixtures.staff(DOWNLOAD));

    assertThat(status(JOB, "reader")).isEqualTo(200);
    assertThat(status(PART, "reader")).isEqualTo(403);
    assertThat(status(JOB, "downloader")).isEqualTo(403);
    assertThat(status(PART, "downloader")).isEqualTo(200);
  }

  @Test
  void otherPermissionsAndNoPermissionAreRefused() throws Exception {
    for (String other : List.of("PARTICIPANT_EXAMINATION_FORM_PRINT", "PARTICIPANT_EXAMINATION_FORM_BULK_PRINT", "PARTICIPANT_VIEW")) {
      when(authenticate.execute("other")).thenReturn(ParticipantFixtures.staff(other));
      assertThat(status(JOB, "other")).as(other).isEqualTo(403);
      assertThat(status(PART, "other")).as(other).isEqualTo(403);
    }
    when(authenticate.execute("none")).thenReturn(ParticipantFixtures.staff());
    assertThat(status(JOB, "none")).isEqualTo(403);
  }

  @Test
  void rejectsAnonymousCallersAndPatientsEvenWithThePermission() throws Exception {
    assertThat(status(JOB, null)).isEqualTo(401);
    assertThat(status(PART, null)).isEqualTo(401);

    var granted = ParticipantFixtures.staff(READ, DOWNLOAD);
    when(authenticate.execute("patient"))
        .thenReturn(
            UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .patientId(UUID.randomUUID())
                .principalType("PATIENT")
                .roleAssignments(granted.roleAssignments())
                .build());
    assertThat(status(JOB, "patient")).isEqualTo(403);
    assertThat(status(PART, "patient")).isEqualTo(403);
    verifyNoInteractions(getJob, downloadPart);
  }

  private int status(String url, String session) throws Exception {
    var request = get(url).header("Origin", ORIGIN);
    if (session != null) request.cookie(new Cookie(COOKIE, session));
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }
}
