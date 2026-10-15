package com.ngockhanh.clinic.accesscontrol.api.request;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateStaffMemberCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Builder;

/** Body of {@code POST /api/v1/staff-members} (docs/api/staff-members.md). */
@Builder
public record CreateStaffMemberRequest(
    @NotBlank @Size(max = 200) @Pattern(regexp = "(?s).*\\p{L}.*") String fullName,
    @Past LocalDate dateOfBirth,
    @Pattern(regexp = "MALE|FEMALE|OTHER|UNKNOWN") String sex,
    @Size(max = 30) String phone,
    @Email @Size(max = 255) String email,
    @NotNull @Size(min = 8, max = 1024) @BcryptPasswordLength String initialPassword,
    @NotBlank @Size(max = 80) String roleCode) {

  public CreateStaffMemberCommand toCommand() {
    return CreateStaffMemberCommand.builder()
        .fullName(fullName)
        .dateOfBirth(dateOfBirth)
        .sex(sex)
        .phone(phone)
        .email(email)
        .initialPassword(initialPassword)
        .roleCode(roleCode)
        .build();
  }

  /** Omits personal data and the password. */
  @Override
  public String toString() {
    return "CreateStaffMemberRequest[roleCode=" + roleCode + "]";
  }
}
