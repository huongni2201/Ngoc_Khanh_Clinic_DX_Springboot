package com.ngockhanh.clinic.accesscontrol.api.request;

import com.ngockhanh.clinic.accesscontrol.application.command.UpdateRoleCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Body of {@code PUT /api/v1/admin/roles/{roleId}} (docs/api/roles-and-permissions.md; SRS 1.3.3).
 * The role code is not part of it: it never changes.
 *
 * @param description optional, at most 500 characters
 * @param active false deactivates the role, true reactivates it
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record UpdateRoleRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    @NotNull Boolean active,
    @NotNull @PositiveOrZero Long rowVersion) {

  public UpdateRoleCommand toCommand() {
    return UpdateRoleCommand.builder()
        .name(name)
        .description(description)
        .active(active)
        .rowVersion(rowVersion)
        .build();
  }
}
