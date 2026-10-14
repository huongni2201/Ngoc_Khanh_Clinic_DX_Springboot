package com.ngockhanh.clinic.accesscontrol.domain.aggregate;

import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * New STAFF account opened for a staff member, with its initial role grants. The password is
 * already hashed; staff accounts never receive the {@code PATIENT} role (ADR-0015).
 */
public final class StaffAccount {
  public static final String PATIENT_ROLE_CODE = "PATIENT";
  public static final Integer USERNAME_MAX_LENGTH = 150;

  private final UUID id;
  private final UUID staffMemberId;
  private final String username;
  private final String passwordHash;
  private final AccountStatus status;
  private final List<RoleRef> roles;
  private final UUID grantedBy;
  private final Instant grantedAt;

  private StaffAccount(
      UUID id,
      UUID staffMemberId,
      String username,
      String passwordHash,
      List<RoleRef> roles,
      UUID grantedBy,
      Instant grantedAt) {
    if (id == null || staffMemberId == null || grantedBy == null || grantedAt == null)
      throw new IllegalArgumentException("Account, owner and grantor are required");
    if (username == null
        || username.isBlank()
        || !username.equals(username.trim())
        || username.length() > USERNAME_MAX_LENGTH)
      throw new IllegalArgumentException("Invalid username");
    if (passwordHash == null || passwordHash.isBlank())
      throw new IllegalArgumentException("Password hash is required");
    if (roles == null || roles.isEmpty())
      throw new IllegalArgumentException("A staff account needs at least one role");
    Map<UUID, RoleRef> distinct = new LinkedHashMap<>();
    for (RoleRef role : roles) {
      if (role == null || !isAssignable(role))
        throw new IllegalArgumentException("Role cannot be granted to a staff account");
      distinct.putIfAbsent(role.id(), role);
    }
    this.id = id;
    this.staffMemberId = staffMemberId;
    this.username = username;
    this.passwordHash = passwordHash;
    this.status = AccountStatus.ACTIVE;
    this.roles = distinct.values().stream().sorted(Comparator.comparing(RoleRef::code)).toList();
    this.grantedBy = grantedBy;
    this.grantedAt = grantedAt;
  }

  /**
   * Opens an active STAFF account owned by the staff member and grants the roles.
   *
   * @param grantedBy STAFF account granting the roles
   * @throws IllegalArgumentException when a value is missing, there is no role or a role is {@code
   *     PATIENT}
   */
  public static StaffAccount open(
      UUID id,
      UUID staffMemberId,
      String username,
      String passwordHash,
      List<RoleRef> roles,
      UUID grantedBy,
      Instant grantedAt) {
    return new StaffAccount(id, staffMemberId, username, passwordHash, roles, grantedBy, grantedAt);
  }

  /** Whether the role may be granted to a staff account. */
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

  /** Distinct granted roles ordered by code. */
  public List<RoleRef> roles() {
    return roles;
  }

  public List<String> roleCodes() {
    return roles.stream().map(RoleRef::code).toList();
  }

  public UUID grantedBy() {
    return grantedBy;
  }

  public Instant grantedAt() {
    return grantedAt;
  }

  /** Never prints the password hash. */
  @Override
  public String toString() {
    return "StaffAccount[id=" + id + "]";
  }
}
