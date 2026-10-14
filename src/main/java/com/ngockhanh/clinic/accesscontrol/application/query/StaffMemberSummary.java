package com.ngockhanh.clinic.accesscontrol.application.query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Staff member row of the administration list, with its account when one exists.
 *
 * @param sex enum name or null
 * @param status {@code ACTIVE}, {@code INACTIVE} or {@code SUSPENDED}
 * @param account null when the staff member has no account
 */
@Builder
public record StaffMemberSummary(
    UUID id,
    String staffCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String phone,
    String email,
    String status,
    Long rowVersion,
    Account account) {

  /**
   * Account of the staff member.
   *
   * @param status {@code ACTIVE}, {@code LOCKED} or {@code DISABLED}
   * @param roleCodes codes of the active granted roles, ordered by code
   */
  @Builder
  public record Account(UUID id, String username, String status, List<String> roleCodes) {
    public Account {
      roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }
  }
}
