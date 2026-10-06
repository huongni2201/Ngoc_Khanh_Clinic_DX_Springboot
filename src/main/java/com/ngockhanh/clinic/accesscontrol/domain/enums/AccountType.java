package com.ngockhanh.clinic.accesscontrol.domain.enums;

import java.util.Arrays;
import java.util.Optional;

/** Owner kind of an account, stored in {@code accounts.account_type}. */
public enum AccountType {
  STAFF,
  PATIENT;

  /** Resolves a stored value; unknown values resolve to empty so they can never authenticate. */
  public static Optional<AccountType> from(String value) {
    return Arrays.stream(values()).filter(type -> type.name().equals(value)).findFirst();
  }
}
