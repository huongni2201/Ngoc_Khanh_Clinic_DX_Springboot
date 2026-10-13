package com.ngockhanh.clinic.accesscontrol.api.request;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateUserAccountCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Account creation input (UC-005). Structural rules only: the password policy and the role rules of
 * each account type are enforced by the use case. Messages never repeat the submitted values.
 *
 * @param accountType STAFF or PATIENT
 * @param ownerId staff member or patient that will own the account
 * @param username printable ASCII, no leading or trailing spaces, at most 150 characters
 * @param temporaryPassword temporary password, at most 72 characters
 * @param roleIds initial roles of a STAFF account; empty for a PATIENT account
 */
@Builder
public record CreateUserAccountRequest(
    @NotNull(message = "Account type is required")
        @Pattern(regexp = "STAFF|PATIENT", message = "Account type must be STAFF or PATIENT")
        String accountType,
    @NotNull(message = "Account owner is required") UUID ownerId,
    @NotNull(message = "Username is required")
        @Size(min = 1, max = 150, message = "Username must have 1 to 150 characters")
        @Pattern(
            regexp = UsernameRules.PATTERN,
            message = "Username must use letters without diacritics, digits or symbols")
        String username,
    @NotNull(message = "Temporary password is required")
        @Size(max = 72, message = "Temporary password must not exceed 72 characters")
        String temporaryPassword,
    @NotNull(message = "Roles are required")
        @Size(max = 20, message = "At most 20 roles can be selected")
        List<@NotNull(message = "Role is required") UUID> roleIds) {

  /** Maps the validated HTTP input to the application command. */
  public CreateUserAccountCommand toCommand() {
    return CreateUserAccountCommand.builder()
        .accountType(accountType)
        .ownerId(ownerId)
        .username(username)
        .temporaryPassword(temporaryPassword)
        .roleIds(roleIds)
        .build();
  }

  @Override
  public String toString() {
    return "CreateUserAccountRequest[accountType=" + accountType + ", ownerId=" + ownerId + "]";
  }
}
