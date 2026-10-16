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
 * @param name at most 100 characters once changed; up to the 200 the column allows when kept as
 *     stored
 * @param description optional, at most 500 characters once changed
 * @param active false deactivates the role, true reactivates it
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record UpdateRoleRequest(
    @NotBlank @Size(max = 200) String name,
    String description,
    @NotNull Boolean active,
    @NotNull @PositiveOrZero Long rowVersion) {
  /** Lengths are checked on the trimmed text, which is what the role stores. */
  public UpdateRoleRequest {
    name = name == null ? null : name.trim();
    description = description == null ? null : description.trim();
  }

  public UpdateRoleCommand toCommand() {
    return UpdateRoleCommand.builder()
        .name(name)
        .description(description)
        .active(active)
        .rowVersion(rowVersion)
        .build();
  }
}
