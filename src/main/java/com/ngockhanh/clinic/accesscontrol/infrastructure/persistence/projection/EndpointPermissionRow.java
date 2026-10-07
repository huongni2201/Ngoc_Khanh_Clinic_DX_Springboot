package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection;

/**
 * Permission of {@code public.permissions} that names an endpoint. Component order matches the
 * SELECT column order used for constructor mapping.
 */
public record EndpointPermissionRow(String permissionCode, String httpMethod, String endpoint) {}
