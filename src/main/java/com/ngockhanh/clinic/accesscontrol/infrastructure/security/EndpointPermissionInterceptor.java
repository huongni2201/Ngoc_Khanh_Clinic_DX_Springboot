package com.ngockhanh.clinic.accesscontrol.infrastructure.security;

import com.ngockhanh.clinic.accesscontrol.application.port.EndpointPermissionCatalog.EndpointPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/**
 * Authorizes each business endpoint with the permission stored for it in {@code public.permissions}
 * (ADR-0015). It runs after Spring MVC has chosen the controller method, and looks the permission up
 * by the HTTP method and the exact route template of that method. A STAFF account holding the
 * permission may call the endpoint; an endpoint without a stored permission is denied. Permissions
 * are those captured in the session at sign-in.
 */
public final class EndpointPermissionInterceptor implements HandlerInterceptor {
  private static final String STAFF = "ACCOUNT_STAFF";

  private final Map<Endpoint, String> permissions;

  /**
   * @param endpointPermissions stored permissions, read once at startup
   * @throws IllegalStateException if two permissions name the same endpoint
   */
  public EndpointPermissionInterceptor(List<EndpointPermission> endpointPermissions) {
    var byEndpoint = new HashMap<Endpoint, String>();
    for (EndpointPermission entry : endpointPermissions) {
      String previous =
          byEndpoint.put(new Endpoint(entry.httpMethod(), entry.endpoint()), entry.permission());
      if (previous != null)
        throw new IllegalStateException(
            "Endpoint " + entry.httpMethod() + " " + entry.endpoint() + " has two permissions");
    }
    this.permissions = Map.copyOf(byEndpoint);
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    // Requests without a controller method (unknown URLs) continue to the 404 response.
    if (!(handler instanceof HandlerMethod)) return true;
    Object template = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String permission =
        template == null
            ? null
            : permissions.get(new Endpoint(request.getMethod(), template.toString()));
    if (permission == null)
      throw new AccessDeniedException("No permission is stored for this endpoint");
    Set<String> authorities = authorities();
    if (!authorities.contains(STAFF) || !authorities.contains("PERM_" + permission))
      throw new AccessDeniedException("The account lacks the permission of this endpoint");
    return true;
  }

  /**
   * Lists controller endpoints under {@code /api/v1} that would always be denied because no
   * permission names them. Sign-in routes are authorized by the security filter chain instead.
   *
   * @param mappings request mappings of the controllers
   * @return {@code "METHOD template"} of each endpoint without a permission
   */
  public List<String> endpointsWithoutPermission(Set<RequestMappingInfo> mappings) {
    var missing = new ArrayList<String>();
    for (Endpoint endpoint : businessEndpoints(mappings)) {
      if (!permissions.containsKey(endpoint)) missing.add(endpoint.toString());
    }
    return missing.stream().sorted().toList();
  }

  /**
   * Lists stored permissions whose endpoint no controller declares, usually a stale template.
   *
   * @param mappings request mappings of the controllers
   * @return {@code "METHOD template"} of each stored endpoint without a controller
   */
  public List<String> permissionsWithoutEndpoint(Set<RequestMappingInfo> mappings) {
    Set<Endpoint> declared = Set.copyOf(businessEndpoints(mappings));
    return permissions.keySet().stream()
        .filter(endpoint -> !declared.contains(endpoint))
        .map(Endpoint::toString)
        .sorted()
        .toList();
  }

  private static List<Endpoint> businessEndpoints(Set<RequestMappingInfo> mappings) {
    var endpoints = new ArrayList<Endpoint>();
    for (RequestMappingInfo mapping : mappings) {
      for (String template : mapping.getPatternValues()) {
        if (!template.startsWith("/api/v1/") || template.startsWith("/api/v1/auth/")) continue;
        var methods = mapping.getMethodsCondition().getMethods();
        if (methods.isEmpty()) endpoints.add(new Endpoint("*", template));
        methods.forEach(method -> endpoints.add(new Endpoint(method.name(), template)));
      }
    }
    return endpoints;
  }

  private static Set<String> authorities() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) return Set.of();
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toUnmodifiableSet());
  }

  private record Endpoint(String httpMethod, String template) {
    @Override
    public String toString() {
      return httpMethod + " " + template;
    }
  }
}
