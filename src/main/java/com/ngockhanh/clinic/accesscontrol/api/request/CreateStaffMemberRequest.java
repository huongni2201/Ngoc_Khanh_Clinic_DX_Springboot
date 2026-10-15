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

/**
 * Body of {@code POST /api/v1/staff-members} (docs/api/staff-members.md). The full name needs a
 * Latin letter to generate the username; the temporary password is printable ASCII without spaces
 * or diacritics, so its 8 to 72 characters are also its bcrypt byte length.
 */
@Builder
public record CreateStaffMemberRequest(
    @NotBlank @Size(max = 200) @Pattern(regexp = LATIN_NAME_PATTERN) String fullName,
    @Past LocalDate dateOfBirth,
    @Pattern(regexp = "MALE|FEMALE|OTHER|UNKNOWN") String sex,
    @Size(max = 30) String phone,
    @Email @Size(max = 255) String email,
    @NotNull @Size(min = 8, max = 72) @Pattern(regexp = PASSWORD_PATTERN) String initialPassword,
    @NotBlank @Size(max = 80) String roleCode) {
  /**
   * Letters of the Vietnamese alphabet (plus f, j, w, z), precomposed or followed by combining
   * marks: exactly the letters the username generator reduces to {@code a}–{@code z}. Letters
   * such as {@code ø}, {@code ß}, {@code æ} or {@code ł} are excluded.
   */
  private static final String NAME_LETTERS = "A-Za-zÀ-ÃÈ-ÊÌÍÒ-ÕÙÚÝà-ãè-êìíò-õùúýĂăĐđĨĩŨũƠơƯưẠ-ỹ";

  /**
   * Only those letters, combining marks, spaces, {@code .}, {@code '} and {@code -}, with at least
   * one letter, so a username can always be generated. Digits and other scripts are rejected.
   */
  public static final String LATIN_NAME_PATTERN =
      "(?=.*[" + NAME_LETTERS + "])[" + NAME_LETTERS + "\\p{M} .'-]+";

  /** Printable ASCII without spaces: letters, digits and symbols. */
  public static final String PASSWORD_PATTERN = "[\\x21-\\x7E]+";

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
