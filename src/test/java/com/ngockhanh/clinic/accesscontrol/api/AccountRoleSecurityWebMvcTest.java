package com.ngockhanh.clinic.accesscontrol.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.ngockhanh.clinic.accesscontrol.api.controller.AccountRoleController;
import com.ngockhanh.clinic.accesscontrol.application.command.ChangeAccountRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountRoleResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ChangeAccountRoleUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.FieldConflictException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
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
 * Exercises the account role change through the production cookie, Origin and permission filters,
 * with the endpoint bound exactly as {@code V007} stores it.
 */
@WebMvcTest(
    controllers = AccountRoleController.class,
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  AccountRoleSecurityWebMvcTest.StoredEndpointPermissions.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class AccountRoleSecurityWebMvcTest {
  private static final String ORIGIN = "https://app.test";
  private static final String COOKIE = "__Host-NKC_SESSION";
  private static final UUID ACCOUNT_ID = UUID.fromString("0199f000-0000-7000-8000-000000000002");
  private static final String ROUTE = "/api/v1/accounts/" + ACCOUNT_ID + "/role";
  private static final String BODY =
      """
      {"roleCode":"NURSE","rowVersion":3}
      """;

  /** The endpoint and its permission as stored in {@code public.permissions}. */
  @TestConfiguration
  static class StoredEndpointPermissions {
    @Bean
    UserLoginMyBatisMapper endpointPermissionMapper() {
      var mapper = mock(UserLoginMyBatisMapper.class);
      when(mapper.findEndpointPermissions())
          .thenReturn(
              List.of(
                  PermissionRecord.builder()
                      .code("USER_ROLE_ASSIGN")
                      .httpMethod("PUT")
                      .endpoint("/api/v1/accounts/{accountId}/role")
                      .build()));
      return mapper;
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean ChangeAccountRoleUseCase changeRole;

  @Test
  void requiresTheAssignPermissionAndAnAllowedOrigin() throws Exception {
    assertThat(perform(json(put(ROUTE), BODY), null).getStatus()).isEqualTo(401);

    signedIn("creator", "USER_ACCOUNT_CREATE");
    assertThat(perform(json(put(ROUTE), BODY), "creator").getStatus()).isEqualTo(403);

    signedIn("admin", "USER_ROLE_ASSIGN");
    var foreign =
        mvc.perform(
                json(put(ROUTE), BODY)
                    .header("Origin", "https://evil.test")
                    .cookie(new Cookie(COOKIE, "admin")))
            .andReturn()
            .getResponse();
    assertThat(foreign.getStatus()).isEqualTo(403);
    verifyNoInteractions(changeRole);
  }

  @Test
  void mapsTheBodyAndReturnsTheNewVersion() throws Exception {
    UserPrincipal admin = signedIn("admin", "USER_ROLE_ASSIGN");
    when(changeRole.execute(eq(ACCOUNT_ID), any(), eq(admin)))
        .thenReturn(
            AccountRoleResponse.builder()
                .accountId(ACCOUNT_ID)
                .roleCode("NURSE")
                .rowVersion(4L)
                .build());

    var changed = perform(json(put(ROUTE), BODY), "admin");

    assertThat(changed.getStatus()).isEqualTo(200);
    assertThat(changed.getContentAsString())
        .contains("Account role changed", "\"roleCode\":\"NURSE\"", "\"rowVersion\":4");
    var command = ArgumentCaptor.forClass(ChangeAccountRoleCommand.class);
    verify(changeRole).execute(eq(ACCOUNT_ID), command.capture(), eq(admin));
    assertThat(command.getValue())
        .isEqualTo(ChangeAccountRoleCommand.builder().roleCode("NURSE").rowVersion(3L).build());
  }

  @Test
  void rejectsAnInvalidBodyWithTheFieldList() throws Exception {
    signedIn("admin", "USER_ROLE_ASSIGN");

    var invalid = perform(json(put(ROUTE), "{\"roleCode\":\" \"}"), "admin");

    assertThat(invalid.getStatus()).isEqualTo(400);
    assertThat(invalid.getContentAsString())
        .contains("\"field\":\"roleCode\"", "\"field\":\"rowVersion\"");
    verifyNoInteractions(changeRole);
  }

  @Test
  void tellsTheThreeRejectionsApart() throws Exception {
    UserPrincipal admin = signedIn("admin", "USER_ROLE_ASSIGN");

    when(changeRole.execute(any(), any(), eq(admin)))
        .thenThrow(
            new UnprocessableEntityException(
                "roleCode", ChangeAccountRoleUseCase.UNAVAILABLE_ROLE, "The role is not available"));
    var unavailable = perform(json(put(ROUTE), BODY), "admin");
    assertThat(unavailable.getStatus()).isEqualTo(422);
    assertThat(unavailable.getContentAsString())
        .contains("\"field\":\"roleCode\"", "\"code\":\"UnavailableRole\"");

    when(changeRole.execute(any(), any(), eq(admin)))
        .thenThrow(
            new FieldConflictException(
                "roleCode",
                ChangeAccountRoleUseCase.LAST_ADMINISTRATOR,
                "The last active Administrator account keeps its role"));
    var lastAdministrator = perform(json(put(ROUTE), BODY), "admin");
    assertThat(lastAdministrator.getStatus()).isEqualTo(409);
    assertThat(lastAdministrator.getContentAsString())
        .contains("\"field\":\"roleCode\"", "\"code\":\"LastAdministrator\"");

    when(changeRole.execute(any(), any(), eq(admin))).thenThrow(new ConcurrentUpdateException());
    var stale = perform(json(put(ROUTE), BODY), "admin");
    assertThat(stale.getStatus()).isEqualTo(409);
    assertThat(stale.getContentAsString()).doesNotContain("LastAdministrator");
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
