package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.domain.entity.NewUserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.entity.UserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.enums.StaffMemberStatus;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleGrant;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserAccountMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection.RoleGrantRow;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.projection.UserLoginRow;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.AccountRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.AccountRoleRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisUserAccountRepository implements UserAccountRepository {
  private final UserLoginMyBatisMapper mapper;
  private final UserAccountMyBatisMapper writes;

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
            AccountStatus.valueOf(row.status()),
            row.staffMemberId(),
            row.staffStatus() == null ? null : StaffMemberStatus.valueOf(row.staffStatus()),
            row.patientId(),
            toGrants(mapper.findActiveGrants(row.accountId()))));
  }

  @Override
  public boolean existsByUsername(String username) {
    return username != null && writes.existsByUsername(username);
  }

  @Override
  public boolean isOwnerLinked(AccountType type, UUID ownerId) {
    return switch (type) {
      case STAFF -> writes.existsByStaffMember(ownerId);
      case PATIENT -> writes.existsByPatient(ownerId);
    };
  }

  @Override
  public Set<UUID> findLinkedPatientIds(Collection<UUID> patientIds) {
    if (patientIds == null || patientIds.isEmpty()) return Set.of();
    return Set.copyOf(writes.findLinkedPatientIds(patientIds));
  }

  @Override
  public boolean insertIfAbsent(NewUserAccount account) {
    AccountRecord record =
        AccountRecord.builder()
            .id(account.id())
            .accountType(account.type().name())
            .username(account.username())
            .passwordHash(account.passwordHash())
            .staffMemberId(account.staffMemberId())
            .patientId(account.patientId())
            .status(account.status().name())
            .createdAt(account.createdAt())
            .updatedAt(account.createdAt())
            .build();
    return writes.insertIfAbsent(record) == 1;
  }

  @Override
  public void grantRoles(UUID accountId, List<UUID> roleIds, UUID grantedBy, Instant grantedAt) {
    if (roleIds.isEmpty()) return;
    List<AccountRoleRecord> grants =
        roleIds.stream()
            .map(
                roleId ->
                    AccountRoleRecord.builder()
                        .accountId(accountId)
                        .roleId(roleId)
                        .grantedBy(grantedBy)
                        .grantedAt(grantedAt)
                        .build())
            .toList();
    if (writes.insertAccountRoles(grants) != grants.size())
      throw new IllegalStateException("Not every role was granted");
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
