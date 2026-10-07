package com.ngockhanh.clinic.accesscontrol.application.port;

import java.util.List;

/** Permissions that protect an API endpoint, as stored with each permission (ADR-0015). */
public interface EndpointPermissionCatalog {
  /**
   * Lists every permission that names an endpoint.
   *
   * @return one entry per protected endpoint
   */
  List<EndpointPermission> findAll();

  /**
   * Permission required to call one endpoint.
   *
   * @param httpMethod HTTP method in upper case
   * @param endpoint route template declared by the controller, such as {@code
   *     /api/v1/organizations/{organizationId}}
   * @param permission permission code
   */
  record EndpointPermission(String httpMethod, String endpoint, String permission) {}
}
