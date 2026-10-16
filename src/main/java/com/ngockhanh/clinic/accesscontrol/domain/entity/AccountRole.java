package com.ngockhanh.clinic.accesscontrol.domain.entity;

import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import java.util.UUID;

/**
 * The one role an account holds, with the account state needed to change it (SRS UC-010 Change User
 * Role). The role is replaced, never added or removed.
 *
 * @param rowVersion version of the account row, checked when the role changes
 */
public record AccountRole(
    UUID accountId, AccountType accountType, AccountStatus status, RoleRef role, Long rowVersion) {
  public AccountRole {
    if (accountId == null || accountType == null || status == null || role == null)
      throw new IllegalArgumentException("Account, type, status and role are required");
    if (rowVersion == null || rowVersion < 0)
      throw new IllegalArgumentException("Row version is required");
  }

  /** Whether the role may change: only an active STAFF account's role does. */
  public Boolean isActiveStaff() {
    return accountType == AccountType.STAFF && status == AccountStatus.ACTIVE;
  }

  /** Whether the account holds the role with this code. */
  public Boolean holds(String roleCode) {
    return role.code().equals(roleCode);
  }
}
