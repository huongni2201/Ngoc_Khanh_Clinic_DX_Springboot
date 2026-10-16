package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter;

import com.ngockhanh.clinic.accesscontrol.application.query.AssignableRole;
import com.ngockhanh.clinic.accesscontrol.application.query.StaffMemberSummary;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffMember;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.AccountRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.RoleRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.StaffMemberRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view.AssignableRoleView;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view.StaffMemberListView;

/** Maps staff administration domain objects and SQL views without querying. */
public final class StaffMemberPersistenceConverter {
  public StaffMemberRecord toRecord(StaffMember staffMember) {
    return StaffMemberRecord.builder()
        .id(staffMember.id())
        .staffCode(staffMember.staffCode())
        .fullName(staffMember.fullName())
        .dateOfBirth(staffMember.dateOfBirth())
        .sex(staffMember.sex() == null ? null : staffMember.sex().name())
        .phone(staffMember.phone())
        .email(staffMember.email())
        .status(staffMember.status().name())
        .rowVersion(staffMember.rowVersion())
        .build();
  }

  public AccountRecord toRecord(StaffAccount account) {
    return AccountRecord.builder()
        .id(account.id())
        .accountType(AccountType.STAFF.name())
        .roleId(account.role().id())
        .username(account.username())
        .passwordHash(account.passwordHash())
        .staffMemberId(account.staffMemberId())
        .status(account.status().name())
        .build();
  }

  public RoleRef toRoleRef(RoleRecord role) {
    return new RoleRef(role.id(), role.code());
  }

  public AssignableRole toAssignableRole(AssignableRoleView role) {
    return new AssignableRole(
        role.id(), role.code(), role.name(), role.description(), role.permissionCount());
  }

  public StaffMemberSummary toSummary(StaffMemberListView row) {
    return StaffMemberSummary.builder()
        .id(row.id())
        .staffCode(row.staffCode())
        .fullName(row.fullName())
        .dateOfBirth(row.dateOfBirth())
        .sex(row.sex())
        .phone(row.phone())
        .email(row.email())
        .status(row.status())
        .rowVersion(row.rowVersion() == null ? Long.valueOf(0L) : row.rowVersion())
        .account(
            row.accountId() == null
                ? null
                : new StaffMemberSummary.Account(
                    row.accountId(),
                    row.username(),
                    row.accountStatus(),
                    row.roleCode(),
                    row.accountRowVersion()))
        .build();
  }
}
