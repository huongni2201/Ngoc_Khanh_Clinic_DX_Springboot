package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.AccountRecord;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.AccountRoleRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StaffAccountMyBatisMapper {
  Integer insert(AccountRecord account);

  /** Inserts all grants in one statement; returns the inserted row count. */
  Integer insertRoles(@Param("grants") List<AccountRoleRecord> grants);

  /** Takes a transaction-scoped advisory lock named by {@code key}. */
  Integer lockSequence(@Param("key") String key);

  /** Highest number {@code n} among usernames {@code <base><n>}, or 0. */
  Long highestUsernameNumber(@Param("base") String base);
}
