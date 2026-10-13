package com.ngockhanh.clinic.accesscontrol.domain.entity;

import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AssignableRole;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Account about to be created by an Administrator (UC-005): an ACTIVE account linked to exactly one
 * owner, with its initial roles. A STAFF account holds at least one active staff role; a PATIENT
 * account holds only the active PATIENT role.
 *
 * @param id new account identifier
 * @param type STAFF or PATIENT
 * @param username sign-in identifier
 * @param passwordHash encoded temporary password
 * @param staffMemberId owner of a STAFF account, otherwise null
 * @param patientId owner of a PATIENT account, otherwise null
 * @param status always ACTIVE
 * @param createdAt creation time
 * @param roles initial roles in the order they were requested
 */
public record NewUserAccount(
    UUID id,
    AccountType type,
    String username,
    String passwordHash,
    UUID staffMemberId,
    UUID patientId,
    AccountStatus status,
    Instant createdAt,
    List<AssignableRole> roles) {
  public NewUserAccount {
    if (id == null || type == null || createdAt == null)
      throw new IllegalArgumentException("Account identity is required");
    if (username == null || username.isBlank() || !username.equals(username.strip()))
      throw new IllegalArgumentException("Username is required without surrounding spaces");
    if (passwordHash == null || passwordHash.isBlank())
      throw new IllegalArgumentException("Password hash is required");
    if (status != AccountStatus.ACTIVE)
      throw new IllegalArgumentException("A new account is created active");
    roles = roles == null ? List.of() : List.copyOf(roles);
    if (roles.stream().anyMatch(role -> !role.active()))
      throw new IllegalArgumentException("Only active roles can be granted");
    if (new HashSet<>(roles.stream().map(AssignableRole::id).toList()).size() != roles.size())
      throw new IllegalArgumentException("A role can be granted only once");
    switch (type) {
      case STAFF -> {
        if (staffMemberId == null || patientId != null)
          throw new IllegalArgumentException("A staff account is owned by one staff member");
        if (roles.isEmpty() || roles.stream().anyMatch(AssignableRole::isPatientRole))
          throw new IllegalArgumentException("A staff account needs at least one staff role");
      }
      case PATIENT -> {
        if (patientId == null || staffMemberId != null)
          throw new IllegalArgumentException("A patient account is owned by one patient");
        if (roles.size() != 1 || !roles.getFirst().isPatientRole())
          throw new IllegalArgumentException("A patient account holds only the patient role");
      }
    }
  }

  /**
   * Builds a STAFF account linked to a staff member.
   *
   * @param id new account identifier
   * @param username sign-in identifier
   * @param passwordHash encoded temporary password
   * @param staffMemberId owning staff member
   * @param roles at least one active, non-patient role
   * @param now creation time
   * @return the new account
   * @throws IllegalArgumentException if an invariant is violated
   */
  public static NewUserAccount forStaff(
      UUID id,
      String username,
      String passwordHash,
      UUID staffMemberId,
      List<AssignableRole> roles,
      Instant now) {
    return new NewUserAccount(
        id,
        AccountType.STAFF,
        username,
        passwordHash,
        staffMemberId,
        null,
        AccountStatus.ACTIVE,
        now,
        roles);
  }

  /**
   * Builds a PATIENT account linked to a patient, holding the patient role.
   *
   * @param id new account identifier
   * @param username sign-in identifier
   * @param passwordHash encoded temporary password
   * @param patientId owning patient
   * @param patientRole the active PATIENT role
   * @param now creation time
   * @return the new account
   * @throws IllegalArgumentException if an invariant is violated
   */
  public static NewUserAccount forPatient(
      UUID id,
      String username,
      String passwordHash,
      UUID patientId,
      AssignableRole patientRole,
      Instant now) {
    return new NewUserAccount(
        id,
        AccountType.PATIENT,
        username,
        passwordHash,
        null,
        patientId,
        AccountStatus.ACTIVE,
        now,
        patientRole == null ? List.of() : List.of(patientRole));
  }

  /**
   * @return the owning staff member or patient identifier
   */
  public UUID ownerId() {
    return type == AccountType.STAFF ? staffMemberId : patientId;
  }

  /**
   * @return the codes of the initial roles
   */
  public List<String> roleCodes() {
    return roles.stream().map(AssignableRole::code).toList();
  }

  @Override
  public String toString() {
    return "NewUserAccount[id=" + id + ", type=" + type + ", roles=" + roleCodes() + "]";
  }
}
