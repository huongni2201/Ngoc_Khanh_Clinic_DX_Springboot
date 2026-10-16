package com.ngockhanh.clinic.accesscontrol.api.request;

import com.ngockhanh.clinic.accesscontrol.application.command.UpdateRolePermissionsCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;

/**
 * Body of {@code PUT /api/v1/admin/roles/{roleId}/permissions} (docs/api/roles-and-permissions.md;
 * SRS 1.3.4). Only the pending changes are sent, not the whole permission set.
 *
 * @param rowVersion role version the client last read; required and not negative
 * @param grant permission codes to grant
 * @param revoke permission codes to remove
 */
@Builder
public record UpdateRolePermissionsRequest(
    @NotNull @PositiveOrZero Long rowVersion,
    @Size(max = 500) List<@NotBlank @Size(max = 120) String> grant,
    @Size(max = 500) List<@NotBlank @Size(max = 120) String> revoke) {

  public UpdateRolePermissionsCommand toCommand() {
    return UpdateRolePermissionsCommand.builder()
        .rowVersion(rowVersion)
        .grant(grant)
        .revoke(revoke)
        .build();
  }
}
