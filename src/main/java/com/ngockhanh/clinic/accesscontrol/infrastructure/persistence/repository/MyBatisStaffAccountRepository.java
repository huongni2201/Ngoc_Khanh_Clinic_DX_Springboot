package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.entity.AccountRole;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.RoleRef;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.UsernameBase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter.StaffMemberPersistenceConverter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.StaffAccountMyBatisMapper;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisStaffAccountRepository implements StaffAccountRepository {
  private static final String USERNAME_LOCK_PREFIX = "nkc:username:";
  private static final String ADMINISTRATOR_LOCK = "nkc:administrator-accounts";

  private final StaffAccountMyBatisMapper mapper;
  private final StaffMemberPersistenceConverter converter = new StaffMemberPersistenceConverter();

  @Override
  public String nextUsername(UsernameBase base) {
    // One lock per base: creations with the same initials wait, others proceed in parallel.
    mapper.lockSequence(USERNAME_LOCK_PREFIX + base.value());
    return base.value() + (mapper.highestUsernameNumber(base.value()) + 1);
  }

  @Override
  public void save(StaffAccount account) {
    if (!Objects.equals(mapper.insert(converter.toRecord(account)), 1))
      throw new IllegalStateException("Account was not inserted");
  }

  @Override
  public Optional<AccountRole> findAccountRole(UUID accountId) {
    if (accountId == null) return Optional.empty();
    return Optional.ofNullable(mapper.findAccountRole(accountId))
        .map(
            row ->
                new AccountRole(
                    row.accountId(),
                    AccountType.valueOf(row.accountType()),
                    AccountStatus.valueOf(row.status()),
                    new RoleRef(row.roleId(), row.roleCode()),
                    row.rowVersion()));
  }

  @Override
  public void lockAdministratorAccounts() {
    mapper.lockSequence(ADMINISTRATOR_LOCK);
  }

  @Override
  public Long countOtherActiveAdministrators(UUID excludedAccountId) {
    return mapper.countOtherActiveAdministrators(excludedAccountId);
  }

  @Override
  public void changeRole(UUID accountId, RoleRef role, Long expectedRowVersion) {
    if (!Objects.equals(mapper.changeRole(accountId, role.id(), expectedRowVersion), 1))
      throw new ConcurrentUpdateException();
  }
}
