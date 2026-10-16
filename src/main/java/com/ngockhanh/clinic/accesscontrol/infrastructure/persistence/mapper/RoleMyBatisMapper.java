package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.RoleRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.view.RoleListView;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMyBatisMapper {
  /** Active role with exactly this code, or null. */
  RoleRecord findActiveByCode(@Param("code") String code);

  /** Active roles other than {@code PATIENT}, ordered by code. */
  List<RoleRecord> findAssignable();

  /** Role of any status, or null. */
  RoleRecord findById(@Param("id") UUID id);

  Boolean existsByCode(@Param("code") String code);

  /** Case-insensitive name match, optionally ignoring one role. */
  Boolean existsByName(@Param("name") String name, @Param("excludedId") UUID excludedId);

  Integer insert(RoleRecord role);

  /** Updates the editable columns when the row version still matches; returns the row count. */
  Integer update(
      @Param("role") RoleRecord role, @Param("expectedRowVersion") Long expectedRowVersion);

  /** Increments the row version when it still matches; returns the row count. */
  Integer incrementRowVersion(
      @Param("id") UUID id, @Param("expectedRowVersion") Long expectedRowVersion);

  List<String> findGrantedPermissionCodes(@Param("roleId") UUID roleId);

  Long countExistingPermissions(@Param("codes") Collection<String> codes);

  Integer grantPermissions(@Param("roleId") UUID roleId, @Param("codes") Collection<String> codes);

  Integer revokePermissions(@Param("roleId") UUID roleId, @Param("codes") Collection<String> codes);

  Integer copyPermissions(
      @Param("sourceRoleId") UUID sourceRoleId, @Param("targetRoleId") UUID targetRoleId);

  /** One page of roles of every status with usage counts, ordered by code. */
  List<RoleListView> search(@Param("limit") Integer limit, @Param("offset") Long offset);

  Long count();

  /** One role with usage counts, or null. */
  RoleListView findListViewById(@Param("id") UUID id);

  /** The whole permission catalogue, ordered by module and code. */
  List<PermissionRecord> findPermissionCatalog();
}
