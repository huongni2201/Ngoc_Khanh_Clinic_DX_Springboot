package com.ngockhanh.clinic.accesscontrol.application.query;

import java.util.UUID;
import lombok.Builder;

/**
 * Role with its usage counts (docs/api/roles-and-permissions.md): a row of the Role List screen and
 * the result of creating or updating a role, returned to HTTP callers as is.
 *
 * @param description optional description, or null
 * @param permissionCount number of permissions granted to the role
 * @param userCount number of accounts holding the role, whatever their status
 * @param rowVersion version to send back when updating the role or its permissions
 */
@Builder
public record RoleSummary(
    UUID id,
    String code,
    String name,
    String description,
    Boolean active,
    Long permissionCount,
    Long userCount,
    Long rowVersion) {}
