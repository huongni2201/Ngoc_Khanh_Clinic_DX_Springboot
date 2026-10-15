package com.ngockhanh.clinic.accesscontrol.api.request;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateRoleCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Builder;

/**
 * Body of {@code POST /api/v1/admin/roles} (docs/api/roles-and-permissions.md; SRS 1.3.2).
 *
 * @param code uppercase letters, digits and underscores, starting with a letter
 * @param description optional, at most 500 characters
 * @param copyFromRoleId active role whose permissions are copied, or null
 * @param active null means active
 */
@Builder
public record CreateRoleRequest(
    @NotBlank @Size(max = 80) @Pattern(regexp = CODE_PATTERN) String code,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    UUID copyFromRoleId,
    Boolean active) {
  public static final String CODE_PATTERN = "[A-Z][A-Z0-9_]*";

  public CreateRoleCommand toCommand() {
    return CreateRoleCommand.builder()
        .code(code)
        .name(name)
        .description(description)
        .copyFromRoleId(copyFromRoleId)
        .active(active)
        .build();
  }
}
