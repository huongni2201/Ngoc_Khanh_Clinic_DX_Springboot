package com.ngockhanh.clinic.accesscontrol.domain.aggregate;

import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import java.util.UUID;

/**
 * New STAFF account opened for a staff member. The password is already hashed. An account holds
 * exactly one role, and a staff account never holds the {@code PATIENT} role (ADR-0015).
 */
public final class StaffAccount {
  public static final String PATIENT_ROLE_CODE = "PATIENT";
  public static final Integer USERNAME_MAX_LENGTH = 150;

  private final UUID id;
  private final UUID staffMemberId;
  private final String username;
  private final String passwordHash;
  private final AccountStatus status;
  private final RoleRef role;

  private StaffAccount(
      UUID id, UUID staffMemberId, String username, String passwordHash, RoleRef role) {
    if (id == null || staffMemberId == null)
      throw new IllegalArgumentException("Account and owner are required");
    if (username == null
        || username.isBlank()
        || !username.equals(username.trim())
        || username.length() > USERNAME_MAX_LENGTH)
      throw new IllegalArgumentException("Invalid username");
    if (passwordHash == null || passwordHash.isBlank())
      throw new IllegalArgumentException("Password hash is required");
    if (role == null || !isAssignable(role))
      throw new IllegalArgumentException("Role cannot be held by a staff account");
    this.id = id;
    this.staffMemberId = staffMemberId;
    this.username = username;
    this.passwordHash = passwordHash;
    this.status = AccountStatus.ACTIVE;
    this.role = role;
  }

  /**
   * Opens an active STAFF account owned by the staff member, holding the role.
   *
   * @throws IllegalArgumentException when a value is missing or the role is {@code PATIENT}
   */
  public static StaffAccount open(
      UUID id, UUID staffMemberId, String username, String passwordHash, RoleRef role) {
    return new StaffAccount(id, staffMemberId, username, passwordHash, role);
  }

  /** Whether a staff account may hold the role. */
  public static Boolean isAssignable(RoleRef role) {
    return !PATIENT_ROLE_CODE.equals(role.code());
  }

  public UUID id() {
    return id;
  }

  public UUID staffMemberId() {
    return staffMemberId;
  }

  public String username() {
    return username;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public AccountStatus status() {
    return status;
  }

  public RoleRef role() {
    return role;
  }

  /** Never prints the password hash. */
  @Override
  public String toString() {
    return "StaffAccount[id=" + id + "]";
  }
}
