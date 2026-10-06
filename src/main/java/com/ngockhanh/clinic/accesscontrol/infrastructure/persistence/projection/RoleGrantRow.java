package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection;

import java.util.UUID;

/**
 * One active role of an account paired with one of its permissions; {@code permissionCode} is null
 * for a role without permissions.
 */
public record RoleGrantRow(UUID roleId, String roleCode, String permissionCode) {}
