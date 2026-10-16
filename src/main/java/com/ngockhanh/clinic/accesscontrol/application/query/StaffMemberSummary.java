package com.ngockhanh.clinic.accesscontrol.application.query;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffMember;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * Staff member with its account (docs/api/staff-members.md): the read contract of the list port and
 * the result of create, returned to HTTP callers as is. Never carries a password or hash.
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
   * Account of the staff member and the code of the one role it holds.
   *
   * @param rowVersion account version to send back when changing its role
   */
  @Builder
  public record Account(
      UUID id, String username, String status, String roleCode, Long rowVersion) {}

  public static StaffMemberSummary created(StaffMember staffMember, StaffAccount account) {
    return StaffMemberSummary.builder()
        .id(staffMember.id())
        .staffCode(staffMember.staffCode())
        .fullName(staffMember.fullName())
        .dateOfBirth(staffMember.dateOfBirth())
        .sex(staffMember.sex() == null ? null : staffMember.sex().name())
        .phone(staffMember.phone())
        .email(staffMember.email())
        .status(staffMember.status().name())
        .rowVersion(staffMember.rowVersion())
        .account(
            new Account(
                account.id(),
                account.username(),
                account.status().name(),
                account.role().code(),
                0L))
        .build();
  }
}
