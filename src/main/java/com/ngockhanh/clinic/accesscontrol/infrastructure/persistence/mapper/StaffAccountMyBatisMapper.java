package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.AccountRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StaffAccountMyBatisMapper {
  Integer insert(AccountRecord account);


  /** Takes a transaction-scoped advisory lock named by {@code key}. */
  Integer lockSequence(@Param("key") String key);

  /** Highest number {@code n} among usernames {@code <base><n>}, or 0. */
  Long highestUsernameNumber(@Param("base") String base);
}
