package com.ngockhanh.clinic.accesscontrol.application.response;

import java.util.UUID;

/**
 * Active staff role that can be granted to a new STAFF account.
 *
 * @param roleId role identifier
 * @param code role code
 * @param name role name
 * @param description role description
 */
public record AssignableRoleResponse(UUID roleId, String code, String name, String description) {}
