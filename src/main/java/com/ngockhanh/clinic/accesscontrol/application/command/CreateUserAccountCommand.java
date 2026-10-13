package com.ngockhanh.clinic.accesscontrol.application.command;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Account creation request of an Administrator (UC-005). {@link #toString()} hides the username and
 * the temporary password.
 *
 * @param accountType STAFF or PATIENT
 * @param ownerId staff member or patient that will own the account
 * @param username sign-in identifier
 * @param temporaryPassword temporary password in plain text
 * @param roleIds initial roles of a STAFF account; empty for a PATIENT account
 */
@Builder
public record CreateUserAccountCommand(
    String accountType,
    UUID ownerId,
    String username,
    String temporaryPassword,
    List<UUID> roleIds) {
  public CreateUserAccountCommand {
    roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
  }

  @Override
  public String toString() {
    return "CreateUserAccountCommand[accountType="
        + accountType
        + ", ownerId="
        + ownerId
        + ", roleCount="
        + roleIds.size()
        + "]";
  }
}
