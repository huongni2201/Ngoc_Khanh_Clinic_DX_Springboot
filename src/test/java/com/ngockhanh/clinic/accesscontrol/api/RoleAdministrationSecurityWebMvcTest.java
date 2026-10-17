package com.ngockhanh.clinic.accesscontrol.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.ngockhanh.clinic.accesscontrol.api.controller.PermissionCatalogController;
import com.ngockhanh.clinic.accesscontrol.api.controller.RoleAdministrationController;
import com.ngockhanh.clinic.accesscontrol.application.command.CreateRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.command.UpdateRolePermissionsCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.ListRolesQuery;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.RolePermissionsResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateRoleUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.GetRolePermissionsUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListPermissionCatalogUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListRolesUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.UpdateRolePermissionsUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.UpdateRoleUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.exception.ProtectedRoleException;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.FieldConflictException;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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
 * Exercises the role administration controllers through the production cookie, Origin and
 * permission filters, with the endpoints bound exactly as {@code V002} stores them.
 */
@WebMvcTest(
    controllers = {RoleAdministrationController.class, PermissionCatalogController.class},
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  RoleAdministrationSecurityWebMvcTest.StoredEndpointPermissions.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class RoleAdministrationSecurityWebMvcTest {
  private static final String ORIGIN = "https://app.test";
  private static final String COOKIE = "__Host-NKC_SESSION";
  private static final UUID ROLE_ID = UUID.fromString("0199f000-0000-7000-8000-000000000001");
  private static final String ROLE = "/api/v1/admin/roles/" + ROLE_ID;
  private static final String CREATE_BODY =
      """
      {"code":"LAB_TECH","name":"Lab technician","description":"Runs lab tests","active":true}
      """;
  private static final String UPDATE_BODY =
      """
      {"name":"Lab technician","active":false,"rowVersion":2}
      """;
  private static final String PERMISSIONS_BODY =
      """
      {"rowVersion":2,"grant":["PAYMENT_VIEW"],"revoke":["PAYMENT_REFUND"]}
      """;

  /** The six endpoints and their permissions as stored in {@code public.permissions}. */
  @TestConfiguration
  static class StoredEndpointPermissions {
    @Bean
    UserLoginMyBatisMapper endpointPermissionMapper() {
      var mapper = mock(UserLoginMyBatisMapper.class);
      when(mapper.findEndpointPermissions())
          .thenReturn(
              List.of(
                  permission("ROLE_MANAGE", "GET", "/api/v1/admin/roles"),
                  permission("ROLE_CREATE", "POST", "/api/v1/admin/roles"),
                  permission("ROLE_UPDATE", "PUT", "/api/v1/admin/roles/{roleId}"),
                  permission("PERMISSION_VIEW", "GET", "/api/v1/admin/permissions"),
                  permission(
                      "ROLE_PERMISSION_VIEW", "GET", "/api/v1/admin/roles/{roleId}/permissions"),
                  permission(
                      "ROLE_PERMISSION_ASSIGN",
                      "PUT",
                      "/api/v1/admin/roles/{roleId}/permissions")));
      return mapper;
    }

    private static PermissionRecord permission(String code, String method, String endpoint) {
      return PermissionRecord.builder().code(code).httpMethod(method).endpoint(endpoint).build();
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean ListRolesUseCase list;
  @MockitoBean CreateRoleUseCase create;
  @MockitoBean UpdateRoleUseCase update;
  @MockitoBean GetRolePermissionsUseCase getPermissions;
  @MockitoBean UpdateRolePermissionsUseCase updatePermissions;
  @MockitoBean ListPermissionCatalogUseCase catalog;

  @Test
  void eachRouteRequiresItsOwnPermissionOnAStaffAccount() throws Exception {
    // Request builders are mutable, so every call builds a fresh one.
    Map<String, Supplier<MockHttpServletRequestBuilder>> routes =
        Map.of(
            "ROLE_MANAGE", () -> get("/api/v1/admin/roles"),
            "ROLE_CREATE", () -> json(post("/api/v1/admin/roles"), CREATE_BODY),
            "ROLE_UPDATE", () -> json(put(ROLE), UPDATE_BODY),
            "PERMISSION_VIEW", () -> get("/api/v1/admin/permissions"),
            "ROLE_PERMISSION_VIEW", () -> get(ROLE + "/permissions"),
            "ROLE_PERMISSION_ASSIGN", () -> json(put(ROLE + "/permissions"), PERMISSIONS_BODY));
    for (String permission : routes.keySet()) {
      signedIn(permission, permission);
      for (var route : routes.entrySet()) {
        var status = perform(route.getValue().get(), permission).getStatus();
        if (route.getKey().equals(permission))
          assertThat(status).as(permission).isBetween(200, 201);
        else assertThat(status).as(permission + " calling " + route.getKey()).isEqualTo(403);
      }
    }
  }

  @Test
  void rejectsAnonymousCallersAndForeignOrigins() throws Exception {
    assertThat(perform(get("/api/v1/admin/roles"), null).getStatus()).isEqualTo(401);

    signedIn("admin", "ROLE_CREATE");
    var foreign =
        mvc.perform(
                json(post("/api/v1/admin/roles"), CREATE_BODY)
                    .header("Origin", "https://evil.test")
                    .cookie(new Cookie(COOKIE, "admin")))
            .andReturn()
            .getResponse();
    assertThat(foreign.getStatus()).isEqualTo(403);
    verifyNoInteractions(create);
  }

  @Test
  void mapsTheListParametersAndTheCreateBody() throws Exception {
    UserPrincipal admin = signedIn("admin", "ROLE_MANAGE", "ROLE_CREATE");
    when(list.execute(any(), eq(admin)))
        .thenReturn(PageResponse.<RoleSummary>builder().items(List.of()).page(2).size(5).build());
    when(create.execute(any(), eq(admin)))
        .thenReturn(RoleSummary.builder().id(ROLE_ID).code("LAB_TECH").build());

    var listed = perform(get("/api/v1/admin/roles?page=2&size=5"), "admin");
    var created = perform(json(post("/api/v1/admin/roles"), CREATE_BODY), "admin");

    assertThat(listed.getStatus()).isEqualTo(200);
    var query = ArgumentCaptor.forClass(ListRolesQuery.class);
    verify(list).execute(query.capture(), eq(admin));
    assertThat(query.getValue()).isEqualTo(ListRolesQuery.builder().page(2).size(5).build());
    assertThat(created.getStatus()).isEqualTo(201);
    assertThat(created.getContentAsString()).contains("\"code\":\"LAB_TECH\"", "Role created");
    var command = ArgumentCaptor.forClass(CreateRoleCommand.class);
    verify(create).execute(command.capture(), eq(admin));
    assertThat(command.getValue().code()).isEqualTo("LAB_TECH");
    assertThat(command.getValue().active()).isTrue();
  }

  @Test
  void checksTheLengthOfTheTrimmedName() throws Exception {
    UserPrincipal admin = signedIn("admin", "ROLE_CREATE");
    when(create.execute(any(), eq(admin)))
        .thenReturn(RoleSummary.builder().id(ROLE_ID).code("N").build());
    String name = "n".repeat(100);

    var created =
        perform(
            json(
                post("/api/v1/admin/roles"),
                "{\"code\":\"N\",\"name\":\"  %s  \",\"description\":\" d \"}".formatted(name)),
            "admin");

    assertThat(created.getStatus()).isEqualTo(201);
    var command = ArgumentCaptor.forClass(CreateRoleCommand.class);
    verify(create).execute(command.capture(), eq(admin));
    assertThat(command.getValue().name()).isEqualTo(name);
    assertThat(command.getValue().description()).isEqualTo("d");
  }

  @Test
  void rejectsInvalidBodiesAndParametersWithTheFieldList() throws Exception {
    signedIn("admin", "ROLE_MANAGE", "ROLE_CREATE", "ROLE_PERMISSION_ASSIGN");

    for (String code : List.of("nurse", "1NURSE", "NURSE-2", " "))
      assertThat(
              perform(
                      json(
                          post("/api/v1/admin/roles"),
                          "{\"code\":\"%s\",\"name\":\"Nurse\"}".formatted(code)),
                      "admin")
                  .getContentAsString())
          .as(code)
          .contains("\"code\":400", "\"field\":\"code\"");
    assertThat(
            perform(
                    json(
                        post("/api/v1/admin/roles"),
                        "{\"code\":\"N\",\"name\":\"%s\"}".formatted("n".repeat(101))),
                    "admin")
                .getContentAsString())
        .contains("\"field\":\"name\"");
    assertThat(perform(get("/api/v1/admin/roles?size=101"), "admin").getStatus()).isEqualTo(400);
    assertThat(
            perform(json(put(ROLE + "/permissions"), "{\"grant\":[\"PAYMENT_VIEW\"]}"), "admin")
                .getContentAsString())
        .contains("\"field\":\"rowVersion\"");
    verifyNoInteractions(list, create, updatePermissions);
  }

  @Test
  void reportsDuplicatesStaleVersionsAndProtectedRolesAs409() throws Exception {
    UserPrincipal admin = signedIn("admin", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_PERMISSION_ASSIGN");
    when(create.execute(any(), eq(admin)))
        .thenThrow(new FieldConflictException("code", "Role code already exists"));
    when(update.execute(eq(ROLE_ID), any(), eq(admin))).thenThrow(new ConcurrentUpdateException());
    when(updatePermissions.execute(eq(ROLE_ID), any(UpdateRolePermissionsCommand.class), eq(admin)))
        .thenThrow(new ProtectedRoleException("The Administrator role must keep it"));

    var duplicate = perform(json(post("/api/v1/admin/roles"), CREATE_BODY), "admin");
    var stale = perform(json(put(ROLE), UPDATE_BODY), "admin");
    var protectedRole = perform(json(put(ROLE + "/permissions"), PERMISSIONS_BODY), "admin");

    assertThat(duplicate.getStatus()).isEqualTo(409);
    assertThat(duplicate.getContentAsString())
        .contains("\"field\":\"code\"", "\"code\":\"Duplicate\"");
    assertThat(stale.getStatus()).isEqualTo(409);
    assertThat(stale.getContentAsString()).doesNotContain("\"field\"");
    assertThat(protectedRole.getStatus()).isEqualTo(409);
  }

  @Test
  void returnsTheGrantedPermissionsOfARole() throws Exception {
    UserPrincipal admin = signedIn("admin", "ROLE_PERMISSION_VIEW");
    when(getPermissions.execute(ROLE_ID, admin))
        .thenReturn(
            RolePermissionsResponse.builder()
                .roleId(ROLE_ID)
                .roleCode("NURSE")
                .active(true)
                .rowVersion(3L)
                .permissionCodes(List.of("PATIENT_VIEW"))
                .build());

    var response = perform(get(ROLE + "/permissions"), "admin");

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentAsString())
        .contains("\"rowVersion\":3", "\"permissionCodes\":[\"PATIENT_VIEW\"]");
  }

  private UserPrincipal signedIn(String session, String... permissions) {
    UserPrincipal principal = ParticipantFixtures.staff(permissions);
    when(authenticate.execute(session)).thenReturn(principal);
    return principal;
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String session)
      throws Exception {
    request.header("Origin", ORIGIN);
    if (session != null) request.cookie(new Cookie(COOKIE, session));
    return mvc.perform(request).andReturn().getResponse();
  }
}
