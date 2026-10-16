package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view;

import java.util.UUID;

/**
 * Active role a staff account may hold, with the number of permissions it grants. Component order
 * matches the SELECT column order used for constructor mapping.
 */
public record AssignableRoleView(
    UUID id, String code, String name, String description, Long permissionCount) {}
