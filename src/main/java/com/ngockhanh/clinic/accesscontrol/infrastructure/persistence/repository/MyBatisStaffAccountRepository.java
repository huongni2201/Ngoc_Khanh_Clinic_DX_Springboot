package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository;

import com.ngockhanh.clinic.accesscontrol.domain.aggregate.StaffAccount;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.UsernameBase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.converter.StaffMemberPersistenceConverter;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.StaffAccountMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.AccountRoleRecord;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisStaffAccountRepository implements StaffAccountRepository {
  private static final String USERNAME_LOCK_PREFIX = "nkc:username:";

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
    List<AccountRoleRecord> grants = converter.toRoleRecords(account);
    if (!Objects.equals(mapper.insertRoles(grants), grants.size()))
      throw new IllegalStateException("Account roles were not inserted");
  }
}
