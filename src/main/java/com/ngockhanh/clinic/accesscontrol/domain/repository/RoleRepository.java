package com.ngockhanh.clinic.accesscontrol.domain.repository;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Looks up roles for new accounts and stores roles with their permission grants. */
public interface RoleRepository {
  /**
   * Finds an active role by its exact code.
   *
   * @param code role code
   * @return the role, or empty when the code is unknown or the role is inactive
   */
  Optional<RoleRef> findActiveByCode(String code);

  /**
   * Finds a role of any status.
   *
   * @param id role identifier
   * @return the role, or empty when it does not exist
   */
  Optional<Role> findById(UUID id);

  /** Whether a role already uses exactly this code. */
  Boolean existsByCode(String code);

  /**
   * Whether another role already uses this name, ignoring case (BR-11).
   *
   * @param name trimmed role name
   * @param excludedRoleId role being renamed, or null when creating
   */
  Boolean existsByName(String name, UUID excludedRoleId);

  /** Stores a new role. */
  void insert(Role role);

  /**
   * Stores the name, description and active flag and increments the row version.
   *
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the stored row
   *     version is no longer {@code expectedRowVersion}
   */
  void update(Role role, Long expectedRowVersion);

  /**
   * Increments the row version of a role whose permissions change.
   *
   * @throws com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException when the stored row
   *     version is no longer {@code expectedRowVersion}
   */
  void incrementRowVersion(UUID roleId, Long expectedRowVersion);

  /** Codes of the permissions granted to the role, ordered by code. */
  List<String> findGrantedPermissionCodes(UUID roleId);

  /** How many of the distinct codes name a permission of the catalogue. */
  Long countExistingPermissions(Collection<String> permissionCodes);

  /** Grants the permissions to the role; codes already granted are ignored. */
  void grantPermissions(UUID roleId, Collection<String> permissionCodes);

  /** Removes the permissions from the role; codes not granted are ignored. */
  void revokePermissions(UUID roleId, Collection<String> permissionCodes);

  /** Grants every permission of the source role to the target role. */
  void copyPermissions(UUID sourceRoleId, UUID targetRoleId);
}
