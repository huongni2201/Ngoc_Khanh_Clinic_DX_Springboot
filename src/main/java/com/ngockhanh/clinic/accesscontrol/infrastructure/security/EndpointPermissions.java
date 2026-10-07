package com.ngockhanh.clinic.accesscontrol.infrastructure.security;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Permission required by each business endpoint, using the codes of the SRS Permission Matrix 4.4
 * seeded by V002 (ADR-0015). Every new {@code /api/v1} endpoint needs a rule here; endpoints without
 * one are denied. Permissions are those captured in the session at sign-in.
 */
public final class EndpointPermissions {

  /** Staff endpoint, matched by HTTP method and path pattern, and the permission it requires. */
  public record Rule(HttpMethod method, String pattern, String permission) {}

  public static final List<Rule> RULES =
      List.of(
          new Rule(HttpMethod.GET, "/api/v1/organizations/*", "ORGANIZATION_VIEW"),
          new Rule(HttpMethod.POST, "/api/v1/organizations", "ORGANIZATION_CREATE"),
          new Rule(HttpMethod.PUT, "/api/v1/organizations/*", "ORGANIZATION_UPDATE"),
          new Rule(
              HttpMethod.GET,
              "/api/v1/organizations/*/health-examination-batches",
              "HEALTH_EXAMINATION_BATCH_VIEW"),
          new Rule(
              HttpMethod.POST,
              "/api/v1/organizations/*/health-examination-batches",
              "HEALTH_EXAMINATION_BATCH_CREATE"));

  private EndpointPermissions() {}

  /**
   * Requires a STAFF account holding the rule's permission, so a permission granted to another
   * account type never opens a staff endpoint.
   *
   * @param requests authorization rules being configured
   */
  public static void apply(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          requests) {
    for (Rule rule : RULES) {
      requests
          .requestMatchers(rule.method(), rule.pattern())
          .hasAllAuthorities("ACCOUNT_STAFF", "PERM_" + rule.permission());
    }
  }
}
