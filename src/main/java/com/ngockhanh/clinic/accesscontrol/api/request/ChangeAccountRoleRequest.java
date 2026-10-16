package com.ngockhanh.clinic.accesscontrol.api.request;

import com.ngockhanh.clinic.accesscontrol.application.command.ChangeAccountRoleCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Body of {@code PUT /api/v1/accounts/{accountId}/role} (docs/api/staff-members.md; SRS UC-010).
 *
 * @param roleCode code of an active role other than {@code PATIENT}
 * @param rowVersion account version the client last read; required and not negative
 */
@Builder
public record ChangeAccountRoleRequest(
    @NotBlank @Size(max = 80) String roleCode, @NotNull @PositiveOrZero Long rowVersion) {

  public ChangeAccountRoleCommand toCommand() {
    return ChangeAccountRoleCommand.builder().roleCode(roleCode).rowVersion(rowVersion).build();
  }
}
