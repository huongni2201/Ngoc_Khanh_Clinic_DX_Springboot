package com.ngockhanh.clinic.accesscontrol.application.command;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * Staff member to create with its account.
 *
 * @param sex {@code MALE}, {@code FEMALE}, {@code OTHER}, {@code UNKNOWN} or null
 * @param initialPassword temporary password exactly as typed; never logged or returned
 * @param roleCodes codes of the roles to grant; duplicates are ignored
 */
@Builder
public record CreateStaffMemberCommand(
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String phone,
    String email,
    String initialPassword,
    List<String> roleCodes) {

  public CreateStaffMemberCommand {
    roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
  }

  /** Omits personal data and the password. */
  @Override
  public String toString() {
    return "CreateStaffMemberCommand[roleCodes=" + roleCodes + "]";
  }
}
