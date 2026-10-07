package com.ngockhanh.clinic.accesscontrol.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.application.port.EndpointPermissionCatalog.EndpointPermission;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

class EndpointPermissionInterceptorTest {
  private static final String ORGANIZATION = "/api/v1/organizations/{organizationId}";

  private final EndpointPermissionInterceptor interceptor =
      new EndpointPermissionInterceptor(
          List.of(new EndpointPermission("GET", ORGANIZATION, "ORGANIZATION_VIEW")));

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private static void signedInWith(String... authorities) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "user", null, Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
  }

  private static MockHttpServletRequest request(String method, String uri, String template) {
    var request = new MockHttpServletRequest(method, uri);
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, template);
    return request;
  }

  private static HandlerMethod controllerMethod() throws NoSuchMethodException {
    return new HandlerMethod(new Object(), Object.class.getMethod("toString"));
  }

  private boolean handle(MockHttpServletRequest request) throws Exception {
    return interceptor.preHandle(request, new MockHttpServletResponse(), controllerMethod());
  }

  @Test
  void looksThePermissionUpByTheRouteTemplateOfTheChosenController() throws Exception {
    signedInWith("ACCOUNT_STAFF", "PERM_ORGANIZATION_VIEW");

    assertThat(handle(request("GET", "/api/v1/organizations/123", ORGANIZATION))).isTrue();
  }

  @Test
  void deniesAMissingPermissionAnotherAccountTypeAndAnotherMethod() {
    signedInWith("ACCOUNT_STAFF", "PERM_ORGANIZATION_UPDATE");
    assertThatThrownBy(() -> handle(request("GET", "/api/v1/organizations/1", ORGANIZATION)))
        .isInstanceOf(AccessDeniedException.class);

    signedInWith("ACCOUNT_PATIENT", "PERM_ORGANIZATION_VIEW", "PERM_ACCOUNT_STAFF");
    assertThatThrownBy(() -> handle(request("GET", "/api/v1/organizations/1", ORGANIZATION)))
        .isInstanceOf(AccessDeniedException.class);

    signedInWith("ACCOUNT_STAFF", "PERM_ORGANIZATION_VIEW");
    assertThatThrownBy(() -> handle(request("PUT", "/api/v1/organizations/1", ORGANIZATION)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void deniesAnEndpointWithoutAStoredPermissionAndLetsUnknownRoutesThrough() throws Exception {
    signedInWith("ACCOUNT_STAFF", "PERM_ORGANIZATION_VIEW");
    assertThatThrownBy(() -> handle(request("GET", "/api/v1/other", "/api/v1/other")))
        .isInstanceOf(AccessDeniedException.class);

    var unknown = new MockHttpServletRequest("GET", "/api/v1/missing");
    assertThat(interceptor.preHandle(unknown, new MockHttpServletResponse(), new Object()))
        .isTrue();
  }

  @Test
  void rejectsTwoPermissionsForOneEndpoint() {
    assertThatThrownBy(
            () ->
                new EndpointPermissionInterceptor(
                    List.of(
                        new EndpointPermission("GET", ORGANIZATION, "ORGANIZATION_VIEW"),
                        new EndpointPermission("GET", ORGANIZATION, "ORGANIZATION_SEARCH"))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reportsControllerEndpointsAndStoredPermissionsThatDoNotMatch() {
    Set<RequestMappingInfo> mappings =
        Set.of(
            RequestMappingInfo.paths(ORGANIZATION).methods(RequestMethod.GET).build(),
            RequestMappingInfo.paths(ORGANIZATION).methods(RequestMethod.DELETE).build(),
            RequestMappingInfo.paths("/api/v1/auth/me").methods(RequestMethod.GET).build());
    var stale =
        new EndpointPermissionInterceptor(
            List.of(
                new EndpointPermission("GET", ORGANIZATION, "ORGANIZATION_VIEW"),
                new EndpointPermission("GET", "/api/v1/old", "ORGANIZATION_SEARCH")));

    assertThat(stale.endpointsWithoutPermission(mappings)).containsExactly("DELETE " + ORGANIZATION);
    assertThat(stale.permissionsWithoutEndpoint(mappings)).containsExactly("GET /api/v1/old");
  }
}
