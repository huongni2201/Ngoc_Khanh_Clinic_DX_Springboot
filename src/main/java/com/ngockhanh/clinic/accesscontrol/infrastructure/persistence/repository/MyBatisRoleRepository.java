package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.Role;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter.RolePersistenceConverter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter.StaffMemberPersistenceConverter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.RoleMyBatisMapper;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisRoleRepository implements RoleRepository {
  private final RoleMyBatisMapper mapper;
  private final StaffMemberPersistenceConverter converter = new StaffMemberPersistenceConverter();
  private final RolePersistenceConverter roleConverter = new RolePersistenceConverter();

  @Override
  public Optional<RoleRef> findActiveByCode(String code) {
    if (code == null || code.isBlank()) return Optional.empty();
    return Optional.ofNullable(mapper.findActiveByCode(code)).map(converter::toRoleRef);
  }

  @Override
  public Optional<Role> findById(UUID id) {
    if (id == null) return Optional.empty();
    return Optional.ofNullable(mapper.findById(id)).map(roleConverter::toDomain);
  }

  @Override
  public Boolean existsByCode(String code) {
    return Boolean.TRUE.equals(mapper.existsByCode(code));
  }

  @Override
  public Boolean existsByName(String name, UUID excludedRoleId) {
    return Boolean.TRUE.equals(mapper.existsByName(name, excludedRoleId));
  }

  @Override
  public void insert(Role role) {
    mapper.insert(roleConverter.toRecord(role));
  }

  @Override
  public void update(Role role, Long expectedRowVersion) {
    if (!Objects.equals(mapper.update(roleConverter.toRecord(role), expectedRowVersion), 1))
      throw new ConcurrentUpdateException();
  }

  @Override
  public void incrementRowVersion(UUID roleId, Long expectedRowVersion) {
    if (!Objects.equals(mapper.incrementRowVersion(roleId, expectedRowVersion), 1))
      throw new ConcurrentUpdateException();
  }

  @Override
  public List<String> findGrantedPermissionCodes(UUID roleId) {
    return List.copyOf(mapper.findGrantedPermissionCodes(roleId));
  }

  @Override
  public Long countExistingPermissions(Collection<String> permissionCodes) {
    if (permissionCodes.isEmpty()) return 0L;
    return mapper.countExistingPermissions(permissionCodes);
  }

  @Override
  public void grantPermissions(UUID roleId, Collection<String> permissionCodes) {
    if (!permissionCodes.isEmpty()) mapper.grantPermissions(roleId, permissionCodes);
  }

  @Override
  public void revokePermissions(UUID roleId, Collection<String> permissionCodes) {
    if (!permissionCodes.isEmpty()) mapper.revokePermissions(roleId, permissionCodes);
  }

  @Override
  public void copyPermissions(UUID sourceRoleId, UUID targetRoleId) {
    mapper.copyPermissions(sourceRoleId, targetRoleId);
  }
}
