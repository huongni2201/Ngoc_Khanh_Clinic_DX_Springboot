package com.ngockhanh.clinic.accesscontrol.domain.valueobject;

import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import java.util.UUID;

/**
 * Staff member or patient profile that a new account is linked to, as needed to create the account.
 *
 * @param id staff member or patient identifier
 * @param type STAFF for a staff member, PATIENT for a patient
 * @param active whether the profile is in ACTIVE status
 * @param phone phone number stored on the profile, possibly null or free text
 */
public record AccountOwner(UUID id, AccountType type, boolean active, String phone) {
  public AccountOwner {
    if (id == null || type == null)
      throw new IllegalArgumentException("Account owner identity is required");
  }

  @Override
  public String toString() {
    return "AccountOwner[id=" + id + ", type=" + type + ", active=" + active + "]";
  }
}
