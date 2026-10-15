package com.ngockhanh.clinic.accesscontrol.domain.repository;

import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import java.util.Optional;

/** Looks up roles for new accounts. */
public interface RoleRepository {
  /**
   * Finds an active role by its exact code.
   *
   * @param code role code
   * @return the role, or empty when the code is unknown or the role is inactive
   */
  Optional<RoleRef> findActiveByCode(String code);
}
