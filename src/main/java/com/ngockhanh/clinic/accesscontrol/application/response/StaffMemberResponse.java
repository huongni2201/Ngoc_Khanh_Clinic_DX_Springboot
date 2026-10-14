package com.ngockhanh.clinic.accesscontrol.application.response;

import com.ngockhanh.clinic.accesscontrol.application.query.StaffMemberSummary;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffMember;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/** Staff member with its account (docs/api/staff-members.md). Never carries a password or hash. */
@Builder
public record StaffMemberResponse(
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

  /** Account of the staff member; {@code roleCodes} are ordered by code. */
  @Builder
  public record Account(UUID id, String username, String status, List<String> roleCodes) {
    public Account {
      roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }
  }

  public static StaffMemberResponse created(StaffMember staffMember, StaffAccount account) {
    return StaffMemberResponse.builder()
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
                account.id(), account.username(), account.status().name(), account.roleCodes()))
        .build();
  }

  public static StaffMemberResponse from(StaffMemberSummary summary) {
    StaffMemberSummary.Account account = summary.account();
    return StaffMemberResponse.builder()
        .id(summary.id())
        .staffCode(summary.staffCode())
        .fullName(summary.fullName())
        .dateOfBirth(summary.dateOfBirth())
        .sex(summary.sex())
        .phone(summary.phone())
        .email(summary.email())
        .status(summary.status())
        .rowVersion(summary.rowVersion())
        .account(
            account == null
                ? null
                : new Account(account.id(), account.username(), account.status(), account.roleCodes()))
        .build();
  }
}
