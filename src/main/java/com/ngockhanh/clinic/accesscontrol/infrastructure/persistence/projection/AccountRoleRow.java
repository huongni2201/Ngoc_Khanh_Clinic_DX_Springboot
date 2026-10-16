package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection;

import java.util.UUID;

/**
 * Row of {@code accounts JOIN roles} read before a role change. Component order matches the SELECT
 * column order used for constructor mapping.
 */
public record AccountRoleRow(
    UUID accountId,
    String accountType,
    String status,
    UUID roleId,
    String roleCode,
    Long rowVersion) {}
