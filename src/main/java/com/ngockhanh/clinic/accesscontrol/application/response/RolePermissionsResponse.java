package com.ngockhanh.clinic.accesscontrol.application.response;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Permissions granted to one role (SRS 1.3.4).
 *
 * @param active whether the role is active; permissions of an inactive role cannot change
 * @param rowVersion version to send back when saving the permissions
 * @param permissionCodes granted permission codes, ordered by code
 */
@Builder
public record RolePermissionsResponse(
    UUID roleId, String roleCode, Boolean active, Long rowVersion, List<String> permissionCodes) {
  public RolePermissionsResponse {
    permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
  }
}
