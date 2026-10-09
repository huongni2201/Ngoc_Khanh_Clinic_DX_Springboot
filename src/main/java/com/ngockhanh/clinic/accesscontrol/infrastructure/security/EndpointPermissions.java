package com.ngockhanh.clinic.accesscontrol.infrastructure.security;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Authorizes business endpoints with the permissions stored in {@code public.permissions}
 * (ADR-0015): each permission names one endpoint by HTTP method and controller route template. A
 * request is allowed when it matches a stored endpoint and the account is STAFF and holds that
 * permission; the most specific template wins. Requests matching no stored endpoint are denied.
 * Endpoints are read once at startup; permissions are those captured in the session at sign-in.
 */
public final class EndpointPermissions
    implements AuthorizationManager<RequestAuthorizationContext> {

  private record Endpoint(PathPattern template, PathPatternRequestMatcher matcher, String permission) {}

  private final List<Endpoint> endpoints;

  /**
   * @param permissions permissions that name an endpoint
   */
  public EndpointPermissions(List<PermissionRecord> permissions) {
    this.endpoints =
        permissions.stream()
            .map(
                permission ->
                    new Endpoint(
                        PathPatternParser.defaultInstance.parse(permission.endpoint()),
                        PathPatternRequestMatcher.pathPattern(
                            HttpMethod.valueOf(permission.httpMethod()), permission.endpoint()),
                        permission.code()))
            .sorted(Comparator.comparing(Endpoint::template, PathPattern.SPECIFICITY_COMPARATOR))
            .toList();
  }

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
    String permission =
        endpoints.stream()
            .filter(endpoint -> endpoint.matcher().matches(context.getRequest()))
            .map(Endpoint::permission)
            .findFirst()
            .orElse(null);
    if (permission == null) return new AuthorizationDecision(false);
    List<String> authorities =
        authentication.get().getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    return new AuthorizationDecision(
        authorities.contains("ACCOUNT_STAFF") && authorities.contains("PERM_" + permission));
  }
}
