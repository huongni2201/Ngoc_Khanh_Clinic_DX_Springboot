package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter.StaffMemberPersistenceConverter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.RoleMyBatisMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisRoleRepository implements RoleRepository {
  private final RoleMyBatisMapper mapper;
  private final StaffMemberPersistenceConverter converter = new StaffMemberPersistenceConverter();

  @Override
  public List<RoleRef> findActiveByCodes(Set<String> codes) {
    if (codes == null || codes.isEmpty()) return List.of();
    return mapper.findActiveByCodes(codes).stream().map(converter::toRoleRef).toList();
  }
}
