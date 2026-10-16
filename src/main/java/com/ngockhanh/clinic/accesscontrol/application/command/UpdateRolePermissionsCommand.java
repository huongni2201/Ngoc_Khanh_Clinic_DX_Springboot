package com.ngockhanh.clinic.accesscontrol.application.command;

import java.util.List;
import lombok.Builder;

/**
 * Permissions to grant to and remove from one role (SRS UC-009, 1.3.4 Role Permissions).
 *
 * @param rowVersion version of the role the caller last read
 * @param grant permission codes to grant; codes already granted are ignored
 * @param revoke permission codes to remove; codes not granted are ignored
 */
@Builder
public record UpdateRolePermissionsCommand(
    Long rowVersion, List<String> grant, List<String> revoke) {
  public UpdateRolePermissionsCommand {
    grant = grant == null ? List.of() : List.copyOf(grant);
    revoke = revoke == null ? List.of() : List.copyOf(revoke);
  }
}
