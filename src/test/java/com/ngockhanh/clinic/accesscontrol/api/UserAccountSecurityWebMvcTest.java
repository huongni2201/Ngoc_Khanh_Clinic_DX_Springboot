package com.ngockhanh.clinic.accesscontrol.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.ngockhanh.clinic.accesscontrol.api.controller.UserAccountController;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountOwnerCandidateListResponse;
import com.ngockhanh.clinic.accesscontrol.application.response.CreateUserAccountResponse;
import com.ngockhanh.clinic.accesscontrol.application.response.UsernameAvailabilityResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CheckUsernameAvailabilityUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateUserAccountUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListAssignableRolesUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.SearchAccountOwnerCandidatesUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.configuration.SecurityConfiguration;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.healthexamination.ParticipantFixtures;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * Exercises the real user account controller (UC-005) through the production cookie, Origin and
 * permission filters, and its request validation.
 */
@WebMvcTest(
    controllers = UserAccountController.class,
    excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({
  SecurityConfiguration.class,
  ClockConfiguration.class,
  UserAccountSecurityWebMvcTest.StoredEndpointPermissions.class
})
@TestPropertySource(
    properties = {"clinic.auth.cookie-secure=true", "clinic.auth.allowed-origins=https://app.test"})
class UserAccountSecurityWebMvcTest {
  private static final String BASE = "/api/v1/user-accounts";
  private static final String PASSWORD = "Abcdef1!";
  private static final String ORIGIN = "https://app.test";
  private static final String COOKIE = "__Host-NKC_SESSION";
  private static final String BODY =
      """
      {"accountType":"STAFF","ownerId":"01990000-0000-7000-8000-000000000001",
       "username":"nguyen.van.a","temporaryPassword":"%s",
       "roleIds":["01990000-0000-7000-8000-000000000002"]}
      """
          .formatted(PASSWORD);

  /** The user account endpoints and their permissions as stored in {@code public.permissions}. */
  @TestConfiguration
  static class StoredEndpointPermissions {
    @Bean
    UserLoginMyBatisMapper endpointPermissionMapper() {
      var mapper = mock(UserLoginMyBatisMapper.class);
      when(mapper.findEndpointPermissions())
          .thenReturn(
              List.of(
                  endpoint("POST", "", "USER_ACCOUNT_CREATE"),
                  endpoint("GET", "/owner-candidates", "USER_ACCOUNT_OWNER_SEARCH"),
                  endpoint("GET", "/username-availability", "USER_ACCOUNT_USERNAME_CHECK"),
                  endpoint("GET", "/assignable-roles", "USER_ACCOUNT_ASSIGNABLE_ROLE_VIEW")));
      return mapper;
    }

    private static PermissionRecord endpoint(String method, String path, String code) {
      return PermissionRecord.builder().code(code).httpMethod(method).endpoint(BASE + path).build();
    }
  }

  @Autowired MockMvc mvc;
  @MockitoBean AuthenticateSessionUseCase authenticate;
  @MockitoBean CreateUserAccountUseCase create;
  @MockitoBean SearchAccountOwnerCandidatesUseCase ownerSearch;
  @MockitoBean CheckUsernameAvailabilityUseCase usernameCheck;
  @MockitoBean ListAssignableRolesUseCase assignableRoles;

  private record Route(
      String name,
      String permission,
      Integer successStatus,
      Supplier<AbstractMockHttpServletRequestBuilder<?>> request) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Route> routes() {
    return Stream.of(
        new Route(
            "create",
            "USER_ACCOUNT_CREATE",
            201,
            () -> post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY)),
        new Route(
            "owner candidates",
            "USER_ACCOUNT_OWNER_SEARCH",
            200,
            () ->
                get(BASE + "/owner-candidates")
                    .param("accountType", "STAFF")
                    .param("keyword", "Nguyen")),
        new Route(
            "username availability",
            "USER_ACCOUNT_USERNAME_CHECK",
            200,
            () -> get(BASE + "/username-availability").param("username", "nguyen.van.a")),
        new Route(
            "assignable roles",
            "USER_ACCOUNT_ASSIGNABLE_ROLE_VIEW",
            200,
            () -> get(BASE + "/assignable-roles")));
  }

  @BeforeEach
  void stubResponsesAtTheApplicationBoundary() {
    when(create.execute(any(), any()))
        .thenReturn(
            CreateUserAccountResponse.builder()
                .accountId(UUID.randomUUID())
                .accountType("STAFF")
                .username("nguyen.van.a")
                .status("ACTIVE")
                .ownerId(UUID.randomUUID())
                .roleCodes(List.of("GENERAL_PRACTITIONER"))
                .smsDelivered(false)
                .build());
    when(ownerSearch.execute(any(), any(), any()))
        .thenReturn(new AccountOwnerCandidateListResponse(List.of(), false));
    when(usernameCheck.execute(any(), any())).thenReturn(new UsernameAvailabilityResponse(true));
    when(assignableRoles.execute(any())).thenReturn(List.of());
  }

  @ParameterizedTest
  @MethodSource("routes")
  void acceptsOnlyTheRoutesPermissionOnAStaffAccount(Route route) throws Exception {
    for (String permission : routes().map(Route::permission).toList()) {
      when(authenticate.execute("session")).thenReturn(ParticipantFixtures.staff(permission));
      assertThat(status(route, "session", ORIGIN))
          .as(permission)
          .isEqualTo(permission.equals(route.permission()) ? route.successStatus() : 403);
    }
  }

  @ParameterizedTest
  @MethodSource("routes")
  void rejectsAnonymousCallersAndPatientAccounts(Route route) throws Exception {
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
    verifyNoInteractions(create, ownerSearch, usernameCheck, assignableRoles);
  }

  @Test
  void createRejectsAnUnknownOrMissingOrigin() throws Exception {
    when(authenticate.execute("session"))
        .thenReturn(ParticipantFixtures.staff("USER_ACCOUNT_CREATE"));
    Route createRoute = routes().findFirst().orElseThrow();

    assertThat(status(createRoute, "session", "https://evil.test")).isEqualTo(403);
    assertThat(status(createRoute, "session", null)).isEqualTo(403);
    verifyNoInteractions(create);
  }

  @Test
  void createdAccountsAreNotCachedAndNeverEchoThePassword() throws Exception {
    when(authenticate.execute("session"))
        .thenReturn(ParticipantFixtures.staff("USER_ACCOUNT_CREATE"));

    var response =
        mvc.perform(
                post(BASE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BODY)
                    .header("Origin", ORIGIN)
                    .cookie(new Cookie(COOKIE, "session")))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getHeader("Cache-Control")).contains("no-store");
    assertThat(response.getContentAsString()).doesNotContain(PASSWORD).contains("smsDelivered");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"username\":\"nguyễn.văn.a\"",
        "\"username\":\" nguyen\"",
        "\"username\":\"nguyen \"",
        "\"username\":\"\""
      })
  void createRejectsUsernamesWithDiacriticsOrSurroundingSpaces(String username) throws Exception {
    String body = BODY.replace("\"username\":\"nguyen.van.a\"", username);

    assertThat(createWith(body)).contains("\"field\":\"username\"").doesNotContain(PASSWORD);
    verifyNoInteractions(create);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"temporaryPassword\":\"\"", "\"temporaryPassword\":\"   \""})
  void createRejectsABlankPassword(String password) throws Exception {
    String body = BODY.replace("\"temporaryPassword\":\"" + PASSWORD + "\"", password);

    assertThat(createWith(body)).contains("\"field\":\"temporaryPassword\"");
    verifyNoInteractions(create);
  }

  @Test
  void createRejectsMissingOwnerTypeAndRoles() throws Exception {
    String body =
        """
        {"accountType":"ADMIN","username":"nguyen.van.a","temporaryPassword":"%s"}
        """
            .formatted(PASSWORD);

    assertThat(createWith(body))
        .contains("\"field\":\"ownerId\"")
        .contains("\"field\":\"accountType\"")
        .contains("\"field\":\"roleIds\"");
    verifyNoInteractions(create);
  }

  @Test
  void ownerSearchNeedsAKeywordOfAtLeastTwoCharacters() throws Exception {
    when(authenticate.execute("session"))
        .thenReturn(ParticipantFixtures.staff("USER_ACCOUNT_OWNER_SEARCH"));

    var response =
        mvc.perform(
                get(BASE + "/owner-candidates")
                    .param("accountType", "STAFF")
                    .param("keyword", "N")
                    .cookie(new Cookie(COOKIE, "session")))
            .andReturn()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getContentAsString()).contains("\"field\":\"keyword\"");
    verifyNoInteractions(ownerSearch);
  }

  private String createWith(String body) throws Exception {
    when(authenticate.execute("session"))
        .thenReturn(ParticipantFixtures.staff("USER_ACCOUNT_CREATE"));
    var response =
        mvc.perform(
                post(BASE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header("Origin", ORIGIN)
                    .cookie(new Cookie(COOKIE, "session")))
            .andReturn()
            .getResponse();
    assertThat(response.getStatus()).isEqualTo(400);
    return response.getContentAsString();
  }

  private Integer status(Route route, String session, String origin) throws Exception {
    var request = route.request().get();
    if (origin != null) request.header("Origin", origin);
    if (session != null) request.cookie(new Cookie(COOKIE, session));
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }
}
