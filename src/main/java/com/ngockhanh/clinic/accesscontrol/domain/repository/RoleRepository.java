package com.ngockhanh.clinic.accesscontrol.domain.repository;

import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AssignableRole;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Roles that can be granted to accounts. */
public interface RoleRepository {
  /**
   * Reads roles and holds a share lock on them until the end of the transaction, so they cannot be
   * deactivated while they are being granted.
   *
   * @param ids role identifiers
   * @return the existing roles among them
   */
  List<AssignableRole> lockByIds(Set<UUID> ids);

  /**
   * @param code role code
   * @return the role, or empty
   */
  Optional<AssignableRole> findByCode(String code);

  /**
   * @return active roles that can be granted to a STAFF account, ordered by name then code
   */
  List<AssignableRole> findActiveStaffRoles();
}
