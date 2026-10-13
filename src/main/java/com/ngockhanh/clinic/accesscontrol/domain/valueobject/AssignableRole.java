package com.ngockhanh.clinic.accesscontrol.domain.valueobject;

import java.util.UUID;

/**
 * Role as needed to grant it to a new account.
 *
 * @param id role identifier
 * @param code unique role code
 * @param name role name
 * @param description role description
 * @param active whether the role currently grants its permissions
 */
public record AssignableRole(
    UUID id, String code, String name, String description, boolean active) {
  /** Code of the role reserved for patient portal accounts. */
  public static final String PATIENT_ROLE_CODE = "PATIENT";

  public AssignableRole {
    if (id == null || code == null || code.isBlank())
      throw new IllegalArgumentException("Role identity is required");
  }

  /**
   * @return true for the role reserved for patient accounts
   */
  public boolean isPatientRole() {
    return PATIENT_ROLE_CODE.equals(code);
  }
}
