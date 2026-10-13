package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.AssignableRole;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.RoleMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.RoleRecord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisRoleRepository implements RoleRepository {
  private final RoleMyBatisMapper mapper;

  @Override
  public List<AssignableRole> lockByIds(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    return mapper.lockByIds(ids).stream().map(MyBatisRoleRepository::toDomain).toList();
  }

  @Override
  public Optional<AssignableRole> findByCode(String code) {
    return Optional.ofNullable(mapper.findByCode(code)).map(MyBatisRoleRepository::toDomain);
  }

  @Override
  public List<AssignableRole> findActiveStaffRoles() {
    return mapper.findActiveExcept(AssignableRole.PATIENT_ROLE_CODE).stream()
        .map(MyBatisRoleRepository::toDomain)
        .toList();
  }

  private static AssignableRole toDomain(RoleRecord record) {
    return new AssignableRole(
        record.id(), record.code(), record.name(), record.description(), record.active());
  }
}
