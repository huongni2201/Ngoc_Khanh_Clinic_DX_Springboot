package com.ngockhanh.clinic.accesscontrol.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.ngockhanh.clinic.accesscontrol.api.controller.RoleController;
import com.ngockhanh.clinic.accesscontrol.api.controller.StaffMemberController;
import com.ngockhanh.clinic.accesscontrol.application.command.CreateStaffMemberCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.query.StaffMemberSummary;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateStaffMemberUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListAssignableRolesUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListStaffMembersUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Exercises the staff member and role controllers through the production cookie, Origin and
 * permission filters, with the endpoints bound exactly as {@code V002} stores them.
 */
@WebMvcTest(
    controllers = {StaffMemberController.class, RoleController.class},
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  StaffMemberSecurityWebMvcTest.StoredEndpointPermissions.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class StaffMemberSecurityWebMvcTest {
  private static final String VIEW = "STAFF_MEMBER_VIEW";
  private static final String CREATE = "USER_ACCOUNT_CREATE";
  private static final String ROLE_VIEW = "ROLE_VIEW";
  private static final String ORIGIN = "https://app.test";
  private static final String COOKIE = "__Host-NKC_SESSION";
  private static final String VALID_BODY =
      """
      {"fullName":"Nguyễn Văn An","initialPassword":"Tam@12345","roleCode":"GENERAL_PRACTITIONER"}
      """;

  /** The three endpoints and their permissions as stored in {@code public.permissions}. */
  @TestConfiguration
  static class StoredEndpointPermissions {
    @Bean
    UserLoginMyBatisMapper endpointPermissionMapper() {
      var mapper = mock(UserLoginMyBatisMapper.class);
      when(mapper.findEndpointPermissions())
          .thenReturn(
              List.of(
                  permission(VIEW, "GET", "/api/v1/staff-members"),
                  permission(CREATE, "POST", "/api/v1/staff-members"),
                  permission(ROLE_VIEW, "GET", "/api/v1/roles")));
      return mapper;
    }

    private static PermissionRecord permission(String code, String method, String endpoint) {
      return PermissionRecord.builder().code(code).httpMethod(method).endpoint(endpoint).build();
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean ListStaffMembersUseCase list;
  @MockitoBean CreateStaffMemberUseCase create;
  @MockitoBean ListAssignableRolesUseCase roles;

  @Test
  void eachRouteRequiresItsOwnPermissionOnAStaffAccount() throws Exception {
    when(list.execute(any(), any()))
        .thenReturn(PageResponse.<StaffMemberSummary>builder().items(List.of()).page(1).size(10).build());
    when(roles.execute(any())).thenReturn(List.of());

    signedIn("viewer", VIEW);
    assertThat(perform(get("/api/v1/staff-members"), "viewer").getStatus()).isEqualTo(200);
    assertThat(perform(get("/api/v1/roles"), "viewer").getStatus()).isEqualTo(403);
    assertThat(perform(postJson(VALID_BODY), "viewer").getStatus()).isEqualTo(403);

    signedIn("roles", ROLE_VIEW);
    assertThat(perform(get("/api/v1/roles"), "roles").getStatus()).isEqualTo(200);
    assertThat(perform(get("/api/v1/staff-members"), "roles").getStatus()).isEqualTo(403);

    verify(create, never()).execute(any(), any());
  }

  @Test
  void acceptsVietnameseNamesWrittenPrecomposedOrWithCombiningMarks() throws Exception {
    UserPrincipal admin = signedIn("admin", CREATE);
    when(create.execute(any(), eq(admin)))
        .thenReturn(StaffMemberSummary.builder().id(UUID.randomUUID()).staffCode("NV-0001").build());

    for (String name :
        List.of(
            "Nguyễn Văn An",
            "ĐẶNG THỊ ĐÀO",
            "Lê-Văn O'Neil",
            "Trần T. Bình",
            "Nguyễn Văn An")) {
      var accepted =
          perform(
              postJson(
                  "{\"fullName\":\"%s\",\"initialPassword\":\"Tam@12345\",\"roleCode\":\"X\"}"
                      .formatted(name)),
              "admin");
      assertThat(accepted.getStatus()).as(name).isEqualTo(201);
    }
  }

  @Test
  void anUnavailableRoleIs422AndNotAConflict() throws Exception {
    UserPrincipal admin = signedIn("admin", CREATE);
    when(create.execute(any(), eq(admin)))
        .thenThrow(
            new com.ngockhanh.clinic.shared.exception.UnprocessableEntityException(
                "The role is not available"));

    var response = perform(postJson(VALID_BODY), "admin");

    assertThat(response.getStatus()).isEqualTo(422);
    assertThat(response.getContentAsString()).contains("\"code\":422").doesNotContain("errorCode");
  }

  @Test
  void createsWithTheBodyMappedToTheCommandAndReturns201() throws Exception {
    UserPrincipal admin = signedIn("admin", CREATE);
    when(create.execute(any(), eq(admin)))
        .thenReturn(
            StaffMemberSummary.builder()
                .id(UUID.randomUUID())
                .staffCode("NV-0001")
                .fullName("Nguyễn Văn An")
                .status("ACTIVE")
                .build());

    var response = perform(postJson(VALID_BODY), "admin");

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
        .contains("\"staffCode\":\"NV-0001\"", "Staff member created")
        .doesNotContain("Tam@12345");
    var command = ArgumentCaptor.forClass(CreateStaffMemberCommand.class);
    verify(create).execute(command.capture(), eq(admin));
    assertThat(command.getValue().roleCode()).isEqualTo("GENERAL_PRACTITIONER");
    assertThat(command.getValue().initialPassword()).isEqualTo("Tam@12345");
  }

  @Test
  void rejectsInvalidBodiesWithTheFieldList() throws Exception {
    signedIn("admin", CREATE);

    for (String password : List.of("ệ".repeat(25), "Mậtkhẩu123", "Tam 12345", "a".repeat(73))) {
      var rejected =
          perform(
              postJson(
                  """
                  {"fullName":"An","initialPassword":"%s","roleCode":"RECEPTIONIST"}
                  """
                      .formatted(password)),
              "admin");
      assertThat(rejected.getStatus()).as(password).isEqualTo(400);
      assertThat(rejected.getContentAsString()).contains("\"field\":\"initialPassword\"");
    }

    for (String name :
        List.of("王小明", "Øyvind", "Straße", "Æsa", "Łukasz", "An 2", "An_B", " . - ")) {
      var rejected =
          perform(
              postJson(
                  "{\"fullName\":\"%s\",\"initialPassword\":\"Tam@12345\",\"roleCode\":\"X\"}"
                      .formatted(name)),
              "admin");
      assertThat(rejected.getStatus()).as(name).isEqualTo(400);
      assertThat(rejected.getContentAsString()).as(name).contains("\"field\":\"fullName\"");
    }

    var noRoles =
        perform(postJson("{\"fullName\":\"An\",\"initialPassword\":\"Tam@12345\",\"roleCode\":\" \"}"), "admin");
    assertThat(noRoles.getStatus()).isEqualTo(400);
    assertThat(noRoles.getContentAsString()).contains("\"field\":\"roleCode\"");

    var noLetters =
        perform(
            postJson("{\"fullName\":\"123\",\"initialPassword\":\"Tam@12345\",\"roleCode\":\"X\"}"),
            "admin");
    assertThat(noLetters.getStatus()).isEqualTo(400);
    verifyNoInteractions(create);
  }

  @Test
  void rejectsAnonymousCallersPatientsAndForeignOrigins() throws Exception {
    assertThat(perform(get("/api/v1/staff-members"), null).getStatus()).isEqualTo(401);

    when(authenticate.execute("patient"))
        .thenReturn(
            UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .patientId(UUID.randomUUID())
                .principalType("PATIENT")
                .roleAssignments(ParticipantFixtures.staff(VIEW, CREATE).roleAssignments())
                .build());
    assertThat(perform(get("/api/v1/staff-members"), "patient").getStatus()).isEqualTo(403);

    signedIn("admin", CREATE);
    var foreign =
        mvc.perform(
                post("/api/v1/staff-members")
                    .header("Origin", "https://evil.test")
                    .cookie(new Cookie(COOKIE, "admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
            .andReturn()
            .getResponse();
    assertThat(foreign.getStatus()).isEqualTo(403);
    verifyNoInteractions(list, create);
  }

  private UserPrincipal signedIn(String session, String... permissions) {
    UserPrincipal principal = ParticipantFixtures.staff(permissions);
    when(authenticate.execute(session)).thenReturn(principal);
    return principal;
  }

  private static MockHttpServletRequestBuilder postJson(String body) {
    return post("/api/v1/staff-members").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String session)
      throws Exception {
    request.header("Origin", ORIGIN);
    if (session != null) request.cookie(new Cookie(COOKIE, session));
    return mvc.perform(request).andReturn().getResponse();
  }
}
