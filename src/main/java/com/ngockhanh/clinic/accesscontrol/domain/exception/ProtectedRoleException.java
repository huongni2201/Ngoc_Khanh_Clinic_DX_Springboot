package com.ngockhanh.clinic.accesscontrol.domain.exception;

import com.ngockhanh.clinic.shared.exception.BusinessRuleException;

/**
 * A change to a role the system depends on: deactivating the {@code ADMINISTRATOR} or {@code
 * PATIENT} role, or removing a role administration permission from {@code ADMINISTRATOR}. Reported
 * as HTTP 409.
 */
public final class ProtectedRoleException extends BusinessRuleException {
  public ProtectedRoleException(String message) {
    super(message);
  }
}
