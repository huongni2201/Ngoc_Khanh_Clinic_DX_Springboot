package com.ngockhanh.clinic.accesscontrol.application.command;

import java.time.LocalDate;
import lombok.Builder;

/**
 * Staff member to create with its account.
 *
 * @param sex {@code MALE}, {@code FEMALE}, {@code OTHER}, {@code UNKNOWN} or null
 * @param initialPassword temporary password exactly as typed; never logged or returned
 * @param roleCode code of the one role the account holds
 */
@Builder
public record CreateStaffMemberCommand(
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String phone,
    String email,
    String initialPassword,
    String roleCode) {

  /** Omits personal data and the password. */
  @Override
  public String toString() {
    return "CreateStaffMemberCommand[roleCode=" + roleCode + "]";
  }
}
