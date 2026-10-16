package com.ngockhanh.clinic.accesscontrol.domain.exception;

import com.ngockhanh.clinic.shared.exception.BusinessRuleException;

/**
 * A change that would leave no role able to administer roles: deactivating the {@code
 * ADMINISTRATOR} role or removing one of its role administration permissions. Reported as HTTP 409.
 */
public final class ProtectedRoleException extends BusinessRuleException {
  public ProtectedRoleException(String message) {
    super(message);
  }
}
