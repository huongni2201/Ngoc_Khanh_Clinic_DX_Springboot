package com.ngockhanh.clinic.accesscontrol.domain.entity;

import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleGrant;
import java.util.List;
import java.util.UUID;

/**
 * Account as needed for sign-in: credentials, status, its single STAFF or PATIENT owner and the
 * active roles granted to it.
 */
public record UserAccount(
    UUID id,
    AccountType accountType,
    String username,
    String passwordHash,
    String status,
    UUID staffMemberId,
    String staffStatus,
    UUID patientId,
    List<RoleGrant> roles) {
  private static final String ACTIVE = "ACTIVE";

  public UserAccount {
    if (id == null) throw new IllegalArgumentException("Account id is required");
    roles = roles == null ? List.of() : List.copyOf(roles);
  }

  /**
   * Whether the account may sign in: the account is active and owned by exactly one valid owner —
   * an active staff member for STAFF accounts, a patient record for PATIENT accounts. Roles may be
   * empty.
   */
  public Boolean eligible() {
    if (!ACTIVE.equals(status) || accountType == null) return false;
    return switch (accountType) {
      case STAFF -> staffMemberId != null && patientId == null && ACTIVE.equals(staffStatus);
      case PATIENT -> patientId != null && staffMemberId == null;
    };
  }
}
