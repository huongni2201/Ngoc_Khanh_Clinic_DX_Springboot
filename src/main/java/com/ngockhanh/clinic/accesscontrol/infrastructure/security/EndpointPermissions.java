package com.ngockhanh.clinic.accesscontrol.infrastructure.security;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Permission required by each business endpoint, using the codes of the SRS Permission Matrix 4.4
 * seeded by V004 (ADR-0015). Every new {@code /api/v1} endpoint needs a rule here for normal
 * access; endpoints without one are denied except in local test full-access mode. Permissions are
 * those captured in the session at sign-in.
 */
public final class EndpointPermissions {

  /** Staff endpoint, matched by HTTP method and path pattern, and the permission it requires. */
  public record Rule(HttpMethod method, String pattern, String permission) {}

  public static final List<Rule> RULES =
      List.of(
          new Rule(HttpMethod.GET, "/api/v1/organizations", "ORGANIZATION_SEARCH"),
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
              "HEALTH_EXAMINATION_BATCH_CREATE"),
          new Rule(
              HttpMethod.GET,
              "/api/v1/organizations/*/health-examination-batches/*",
              "HEALTH_EXAMINATION_BATCH_VIEW"),
          new Rule(
              HttpMethod.PUT,
              "/api/v1/organizations/*/health-examination-batches/*",
              "HEALTH_EXAMINATION_BATCH_UPDATE"),
          new Rule(
              HttpMethod.GET,
              "/api/v1/organizations/*/health-examination-batches/*/participants",
              "PARTICIPANT_VIEW"),
          new Rule(
              HttpMethod.GET,
              "/api/v1/organizations/*/health-examination-batches/*/participants/import-template",
              "PARTICIPANT_TEMPLATE_DOWNLOAD"),
          new Rule(
              HttpMethod.POST,
              "/api/v1/organizations/*/health-examination-batches/*/participants/imports",
              "PARTICIPANT_IMPORT"),
          new Rule(
              HttpMethod.POST,
              "/api/v1/organizations/*/health-examination-batches/*/participants",
              "PARTICIPANT_CREATE"),
          new Rule(
              HttpMethod.GET,
              "/api/v1/organizations/*/health-examination-batches/*/participants/*",
              "PARTICIPANT_VIEW"),
          new Rule(
              HttpMethod.PUT,
              "/api/v1/organizations/*/health-examination-batches/*/participants/*",
              "PARTICIPANT_UPDATE"),
          new Rule(
              HttpMethod.DELETE,
              "/api/v1/organizations/*/health-examination-batches/*/participants/*",
              "PARTICIPANT_REMOVE"),
          new Rule(
              HttpMethod.POST,
              "/api/v1/organizations/*/health-examination-batches/*/participants/*/reactivate",
              "PARTICIPANT_REACTIVATE"),
          new Rule(
              HttpMethod.GET,
              "/api/v1/organizations/*/health-examination-batches/*/examination-details",
              "HEALTH_EXAMINATION_SERVICE_READ"),
          new Rule(
              HttpMethod.GET,
              "/api/v1/organizations/*/health-examination-batches/*/examination-details/summary",
              "HEALTH_EXAMINATION_SERVICE_READ"),
          new Rule(
              HttpMethod.GET,
              "/api/v1/organizations/*/health-examination-batches/*/examination-details/export",
              "HEALTH_EXAMINATION_SERVICE_READ"),
          new Rule(
              HttpMethod.POST,
              "/api/v1/organizations/*/health-examination-batches/*/examination-details/imports",
              "HEALTH_EXAMINATION_SERVICE_RECONCILE"),
          new Rule(
              HttpMethod.GET,
              "/api/v1/organizations/*/health-examination-batches/*/reports/payment-summary",
              "HEALTH_EXAMINATION_REPORT_READ"),
          new Rule(
              HttpMethod.GET,
              "/api/v1/organizations/*/health-examination-batches/*/reports/payment-summary/docx",
              "HEALTH_EXAMINATION_REPORT_READ"));

  private EndpointPermissions() {}

  /**
   * Requires a STAFF account holding the rule's permission, so a permission granted to another
   * account type never opens a staff endpoint. Local test access can explicitly allow ROLE_TEST.
   *
   * @param requests authorization rules being configured
   * @param testRoleFullAccess whether ROLE_TEST bypasses per-endpoint permission checks
   */
  public static void apply(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          requests,
      boolean testRoleFullAccess) {
    for (Rule rule : RULES) {
      if (testRoleFullAccess) {
        requests
            .requestMatchers(rule.method(), rule.pattern())
            .access(
                AuthorizationManagers.anyOf(
                    new AuthorizationDecision(false),
                    AuthorityAuthorizationManager.hasAuthority("ROLE_TEST"),
                    AuthorizationManagers.allOf(
                        new AuthorizationDecision(false),
                        AuthorityAuthorizationManager.hasAuthority("ACCOUNT_STAFF"),
                        AuthorityAuthorizationManager.hasAuthority("PERM_" + rule.permission()))));
      } else {
        requests
            .requestMatchers(rule.method(), rule.pattern())
            .hasAllAuthorities("ACCOUNT_STAFF", "PERM_" + rule.permission());
      }
    }
  }
}
