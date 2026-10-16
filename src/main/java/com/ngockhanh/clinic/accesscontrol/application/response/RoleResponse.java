package com.ngockhanh.clinic.accesscontrol.application.response;

import com.ngockhanh.clinic.accesscontrol.application.query.AssignableRole;
import java.util.UUID;

/** Role that may be granted to a staff account. */
public record RoleResponse(
    UUID id, String code, String name, String description, Long permissionCount) {
  public static RoleResponse from(AssignableRole role) {
    return new RoleResponse(
        role.id(), role.code(), role.name(), role.description(), role.permissionCount());
  }
}
