package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.domain.entity.UserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleGrant;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection.RoleGrantRow;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection.UserLoginRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisUserAccountRepository implements UserAccountRepository {
  private final UserLoginMyBatisMapper mapper;

  @Override
  public Optional<UserAccount> findByUsername(String username) {
    if (username == null) return Optional.empty();
    UserLoginRow row = mapper.findByUsername(username);
    if (row == null) return Optional.empty();
    return Optional.of(
        new UserAccount(
            row.accountId(),
            AccountType.from(row.accountType()).orElse(null),
            row.username(),
            row.passwordHash(),
            row.status(),
            row.staffMemberId(),
            row.staffStatus(),
            row.patientId(),
            toGrants(mapper.findActiveGrants(row.accountId()))));
  }

  private static List<RoleGrant> toGrants(List<RoleGrantRow> rows) {
    Map<UUID, String> codes = new LinkedHashMap<>();
    Map<UUID, List<String>> permissions = new LinkedHashMap<>();
    for (RoleGrantRow row : rows) {
      codes.putIfAbsent(row.roleId(), row.roleCode());
      List<String> rolePermissions =
          permissions.computeIfAbsent(row.roleId(), id -> new ArrayList<>());
      if (row.permissionCode() != null) rolePermissions.add(row.permissionCode());
    }
    return codes.entrySet().stream()
        .map(
            entry ->
                new RoleGrant(entry.getKey(), entry.getValue(), permissions.get(entry.getKey())))
        .toList();
  }
}
