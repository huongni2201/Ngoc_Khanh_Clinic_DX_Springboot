package com.ngockhanh.clinic.accesscontrol.application.query;

import java.util.UUID;

/**
 * Active role, other than {@code PATIENT}, that may be granted to a staff account.
 *
 * @param permissionCount number of permissions the role grants
 */
public record AssignableRole(
    UUID id, String code, String name, String description, Long permissionCount) {}
